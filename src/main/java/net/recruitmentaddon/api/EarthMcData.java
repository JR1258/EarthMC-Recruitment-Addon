package net.recruitmentaddon.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.model.PlayerProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * Cached access to the official EarthMC API. Only used to look up a player's
 * town / nation / registration date.
 *
 * Requests follow EarthMC's published guidance: batches of up to 100, a field template so
 * only the fields we read come back, gzip, at most 4 requests in flight, a client-side
 * per-minute budget (the API sends no rate-limit headers), and exponential back-off on
 * 429 / 5xx. Join lookups jump the queue ahead of bulk HUD refreshes.
 *
 * Players are queried by UUID when the tab list has told us one, so renamed accounts still
 * resolve. Names the API doesn't return (players who ran {@code /api opt-out}) are
 * remembered as absent instead of being re-requested every few seconds.
 *
 * Parsing is pure Java (no Minecraft types), so this class can later move into a shared
 * EarthMC API library used by several mods.
 */
public final class EarthMcData {

    private static final Logger LOGGER = LoggerFactory.getLogger("RecruitmentAddon");
    private static final long PROFILE_TTL_MS      = 60_000L;
    private static final long NULL_PROFILE_TTL_MS = 10_000L; // retry fast when API hasn't returned a profile yet
    /** Players the API didn't return (opted out / unknown) aren't asked about again for this long. */
    private static final long ABSENT_TTL_MS = 10 * 60_000L;
    private static final int MAX_QUERY_BATCH = 100;
    private static final int MAX_CONCURRENT_FETCHES = 4;
    private static final long RATE_WINDOW_MS = 60_000L;
    // EarthMC allows 180 requests/minute per IP, shared with every other mod on this machine.
    private static final int BULK_BUDGET_PER_MIN = 120;
    private static final int PRIORITY_BUDGET_PER_MIN = 160;
    private static final long MIN_BACKOFF_MS = 5_000L;
    private static final long MAX_BACKOFF_MS = 120_000L;
    private static final String[] PLAYER_FIELDS = {"name", "uuid", "town", "nation", "timestamps"};

    private final RecruitmentConfig config;
    private final HttpClient http;
    private final ExecutorService executor;
    private final TownyMapProfileBridge townyMapBridge;

    private final Map<String, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, Long> profileFetchedAt = new ConcurrentHashMap<>();
    private final Map<String, Long> absentUntil = new ConcurrentHashMap<>();
    private final Map<String, UUID> uuids = new ConcurrentHashMap<>();
    /** Keys whose next answer must come from the EarthMC API, not the Towny Map bridge. Cleared when the API answers. */
    private final Set<String> bridgeBypassKeys = ConcurrentHashMap.newKeySet();

    // Request scheduling — guarded by this.
    private final LinkedHashMap<String, String> priorityQueue = new LinkedHashMap<>(); // key -> name
    private final LinkedHashMap<String, String> bulkQueue = new LinkedHashMap<>();
    private final Set<String> inFlight = new HashSet<>();
    private final ArrayDeque<Long> requestTimes = new ArrayDeque<>();
    private int activeFetches = 0;
    private long backoffUntil = 0L;
    private long backoffMs = 0L;

    public EarthMcData(RecruitmentConfig config) {
        this.config = config;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(executor)
                .build();
        this.townyMapBridge = TownyMapProfileBridge.create();
    }

    /** Cached profile for a player, or null if not fetched yet. */
    public PlayerProfile profile(String name) {
        String k = key(name);
        PlayerProfile profile = profiles.get(k);
        if (profile != null) return profile;
        if (bridgeBypassKeys.contains(k)) return null;
        PlayerProfile bridged = townyMapBridge.cachedProfile(name);
        if (bridged != null) {
            cacheProfile(k, bridged, System.currentTimeMillis());
            return bridged;
        }
        return null;
    }

    /** True if the API recently answered without this player (opted out of the API, or unknown). */
    public boolean isAbsent(String name) {
        Long until = absentUntil.get(key(name));
        return until != null && System.currentTimeMillis() < until;
    }

    /** Records the UUID the tab list reports for {@code name}; lookups then query by UUID. */
    public void rememberUuid(String name, UUID id) {
        if (name == null || id == null) return;
        uuids.put(key(name), id);
    }

