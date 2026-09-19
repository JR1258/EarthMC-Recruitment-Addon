package net.recruitmentaddon.alert;

import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.api.EarthMcData;
import net.recruitmentaddon.model.PlayerProfile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks townless online players and manages the HUD display list.
 * Players are synced from the Minecraft player list each tick and filtered
 * by account age, town status, and invite state.
 */
public final class TownlessTracker {

    private static final long GRACE_MS = 8_000L;

    // "You have invited PlayerName to your town" / "PlayerName has been sent a town invitation"
    private static final Pattern INVITED = Pattern.compile(
            "(?i)invited\\s+([A-Za-z0-9_]{3,16})\\b|\\b([A-Za-z0-9_]{3,16})\\s+has been sent a town invitation"
    );
    // /t invite sent response: "Pending town invitations: PlayerA, PlayerB"
    private static final Pattern INVITE_LIST = Pattern.compile(
            "(?i)pending\\s+(?:town\\s+)?invit(?:ation|e)s?\\s*[:\\s]\\s*([A-Za-z0-9_,\\s]+)"
    );

    private final Map<String, String> onlinePlayers = new ConcurrentHashMap<>();     // lowercase -> display name
    private final LinkedHashMap<String, String> townlessVisible = new LinkedHashMap<>(); // lowercase -> display name
    private final Set<String> invited = ConcurrentHashMap.newKeySet();               // lowercase
    private long graceUntil = 0L;

    public void reset() {
        onlinePlayers.clear();
        townlessVisible.clear();
        invited.clear();
        graceUntil = System.currentTimeMillis() + GRACE_MS;
    }

    /** Replaces the tracked online player map from the live Minecraft player list. */
    public void syncOnlinePlayers(Map<String, String> lowercaseToDisplay) {
        onlinePlayers.keySet().retainAll(lowercaseToDisplay.keySet());
        onlinePlayers.putAll(lowercaseToDisplay);
        townlessVisible.keySet().retainAll(lowercaseToDisplay.keySet());
    }

    /** Marks a player as invited, removing them from the HUD list. */
    public void onInvited(String name) {
        if (name == null) return;
        String k = name.toLowerCase(Locale.ROOT);
        invited.add(k);
        townlessVisible.remove(k);
    }

    /** Parses incoming system messages for invite confirmations and /t invite sent output. */
    public void onMessage(String message) {
        if (message == null) return;
        Matcher m = INVITED.matcher(message);
        if (m.find()) {
            String name = m.group(1) != null ? m.group(1) : m.group(2);
            if (name != null) onInvited(name);
            return;
        }
        Matcher list = INVITE_LIST.matcher(message);
        if (list.find()) {
            for (String n : list.group(1).split("[,\\s]+")) {
                String trimmed = n.trim();
                if (!trimmed.isBlank()) onInvited(trimmed);
            }
        }
    }

    public void update(EarthMcData data, RecruitmentConfig config) {
        if (!config.townlessHudEnabled) {
            townlessVisible.clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (now < graceUntil) return;
        long minAgeMs = parseAgeMs(config.townlessMinAge);

        // Batch-request profiles we haven't fetched yet
        List<String> toRequest = new ArrayList<>();
        for (Map.Entry<String, String> e : onlinePlayers.entrySet()) {
            if (!invited.contains(e.getKey()) && data.profile(e.getKey()) == null) {
                toRequest.add(e.getValue());
            }
        }
        if (!toRequest.isEmpty()) data.requestProfiles(toRequest);

        // Promote confirmed-townless players to the visible list
        for (Map.Entry<String, String> e : new ArrayList<>(onlinePlayers.entrySet())) {
            String k = e.getKey();
            if (townlessVisible.containsKey(k) || invited.contains(k)) continue;
            PlayerProfile profile = data.profile(k);
            if (profile == null) continue;
            if (!profile.townless()) continue;
            if (minAgeMs > 0 && profile.registeredMs() > 0 && now - profile.registeredMs() < minAgeMs) continue;
            townlessVisible.put(k, e.getValue());
        }

        // Remove players who got a town since we last checked (profile TTL: 60s)
        townlessVisible.entrySet().removeIf(e -> {
            PlayerProfile p = data.profile(e.getKey());
            return p != null && !p.townless();
        });
    }

    public List<String> getDisplayList() {
        return new ArrayList<>(townlessVisible.values());
    }

    /**
     * Parses a human-readable duration string into milliseconds.
     * Accepted suffixes: d (days), h (hours), m (minutes), s (seconds).
     * A bare number is treated as seconds. Returns 1 day on invalid input.
     */
    public static long parseAgeMs(String age) {
        if (age == null || age.isBlank()) return 86_400_000L;
        String s = age.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.endsWith("d")) return Long.parseLong(s.substring(0, s.length() - 1)) * 86_400_000L;
            if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 3_600_000L;
            if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) *     60_000L;
            if (s.endsWith("s")) return Long.parseLong(s.substring(0, s.length() - 1)) *      1_000L;
            return Long.parseLong(s) * 1_000L;
        } catch (NumberFormatException ignored) {
            return 86_400_000L;
        }
    }
}
