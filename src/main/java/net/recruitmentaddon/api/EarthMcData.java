package net.recruitmentaddon.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.model.PlayerProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cached access to the official EarthMC API. Only used to look up a player's
 * town / nation / registration date, <b>batched</b> into one request and cached with a TTL.
 *
 * One reused {@link HttpClient}; parsing is pure Java (no Minecraft types), so this class
 * can later move into a shared EarthMC API library used by several mods.
 */
public final class EarthMcData {

    private static final Logger LOGGER = LoggerFactory.getLogger("RecruitmentAddon");
    private static final long PROFILE_TTL_MS = 60_000L;
    private static final int MAX_QUERY_BATCH = 50;

    private final RecruitmentConfig config;
    private final HttpClient http;
    private final ExecutorService executor;

    private final Map<String, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, Long> profileFetchedAt = new ConcurrentHashMap<>();
    private final AtomicBoolean profileFetchRunning = new AtomicBoolean(false);

    public EarthMcData(RecruitmentConfig config) {
        this.config = config;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(executor)
                .build();
    }

    /** Cached profile for a player, or null if not fetched yet. */
    public PlayerProfile profile(String name) {
        return profiles.get(key(name));
    }

    public void clear() {
        profiles.clear();
        profileFetchedAt.clear();
    }

    /** Fetches town/nation/registration for the given names that are missing or stale. */
    public void requestProfiles(Collection<String> names) {
        if (names.isEmpty()) return;
        long now = System.currentTimeMillis();
        ArrayList<String> needed = new ArrayList<>();
        for (String name : names) {
            String k = key(name);
            Long at = profileFetchedAt.get(k);
            if (at == null || now - at >= PROFILE_TTL_MS) needed.add(name);
            if (needed.size() >= MAX_QUERY_BATCH) break;
        }
        if (needed.isEmpty()) return;
        if (!profileFetchRunning.compareAndSet(false, true)) return;
        executor.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                JsonArray query = new JsonArray();
                needed.forEach(query::add);
                body.add("query", query);
                String json = post(config.earthmcApiBaseUrl + "/players", body.toString());
                long fetchedAt = System.currentTimeMillis();
                // Mark all requested as attempted (avoids re-querying unknown names every tick).
                for (String name : needed) profileFetchedAt.put(key(name), fetchedAt);
                if (json == null) return;
                JsonElement root = JsonParser.parseString(json);
                if (!root.isJsonArray()) return;
                for (JsonElement el : root.getAsJsonArray()) {
                    if (!el.isJsonObject()) continue;
                    PlayerProfile profile = parseProfile(el.getAsJsonObject());
                    if (profile != null) profiles.put(key(profile.name()), profile);
                }
            } catch (Exception e) {
                LOGGER.debug("[Recruitment] profile batch failed: {}", e.getMessage());
            } finally {
                profileFetchRunning.set(false);
            }
        });
    }

    private PlayerProfile parseProfile(JsonObject p) {
        String name = str(p, "name");
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

    // ── HTTP ────────────────────────────────────────────────────────────────--

    private String post(String url, String body) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "EarthMC-Recruitment-Addon/1.0 (Fabric Mod)")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (Exception e) {
            return null;
        }
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
