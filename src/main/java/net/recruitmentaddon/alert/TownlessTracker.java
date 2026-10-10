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
    private static final long RECHECK_TOWNED_MS = 300_000L; // re-classify towned players every 5 min

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
    /** Invited players that survive reconnects — so dismissed players don't reappear after log-out/rejoin. */
    private final Set<String> persistentInvited = ConcurrentHashMap.newKeySet();
    /** Players confirmed to have a town — skipped until they leave and rejoin. */
    private final Set<String> checkedTowned = ConcurrentHashMap.newKeySet();
    /** New arrivals awaiting a profile response; drained as results come back. */
    private final Map<String, String> pendingLookup = new ConcurrentHashMap<>();
    private long graceUntil = 0L;
    private long lastRecheckMs = 0L;

    public void reset() {
        onlinePlayers.clear();
        townlessVisible.clear();
        invited.clear();
        invited.addAll(persistentInvited); // restore dismissed players so they don't reappear on rejoin
        checkedTowned.clear();
        pendingLookup.clear();
        graceUntil = System.currentTimeMillis() + GRACE_MS;
    }

    public void syncOnlinePlayers(Map<String, String> lowercaseToDisplay) {
        // Enqueue genuinely new arrivals; skip anyone already classified
        for (Map.Entry<String, String> e : lowercaseToDisplay.entrySet()) {
            String k = e.getKey();
            if (!onlinePlayers.containsKey(k)
                    && !checkedTowned.contains(k)
                    && !townlessVisible.containsKey(k)
                    && !invited.contains(k)) {
                pendingLookup.put(k, e.getValue());
            }
        }
        onlinePlayers.keySet().retainAll(lowercaseToDisplay.keySet());
        onlinePlayers.putAll(lowercaseToDisplay);
        townlessVisible.keySet().retainAll(lowercaseToDisplay.keySet());
        checkedTowned.retainAll(lowercaseToDisplay.keySet());
        pendingLookup.keySet().retainAll(lowercaseToDisplay.keySet());
    }

    public void onInvited(String name) {
        if (name == null) return;
        String k = name.toLowerCase(Locale.ROOT);
        invited.add(k);
        persistentInvited.add(k);
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

    /** Called by JoinAlerter when it confirms a new player. Authoritative — overrides any API-scan classification. */
    public void addJoinAlertPlayer(String displayName, long registeredMs) {
        if (displayName == null) return;
        String k = displayName.toLowerCase(Locale.ROOT);
        if (invited.contains(k)) return;
        checkedTowned.remove(k);
        pendingLookup.remove(k);
        townlessVisible.put(k, new Entry(displayName, registeredMs));
    }

    public void update(EarthMcData data, RecruitmentConfig config) {
        if (!config.townlessHudEnabled) {
            townlessVisible.clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (now < graceUntil) return;
        long maxAgeMs = config.townlessMaxAgeDays > 0 ? (long) config.townlessMaxAgeDays * 86_400_000L : 0;

        // Remove players whose accounts have aged past the configured window
        if (maxAgeMs > 0) {
            townlessVisible.entrySet().removeIf(e -> {
                long reg = e.getValue().registeredMs();
                return reg > 0 && now - reg > maxAgeMs;
            });
        }

        // Periodically re-classify towned players; they may have left their town since last check
        if (now - lastRecheckMs >= RECHECK_TOWNED_MS) {
            lastRecheckMs = now;
            for (String k : new ArrayList<>(checkedTowned)) {
                if (onlinePlayers.containsKey(k) && !invited.contains(k)) {
                    checkedTowned.remove(k);
                    data.invalidateProfile(k);
                    pendingLookup.put(k, onlinePlayers.get(k));
                }
            }
        }

        // Request profiles for pending arrivals + re-check visible players for town joins
        List<String> toRequest = new ArrayList<>(pendingLookup.values());
        for (Entry e : townlessVisible.values()) toRequest.add(e.displayName());
        if (!toRequest.isEmpty()) data.requestProfiles(toRequest);

        // Drain pending: classify players whose profiles have now arrived
        pendingLookup.entrySet().removeIf(e -> {
            PlayerProfile p = data.profile(e.getKey());
            if (p == null) return false;
            if (p.townless()) {
                String display = p.name() != null ? p.name() : e.getValue();
                townlessVisible.put(e.getKey(), new Entry(display, p.registeredMs()));
            } else {
                checkedTowned.add(e.getKey());
            }
            return true;
        });

        // Remove visible players that have since joined a town
        townlessVisible.entrySet().removeIf(e -> {
            PlayerProfile p = data.profile(e.getKey());
            if (p != null && !p.townless()) { checkedTowned.add(e.getKey()); return true; }
            return false;
        });
    }

    /** Returns entries sorted newest-first (most recently registered account first). */
    public List<Entry> getDisplayList() {
        List<Entry> list = new ArrayList<>(townlessVisible.values());
        list.sort(Comparator.comparingLong(Entry::registeredMs).reversed());
        return list;
    }

}
