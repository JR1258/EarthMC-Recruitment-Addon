package net.recruitmentaddon.alert;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.api.EarthMcData;
import net.recruitmentaddon.model.PlayerProfile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Watches the server player list. When a player who wasn't online before appears, and
 * the official API confirms their account is newly registered, a clickable chat line is
 * posted; clicking it runs the configured recruit command for that player.
 *
 * The player list populates incrementally right after you connect, so everyone seen in
 * the first few seconds is taken as the baseline — you're only pinged for genuine joins.
 */
public final class JoinAlerter {

    /** Window after (re)connect during which arrivals are treated as the baseline. */
    private static final long GRACE_MS = 8_000L;

    private final Set<String> previousOnline = new HashSet<>(); // lower-case keys
    private final Set<String> pending = new HashSet<>();        // original names, awaiting profile
    private final Set<String> alerted = new HashSet<>();        // lower-case keys, pinged this session
    private boolean graceStarted = false;
    private long graceUntil = 0L;

    public void reset() {
        previousOnline.clear();
        pending.clear();
        alerted.clear();
        graceStarted = false;
        graceUntil = 0L;
    }

    public void update(Set<String> online, EarthMcData data, RecruitmentConfig config) {
        if (online.isEmpty()) return; // player list not populated yet

        Map<String, String> currentByKey = new HashMap<>();
        for (String n : online) currentByKey.put(key(n), n);
        Set<String> currentKeys = currentByKey.keySet();

        // Disabled → keep the baseline fresh so toggling on later doesn't dump everyone.
        if (!config.enabled) {
            previousOnline.clear();
            previousOnline.addAll(currentKeys);
            pending.clear();
            graceStarted = true;
            graceUntil = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (!graceStarted) {
            graceStarted = true;
            graceUntil = now + GRACE_MS;
        }
        if (now < graceUntil) {
            previousOnline.addAll(currentKeys); // still settling — everyone is baseline
            return;
        }

        // Genuine new arrivals since the last poll.
        for (String k : currentKeys) {
            String name = currentByKey.get(k);
            if (!previousOnline.contains(k) && !alerted.contains(k) && !RecruitmentAddon.isExcluded(name)) {
                pending.add(name);
            }
        }

        if (!pending.isEmpty()) {
            data.requestProfiles(pending);
            pending.removeIf(name -> {
                String k = key(name);
                if (!currentKeys.contains(k)) return true;         // left before we decided
                PlayerProfile profile = data.profile(name);
                if (profile == null) return false;                 // still waiting on the API
                if (profile.newlyRegistered(config.newPlayerMaxDays)) {
                    postRecruitMessage(profile.name(), config);
                    alerted.add(k);
                }
                return true;                                       // decided either way
            });
        }

        previousOnline.clear();
        previousOnline.addAll(currentKeys);
    }

    /** Posts the clickable recruit line for {@code name}. Shared by the alerter and {@code /recruit test}. */
    public static void postRecruitMessage(String name, RecruitmentConfig config) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String command = config.recruitMessage.replace("{player}", name);
        Text message = Text.literal("[Recruitment] ").formatted(Formatting.AQUA)
                .append(Text.literal(name).formatted(Formatting.YELLOW))
                .append(Text.literal(" just joined as a new player — ").formatted(Formatting.GRAY))
                .append(Text.literal("[Click to recruit]").styled(s -> s
                        .withColor(Formatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Sends: " + command)))));
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendMessage(message, false);
        });
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
