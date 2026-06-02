package net.recruitmentaddon.alert;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.recruitmentaddon.RecruitmentConfig;

public final class GlobalAdReminder {

    private long nextReminderAtMs = 0L;

    public void reset() {
        nextReminderAtMs = 0L;
    }

    public void update(RecruitmentConfig config) {
        if (!config.enabled || !config.globalAdReminderEnabled) {
            reset();
            return;
        }
        long now = System.currentTimeMillis();
        if (nextReminderAtMs == 0L) {
            nextReminderAtMs = now + intervalMs(config);
            return;
        }
        if (now < nextReminderAtMs) return;
        postGlobalAdPrompt(config);
        nextReminderAtMs = now + intervalMs(config);
    }

    public static void postGlobalAdPrompt(RecruitmentConfig config) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean useShowItem = config.globalAdUseShowItem;
        String actionText = useShowItem ? "Suggest /showitem" : "Copy global ad";
        String hoverText = useShowItem ? "Suggests: /showitem" : "Copies: " + config.globalAdMessage;
        ClickEvent click = useShowItem
                ? new ClickEvent.SuggestCommand("/showitem")
                : new ClickEvent.CopyToClipboard(config.globalAdMessage == null ? "" : config.globalAdMessage);
        Text message = Text.literal("[Recruitment] ").formatted(Formatting.AQUA)
                .append(Text.literal("Global ad reminder — ").formatted(Formatting.GRAY))
                .append(Text.literal("[" + actionText + "]").styled(s -> s
                        .withColor(Formatting.GREEN)
                        .withBold(true)
                        .withClickEvent(click)
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal(hoverText)))));
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendMessage(message, false);
            if (config.soundEnabled) {
                mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.2F));
            }
        });
    }

    private static long intervalMs(RecruitmentConfig config) {
        return Math.max(5, config.globalAdReminderMinutes) * 60_000L;
    }
}