    public void clear() {
        profiles.clear();
        profileFetchedAt.clear();
        absentUntil.clear();
        uuids.clear();
        bridgeBypassKeys.clear();
        synchronized (this) {
            priorityQueue.clear();
            bulkQueue.clear();
        }
    }

    /** Starts queued requests that were waiting on back-off or the rate budget. Call regularly. */
    public void tick() {
        pump();
    }

    /** Fetches town/nation/registration for the given names that are missing or stale. */
    public void requestProfiles(Collection<String> names) {
        enqueue(names, false, false);
    }

    /** Fetches a single profile straight from the API, ahead of any bulk lookups. */
    public void requestFreshProfile(String name) {
        String k = key(name);
        profiles.remove(k);
        profileFetchedAt.remove(k);
        absentUntil.remove(k);
        bridgeBypassKeys.add(k);
        enqueue(List.of(name), true, true);
    }

    /** Clears the cached profile for {@code name} so the next batch request fetches it fresh. */
    public void invalidateProfile(String name) {
        String k = key(name);
        profiles.remove(k);
        profileFetchedAt.remove(k);
        bridgeBypassKeys.add(k);
    }

    // ── scheduling ──────────────────────────────────────────────────────────---

    private synchronized void enqueue(Collection<String> names, boolean priority, boolean forceFresh) {
        long now = System.currentTimeMillis();
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            String k = key(name);
            if (inFlight.contains(k)) continue;
            if (!forceFresh) {
                Long until = absentUntil.get(k);
                if (until != null && now < until) continue;
                Long at = profileFetchedAt.get(k);
                if (at != null) {
                    long ttl = profiles.containsKey(k) ? PROFILE_TTL_MS : NULL_PROFILE_TTL_MS;
                    if (now - at < ttl) continue;
                }
            }
            if (!bridgeBypassKeys.contains(k)) {
                PlayerProfile bridged = townyMapBridge.cachedProfile(name);
                if (bridged != null) {
                    cacheProfile(k, bridged, now);
                    continue;
                }
            }
            if (priority) {
                bulkQueue.remove(k);
                priorityQueue.put(k, name);
            } else if (!priorityQueue.containsKey(k)) {
                bulkQueue.put(k, name);
            }
        }
        pump();
    }

    private void pump() {
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (now < backoffUntil) return;
            while (activeFetches < MAX_CONCURRENT_FETCHES) {
                boolean priority = !priorityQueue.isEmpty();
                if (!priority && bulkQueue.isEmpty()) return;
                if (!withinBudget(now, priority ? PRIORITY_BUDGET_PER_MIN : BULK_BUDGET_PER_MIN)) return;
                LinkedHashMap<String, String> batch = new LinkedHashMap<>();
                drainInto(priorityQueue, batch);
                drainInto(bulkQueue, batch);
                requestTimes.addLast(now);
                activeFetches++;
                inFlight.addAll(batch.keySet());
                executor.execute(() -> fetch(batch));
            }
        }
    }

    private boolean withinBudget(long now, int limit) {
        while (!requestTimes.isEmpty() && now - requestTimes.peekFirst() >= RATE_WINDOW_MS) {
            requestTimes.pollFirst();
        }
        return requestTimes.size() < limit;
    }

    private static void drainInto(LinkedHashMap<String, String> source, LinkedHashMap<String, String> batch) {
        Iterator<Map.Entry<String, String>> it = source.entrySet().iterator();
        while (it.hasNext() && batch.size() < MAX_QUERY_BATCH) {
            Map.Entry<String, String> e = it.next();
            batch.put(e.getKey(), e.getValue());
            it.remove();
        }
    }

    private void fetch(LinkedHashMap<String, String> batch) {
        int status = -1;
        try {
            JsonObject body = new JsonObject();
            JsonArray query = new JsonArray();
            for (Map.Entry<String, String> e : batch.entrySet()) {
                UUID id = uuids.get(e.getKey());
                query.add(id != null ? id.toString() : e.getValue());
            }
            body.add("query", query);
            JsonObject template = new JsonObject();
            for (String field : PLAYER_FIELDS) template.addProperty(field, true);
            body.add("template", template);

            Response resp = post(config.earthmcApiBaseUrl + "/players", body.toString());
            status = resp.status();
            long fetchedAt = System.currentTimeMillis();
            if (status != 200 && status != 404) {
                // Nothing learned. Fetch times stay unset, so callers re-queue these after the back-off.
                onFailure(status);
                return;
            }
            // 404 means none of the queried players exist in the API.
            Set<String> returned = new HashSet<>();
            if (status == 200) {
                JsonElement root = JsonParser.parseString(resp.body());
                if (root.isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray()) {
                        if (!el.isJsonObject()) continue;
                        JsonObject obj = el.getAsJsonObject();
                        String k = matchKey(batch, obj);
                        PlayerProfile profile = parseProfile(obj, k != null ? batch.get(k) : null);
                        if (k == null || profile == null) continue;
                        cacheProfile(k, profile, fetchedAt);
                        returned.add(k);
                    }
                }
            }
            for (String k : batch.keySet()) {
                bridgeBypassKeys.remove(k);
                if (!returned.contains(k)) {
                    profileFetchedAt.put(k, fetchedAt);
                    absentUntil.put(k, fetchedAt + ABSENT_TTL_MS);
                }
            }
            onSuccess();
        } catch (Exception e) {
            LOGGER.debug("[Recruitment] profile batch failed: {}", e.getMessage());
            onFailure(status);
        } finally {
            synchronized (this) {
                activeFetches--;
                inFlight.removeAll(batch.keySet());
            }
            pump();
        }
    }

    /** Matches a response entry to the query that produced it: by UUID first, then by name. */
    private String matchKey(Map<String, String> batch, JsonObject obj) {
        String uuid = str(obj, "uuid");
        if (uuid != null) {
            for (String k : batch.keySet()) {
                UUID id = uuids.get(k);
                if (id != null && id.toString().equalsIgnoreCase(uuid)) return k;
            }
        }
        String name = str(obj, "name");
        if (name != null && batch.containsKey(key(name))) return key(name);
        return null;
    }

    private synchronized void onSuccess() {
        backoffMs = 0L;
    }

    private synchronized void onFailure(int status) {
        long now = System.currentTimeMillis();
        // 429, 5xx, timeouts, no connection: back off exponentially.
        backoffMs = backoffMs == 0L ? MIN_BACKOFF_MS : Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        backoffUntil = now + backoffMs;
        LOGGER.debug("[Recruitment] EarthMC API returned {}, backing off {} ms", status, backoffMs);
    }

    /**
     * @param requestedName the name we asked about; used as the display name when the API
     *                      still has an older name for a renamed account (matched by UUID).
     */
    private PlayerProfile parseProfile(JsonObject p, String requestedName) {
        String name = str(p, "name");
        if (requestedName != null && (name == null || !name.equalsIgnoreCase(requestedName))) name = requestedName;
        if (name == null) return null;
        String town = objectName(p, "town");
        String nation = objectName(p, "nation");
        long registeredMs = 0L;
        if (p.has("timestamps") && p.get("timestamps").isJsonObject()) {
            JsonObject ts = p.getAsJsonObject("timestamps");
            if (ts.has("registered") && ts.get("registered").isJsonPrimitive()) {
                registeredMs = ts.get("registered").getAsLong();
            }
        }
        return new PlayerProfile(name, town, nation, registeredMs);
    }

    private void cacheProfile(String k, PlayerProfile profile, long fetchedAt) {
        profiles.put(k, profile);
        profileFetchedAt.put(k, fetchedAt);
        absentUntil.remove(k);
    }

    // ── HTTP ────────────────────────────────────────────────────────────────--

    private record Response(int status, String body) {}

    private Response post(String url, String body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept-Encoding", "gzip")
                .header("User-Agent", "EarthMC-Recruitment-Addon/1.0 (Fabric Mod)")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        byte[] bytes = resp.body();
        boolean gzip = resp.headers().firstValue("Content-Encoding")
                .map(v -> v.equalsIgnoreCase("gzip")).orElse(false);
        if (gzip && bytes.length > 0) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                bytes = in.readAllBytes();
            }
        }
        return new Response(resp.statusCode(), new String(bytes, StandardCharsets.UTF_8));
    }

    // ── helpers ────────────────────────────────────────────────────────────---

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static String str(JsonObject obj, String k) {
        return obj.has(k) && obj.get(k).isJsonPrimitive() ? obj.get(k).getAsString() : null;
    }

    private static String objectName(JsonObject obj, String k) {
        if (obj.has(k) && obj.get(k).isJsonObject()) {
            JsonObject o = obj.getAsJsonObject(k);
            return o.has("name") && o.get("name").isJsonPrimitive() ? o.get("name").getAsString() : "";
        }
        return "";
    }
}
