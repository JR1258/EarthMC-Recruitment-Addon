package net.recruitmentaddon.alert;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
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
 * Consumes server player-list ADD_PLAYER packets. When the official API confirms the
 * added player's account was registered moments ago, a clickable chat line is posted;
 * clicking it runs the configured recruit command for that player.
 *
 * The player list populates incrementally right after you connect, so everyone seen in
 * the first few seconds is taken as the baseline — you're only pinged for genuine joins.
 *
 * Townless players whose account is still recent (see {@code returningTownlessMaxDays})
 * are alerted too when they come back online, once per game session.
 */
public final class JoinAlerter {

    /** Window after (re)connect during which arrivals are treated as the baseline. */
    private static final long GRACE_MS = 8_000L;
    private static final long PENDING_TTL_MS = 120_000L;
    private static final long PENDING_REFRESH_MS = 3_000L;
    /** Players the API keeps answering without (opted out) are dropped after this long, not PENDING_TTL_MS. */
    private static final long ABSENT_GIVE_UP_MS = 15_000L;
    /** How long a just-added tab entry may stay unlisted before we treat the player as gone. */
    private static final long LIST_SETTLE_MS = 3_000L;
    // EarthMC writes the registration timestamp asynchronously, so it can land a few
    // seconds AFTER the ADD_PLAYER packet reaches us — joinLagMs goes slightly negative.
    private static final long ASYNC_REGISTRATION_TOLERANCE_MS = 30_000L;
    private static final String NEW_PLAYER_REASON = "just joined as a new player";

