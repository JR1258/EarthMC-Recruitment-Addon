package net.recruitmentaddon.alert;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
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
        Minecraft mc = Minecraft.getInstance();
        boolean useShowItem = config.globalAdUseShowItem;
        String actionText = useShowItem ? "Suggest /showitem" : "Copy global ad";
        String hoverText = useShowItem ? "Suggests: /showitem" : "Copies: " + config.globalAdMessage;
        ClickEvent click = useShowItem
                ? new ClickEvent.SuggestCommand("/showitem")
                : new ClickEvent.CopyToClipboard(config.globalAdMessage == null ? "" : config.globalAdMessage);
        Component message = Component.literal("[Recruitment] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("Global ad reminder - ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[" + actionText + "]").withStyle(s -> s
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(click)
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText)))));
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(message);
            if (config.soundEnabled) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
            }
        });
    }

    private static long intervalMs(RecruitmentConfig config) {
        return Math.max(5, config.globalAdReminderMinutes) * 60_000L;
    }
}
