package net.recruitmentaddon.alert;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.api.EarthMcData;
import net.recruitmentaddon.model.LivePlayer;
import net.recruitmentaddon.model.PlayerProfile;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Watches the online-player feed and, when a <i>newly-registered</i> player first
 * appears online, sends a clickable chat message; clicking it runs the configured
 * recruit command for that player.
 *
 * Players already online when you join are taken as the baseline (no alert), so you
 * only get pinged for genuine new arrivals.
 */
public final class JoinAlerter {

    private final Set<String> previousOnline = new HashSet<>();
    private final Set<String> pending = new HashSet<>();   // appeared, awaiting profile decision
    private final Set<String> alerted = new HashSet<>();   // already pinged this session
    private boolean baselineSet = false;

    public void reset() {
        previousOnline.clear();
        pending.clear();
        alerted.clear();
        baselineSet = false;
    }

    public void update(List<LivePlayer> online, EarthMcData data, RecruitmentConfig config) {
        if (!config.enabled || !config.newPlayerAlertEnabled) {
            // Keep the baseline current so toggling on later doesn't dump everyone.
            refreshBaseline(online);
            return;
        }

        Set<String> current = new HashSet<>();
        for (LivePlayer p : online) current.add(key(p.name()));

        if (!baselineSet) {
            previousOnline.clear();
            previousOnline.addAll(current);
            baselineSet = true;
            return;
        }

        // New arrivals since last poll → start tracking them.
        for (LivePlayer p : online) {
            String k = key(p.name());
            if (!previousOnline.contains(k) && !alerted.contains(k) && !RecruitmentAddon.isExcluded(p.name())) {
                pending.add(k);
            }
        }

        // Resolve pending arrivals as their profiles arrive.
        if (!pending.isEmpty()) {
            data.requestProfiles(pending.stream().toList());
            pending.removeIf(k -> {
                if (!current.contains(k)) return true;            // left before we decided
                PlayerProfile profile = data.profile(k);
                if (profile == null) return false;                // still waiting on data
                if (profile.newlyRegistered(config.newPlayerMaxDays)) {
                    alert(profile.name(), config);
                    alerted.add(k);
                }
                return true;                                       // decided either way
            });
        }

        previousOnline.clear();
        previousOnline.addAll(current);
    }

    private void refreshBaseline(List<LivePlayer> online) {
        previousOnline.clear();
        for (LivePlayer p : online) previousOnline.add(key(p.name()));
        baselineSet = true;
        pending.clear();
    }

    private void alert(String name, RecruitmentConfig config) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String command = config.recruitMessage.replace("{player}", name);
        Text message = Text.literal("[Recruitment] ").formatted(Formatting.AQUA)
                .append(Text.literal(name).formatted(Formatting.YELLOW))
                .append(Text.literal(" is a new player online — ").formatted(Formatting.GRAY))
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
