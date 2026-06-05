package net.recruitmentaddon.alert;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.api.EarthMcData;
import net.recruitmentaddon.model.PlayerProfile;

import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/**
 * Consumes server player-list ADD_PLAYER packets. When the official API confirms the
 * added player's account was registered moments ago, a clickable chat line is posted;
 * clicking it runs the configured recruit command for that player.
 *
 * The player list populates incrementally right after you connect, so everyone seen in
 * the first few seconds is taken as the baseline — you're only pinged for genuine joins.
 */
public final class JoinAlerter {

    /** Window after (re)connect during which arrivals are treated as the baseline. */
    private static final long GRACE_MS = 8_000L;
    private static final long PENDING_TTL_MS = 120_000L;
    private static final long PENDING_REFRESH_MS = 3_000L;

    private final Map<String, PendingJoin> pending = new HashMap<>(); // lower-case key -> pending join
    private final Map<String, Long> alertedAt = new HashMap<>();      // lower-case keys -> last alert time
    private final Map<String, ScheduledAlert> scheduled = new HashMap<>();
    private long graceUntil = 0L;

    public void reset() {
        pending.clear();
        scheduled.clear();
        alertedAt.clear();
        graceUntil = System.currentTimeMillis() + GRACE_MS;
    }

    public void onPlayerAdded(String name, EarthMcData data, RecruitmentConfig config) {
        if (name == null || name.isBlank() || !config.enabled) return;
        if (RecruitmentAddon.isExcluded(name)) return;
        long now = System.currentTimeMillis();
        if (now < graceUntil) return;
        String k = key(name);
        if (onCooldown(k, now, config) || pending.containsKey(k)) return;
        pending.put(k, new PendingJoin(name, now, 0L));
        data.requestFreshProfile(name);
    }

    public void update(EarthMcData data, RecruitmentConfig config) {
        if (!config.enabled) {
            pending.clear();
            scheduled.clear();
            return;
        }

        long now = System.currentTimeMillis();
        postDueAlerts(config, now);
        if (pending.isEmpty()) return;
        pending.entrySet().removeIf(entry -> {
            PendingJoin join = entry.getValue();
            if (now - join.seenAtMs() > PENDING_TTL_MS) return true;
            PlayerProfile profile = data.profile(join.name());
            if (profile == null && now - join.lastRefreshMs() >= PENDING_REFRESH_MS) {
                join.lastRefreshMs(now);
                data.requestFreshProfile(join.name());
            }
            if (profile == null) return false;
            if (profile.registeredWithinSeconds(config.newPlayerMaxSeconds)) {
                long ageMs = System.currentTimeMillis() - profile.registeredMs();
                long joinLagMs = join.seenAtMs() - profile.registeredMs();
                if (ageMs >= 0 && joinLagMs >= 0 && joinLagMs <= config.newPlayerMaxSeconds * 1_000L) {
                    scheduleRecruitMessage(entry.getKey(), profile.name(), config, now);
                    alertedAt.put(entry.getKey(), System.currentTimeMillis());
                }
            }
            return true;
        });
    }

    /** Posts the clickable recruit line for {@code name}. Shared by the alerter and {@code /recruit test}. */
    public static void postRecruitMessage(String name, RecruitmentConfig config) {
        postCopyPrompt(name, config, "just joined as a new player", "Copy recruit message", config.recruitMessage);
    }

    private void scheduleRecruitMessage(String key, String name, RecruitmentConfig config, long now) {
        if (config.alertDelaySeconds <= 0) {
            postRecruitMessage(name, config);
            return;
        }
        scheduled.put(key, new ScheduledAlert(name, now + config.alertDelaySeconds * 1_000L));
    }

    private void postDueAlerts(RecruitmentConfig config, long now) {
        if (scheduled.isEmpty()) return;
        scheduled.entrySet().removeIf(entry -> {
            if (now < entry.getValue().dueAtMs()) return false;
            postRecruitMessage(entry.getValue().name(), config);
            return true;
        });
    }

    public static void postFollowUpMessages(String name, RecruitmentConfig config) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (config.followUpMessages == null || config.followUpMessages.isEmpty()) return;
        Text message = Text.literal("[Recruitment] ").formatted(Formatting.AQUA)
                .append(Text.literal(name).formatted(Formatting.YELLOW))
                .append(Text.literal(" joined your town — ").formatted(Formatting.GRAY));
        boolean first = true;
        for (RecruitmentConfig.FollowUpMessage followUp : config.followUpMessages) {
            if (followUp == null || blank(followUp.title) || blank(followUp.message)) continue;
            if (!first) message = message.copy().append(Text.literal(" ").formatted(Formatting.GRAY));
            first = false;
            String copied = followUp.message.replace("{player}", name);
            message = message.copy().append(Text.literal("[" + followUp.title + "]").styled(s -> s
                    .withColor(Formatting.GREEN)
                    .withBold(true)
                    .withClickEvent(new ClickEvent.CopyToClipboard(copied))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Copies: " + copied)))));
        }
        Text finalMessage = message;
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendMessage(finalMessage, false);
            playPromptSound(mc, config);
        });
    }

    private static void postCopyPrompt(String name, RecruitmentConfig config, String reason, String button, String template) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String copied = template.replace("{player}", name);
        Text message = Text.literal("[Recruitment] ").formatted(Formatting.AQUA)
                .append(Text.literal(name).formatted(Formatting.YELLOW))
                .append(Text.literal(" " + reason + " — ").formatted(Formatting.GRAY))
                .append(Text.literal("[" + button + "]").styled(s -> s
                        .withColor(Formatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent.CopyToClipboard(copied))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Copies: " + copied)))));
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendMessage(message, false);
            playPromptSound(mc, config);
        });
    }

    private static void playPromptSound(MinecraftClient mc, RecruitmentConfig config) {
        if (!config.soundEnabled) return;
        mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.2F));
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean onCooldown(String key, long now, RecruitmentConfig config) {
        Long previous = alertedAt.get(key);
        if (previous == null) return false;
        return now - previous < config.alertCooldownMinutes * 60_000L;
    }

    private static final class PendingJoin {
        private final String name;
        private final long seenAtMs;
        private long lastRefreshMs;

        private PendingJoin(String name, long seenAtMs, long lastRefreshMs) {
            this.name = name;
            this.seenAtMs = seenAtMs;
            this.lastRefreshMs = lastRefreshMs;
        }

        private String name() { return name; }
        private long seenAtMs() { return seenAtMs; }
        private long lastRefreshMs() { return lastRefreshMs; }
        private void lastRefreshMs(long value) { this.lastRefreshMs = value; }
    }

    private record ScheduledAlert(String name, long dueAtMs) {}
}