    private final Map<String, PendingJoin> pending = new HashMap<>(); // lower-case key -> pending join
    private final Map<String, Long> alertedAt = new HashMap<>();      // lower-case keys -> last alert time
    private final Map<String, ScheduledAlert> scheduled = new HashMap<>();
    /** Returning townless players already alerted this game session; survives reconnects. */
    private final Set<String> returningAlerted = new HashSet<>();
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
        pending.put(k, new PendingJoin(name, now, now));
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
            if (!RecruitmentAddon.isListedOnline(join.name())) {
                return now - join.seenAtMs() > LIST_SETTLE_MS;
            }
            PlayerProfile profile = data.profile(join.name());
            if (profile == null) {
                // A brand-new account can briefly be missing, so retry a few times before
                // concluding the player has opted out of the API.
                if (data.isAbsent(join.name()) && now - join.seenAtMs() > ABSENT_GIVE_UP_MS) return true;
                if (now - join.lastRefreshMs() >= PENDING_REFRESH_MS) {
                    join.lastRefreshMs(now);
                    data.requestFreshProfile(join.name());
                }
                return false;
            }
            // Compare when we SAW the player (seenAtMs) to their EarthMC registration,
            // not the current time. Using now would silently drop alerts whenever the API
            // responds slower than newPlayerMaxSeconds.
            long joinLagMs = join.seenAtMs() - profile.registeredMs();
            if (profile.registeredMs() <= 0) return true;
            String reason = null;
            if (joinLagMs >= -ASYNC_REGISTRATION_TOLERANCE_MS && joinLagMs <= config.newPlayerMaxSeconds * 1_000L) {
                reason = NEW_PLAYER_REASON;
            } else if (isReturningTownless(entry.getKey(), profile, config, join.seenAtMs())
                    && returningAlerted.add(entry.getKey())) {
                reason = "came online without a town (account " + formatAge(joinLagMs) + " old)";
            }
            if (reason != null) {
                scheduleRecruitMessage(entry.getKey(), profile.name(), reason, config, now);
                alertedAt.put(entry.getKey(), now);
                TownlessTracker tracker = RecruitmentAddon.townlessTracker();
                if (tracker != null) tracker.addJoinAlertPlayer(profile.name(), profile.registeredMs());
            }
            return true;
        });
    }

    /** Posts the clickable recruit line for {@code name}. Shared by the alerter and {@code /recruit test}. */
    public static void postRecruitMessage(String name, RecruitmentConfig config) {
        postRecruitMessage(name, NEW_PLAYER_REASON, config);
    }

    private static void postRecruitMessage(String name, String reason, RecruitmentConfig config) {
        postSuggestPrompt(name, config, reason, "Suggest recruit message", config.recruitMessage);
    }

    /** A townless player with a recent account who isn't brand-new (e.g. logging back in a day later). */
    private static boolean isReturningTownless(String key, PlayerProfile profile, RecruitmentConfig config, long seenAtMs) {
        if (!config.returningTownlessAlerts || !profile.townless()) return false;
        if (seenAtMs - profile.registeredMs() > config.returningTownlessMaxDays * 86_400_000L) return false;
        TownlessTracker tracker = RecruitmentAddon.townlessTracker();
        return tracker == null || !tracker.isInvited(key);
    }

    private static String formatAge(long ms) {
        long minutes = Math.max(0L, ms) / 60_000L;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 48) return hours + "h";
        return (hours / 24) + "d";
    }

    private void scheduleRecruitMessage(String key, String name, String reason, RecruitmentConfig config, long now) {
        if (config.alertDelaySeconds <= 0) {
            postRecruitMessage(name, reason, config);
            return;
        }
        scheduled.put(key, new ScheduledAlert(name, reason, now + config.alertDelaySeconds * 1_000L));
    }

    private void postDueAlerts(RecruitmentConfig config, long now) {
        if (scheduled.isEmpty()) return;
        scheduled.entrySet().removeIf(entry -> {
            if (now < entry.getValue().dueAtMs()) return false;
            if (RecruitmentAddon.isListedOnline(entry.getValue().name())) {
                postRecruitMessage(entry.getValue().name(), entry.getValue().reason(), config);
            }
            return true;
        });
    }

    public static void postFollowUpMessages(String name, RecruitmentConfig config) {
        Minecraft mc = Minecraft.getInstance();
        if (config.followUpMessages == null || config.followUpMessages.isEmpty()) return;
        Component message = Component.literal("[Recruitment] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" joined your town - ").withStyle(ChatFormatting.GRAY));
        boolean first = true;
        for (RecruitmentConfig.FollowUpMessage followUp : config.followUpMessages) {
            if (followUp == null || blank(followUp.title) || blank(followUp.message)) continue;
            if (!first) message = message.copy().append(Component.literal(" ").withStyle(ChatFormatting.GRAY));
            first = false;
            String copied = followUp.message.replace("{player}", name)
                    .replace("{town}", net.recruitmentaddon.RecruitmentAddon.selfTown());
            message = message.copy().append(Component.literal("[" + followUp.title + "]").withStyle(s -> s
                    .withColor(ChatFormatting.GREEN)
                    .withBold(true)
                    // Fills the chat bar with the text rather than silently copying to the
                    // clipboard, so you can see and edit it before sending.
                    .withClickEvent(new ClickEvent.SuggestCommand(copied))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Suggests: " + copied)))));
        }
        Component finalMessage = message;
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(finalMessage);
            playPromptSound(mc, config);
        });
    }

    private static void postSuggestPrompt(String name, RecruitmentConfig config, String reason, String button, String template) {
        Minecraft mc = Minecraft.getInstance();
        String copied = template.replace("{player}", name)
                .replace("{town}", net.recruitmentaddon.RecruitmentAddon.selfTown());
        Component message = Component.literal("[Recruitment] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" " + reason + " - ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[" + button + "]").withStyle(s -> s
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        // Fills the chat bar with the text rather than silently copying to the
                        // clipboard, so you can see and edit it before sending.
                        .withClickEvent(new ClickEvent.SuggestCommand(copied))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Suggests: " + copied)))));
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(message);
            playPromptSound(mc, config);
        });
    }

    private static void playPromptSound(Minecraft mc, RecruitmentConfig config) {
        if (!config.soundEnabled) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
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

    private record ScheduledAlert(String name, String reason, long dueAtMs) {}
}
