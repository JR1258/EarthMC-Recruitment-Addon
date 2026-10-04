package net.recruitmentaddon.alert;

import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.api.EarthMcData;
import net.recruitmentaddon.model.PlayerProfile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TownlessTracker {

    private static final long GRACE_MS = 8_000L;

    private static final Pattern INVITED = Pattern.compile(
            "(?i)invited\\s+([A-Za-z0-9_]{3,16})\\b|\\b([A-Za-z0-9_]{3,16})\\s+has been sent a town invitation"
    );
    private static final Pattern INVITE_LIST = Pattern.compile(
            "(?i)pending\\s+(?:town\\s+)?invit(?:ation|e)s?\\s*[:\\s]\\s*([A-Za-z0-9_,\\s]+)"
    );
    private static final Pattern OUTGOING_MSG = Pattern.compile(
            "^(?:msg|w|whisper|tell|pm)\\s+([A-Za-z0-9_]{3,16})(?:\\s|$)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OUTGOING_TOWN = Pattern.compile(
            "^t\\s+(?:add|invite)\\s+([A-Za-z0-9_]{3,16})(?:\\s|$)",
            Pattern.CASE_INSENSITIVE
    );

    /** A confirmed new player shown in the townless HUD. */
    public record Entry(String displayName, long registeredMs) {}

    private final Map<String, String> onlinePlayers = new ConcurrentHashMap<>();
    private final LinkedHashMap<String, Entry> townlessVisible = new LinkedHashMap<>();
    private final Set<String> invited = ConcurrentHashMap.newKeySet();
    private long graceUntil = 0L;

    public void reset() {
        onlinePlayers.clear();
        townlessVisible.clear();
        invited.clear();
        graceUntil = System.currentTimeMillis() + GRACE_MS;
    }

    public void syncOnlinePlayers(Map<String, String> lowercaseToDisplay) {
        onlinePlayers.keySet().retainAll(lowercaseToDisplay.keySet());
        onlinePlayers.putAll(lowercaseToDisplay);
        townlessVisible.keySet().retainAll(lowercaseToDisplay.keySet());
    }

    public void onInvited(String name) {
        if (name == null) return;
        String k = name.toLowerCase(Locale.ROOT);
        invited.add(k);
        townlessVisible.remove(k);
    }

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

    /** Removes a player when you send them a message or /t add/invite them. */
    public void onOutgoingCommand(String command) {
        if (command == null) return;
        Matcher m = OUTGOING_MSG.matcher(command);
        if (m.find()) { onInvited(m.group(1)); return; }
        m = OUTGOING_TOWN.matcher(command);
        if (m.find()) { onInvited(m.group(1)); }
    }

    /** Called by JoinAlerter when it confirms a new player. Adds them to the HUD. */
    public void addJoinAlertPlayer(String displayName, long registeredMs) {
        if (displayName == null) return;
        String k = displayName.toLowerCase(Locale.ROOT);
        if (invited.contains(k)) return;
        townlessVisible.put(k, new Entry(displayName, registeredMs));
    }

    public void update(EarthMcData data, RecruitmentConfig config) {
        if (!config.townlessHudEnabled) {
            townlessVisible.clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (now < graceUntil) return;
        long maxAgeMs = parseAgeMs(config.townlessMaxAge);

        // Remove players whose accounts have aged past the configured window
        if (maxAgeMs > 0) {
            townlessVisible.entrySet().removeIf(e -> {
                long reg = e.getValue().registeredMs();
                return reg > 0 && now - reg > maxAgeMs;
            });
        }

        // Refresh visible players so we detect when they join a town
        List<String> toRequest = new ArrayList<>();
        for (Entry e : townlessVisible.values()) {
            toRequest.add(e.displayName());
        }
        if (!toRequest.isEmpty()) data.requestProfiles(toRequest);

        // Remove players that have since joined a town
        townlessVisible.entrySet().removeIf(e -> {
            PlayerProfile p = data.profile(e.getKey());
            return p != null && !p.townless();
        });
    }

    /** Returns entries sorted newest-first (most recently registered account first). */
    public List<Entry> getDisplayList() {
        List<Entry> list = new ArrayList<>(townlessVisible.values());
        list.sort(Comparator.comparingLong(Entry::registeredMs).reversed());
        return list;
    }

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
