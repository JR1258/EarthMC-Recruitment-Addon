package net.recruitmentaddon.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.alert.TownlessTracker;

import java.util.List;

public final class TownlessHud {

    private TownlessHud() {}

    public static void render(GuiGraphicsExtractor ctx) {
        RecruitmentConfig config = RecruitmentAddon.config();
        if (config == null || !config.townlessHudEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        List<TownlessTracker.Entry> players = capped(RecruitmentAddon.townlessTracker().getDisplayList(), config.townlessHudMaxPlayers);
        if (players.isEmpty()) return;
        renderAt(ctx, mc.font, players, config.townlessHudX, config.townlessHudY);
    }

    static void renderAt(GuiGraphicsExtractor ctx, Font font, List<TownlessTracker.Entry> players, int x, int y) {
        long now = System.currentTimeMillis();
        int lh = font.lineHeight + 2;
        String header = "Townless (" + players.size() + ")";
        int maxW = font.width(header);
        for (TownlessTracker.Entry e : players) {
            maxW = Math.max(maxW, font.width(e.displayName()) + font.width("  " + formatAge(now - e.registeredMs())));
        }
        ctx.fill(x - 3, y - 3, x + maxW + 3, y + lh * (players.size() + 1) + 3, 0x80000000);
        ctx.text(font, Component.literal(header), x, y, 0xFFAAAAAA);
        y += lh;
        for (TownlessTracker.Entry e : players) {
            long ageMs = now - e.registeredMs();
            int nameW = font.width(e.displayName());
            ctx.text(font, Component.literal(e.displayName()), x, y, 0xFFFFFFFF);
            ctx.text(font, Component.literal("  " + formatAge(ageMs)), x + nameW, y, ageColor(ageMs));
            y += lh;
        }
    }

    static int hudWidth(Font font, List<TownlessTracker.Entry> players) {
        long now = System.currentTimeMillis();
        String header = "Townless (" + players.size() + ")";
        int w = font.width(header);
        for (TownlessTracker.Entry e : players) {
            w = Math.max(w, font.width(e.displayName()) + font.width("  " + formatAge(now - e.registeredMs())));
        }
        return w + 6;
    }

    static int hudHeight(Font font, List<TownlessTracker.Entry> players) {
        return (font.lineHeight + 2) * (players.size() + 1) + 6;
    }

    private static String formatAge(long ageMs) {
        if (ageMs <= 0) return "?";
        long minutes = ageMs / 60_000L;
        if (minutes < 60) return minutes + "m";
        long hours = ageMs / 3_600_000L;
        if (hours < 24) return hours + "h";
        return (ageMs / 86_400_000L) + "d";
    }

    private static int ageColor(long ageMs) {
        long hours = ageMs / 3_600_000L;
        if (hours < 24) return 0xFF55FF55;
        if (hours < 72) return 0xFFFFFF55;
        return 0xFFFFFFFF;
    }

    private static <T> List<T> capped(List<T> list, int max) {
        if (max > 0 && list.size() > max) return list.subList(0, max);
        return list;
    }
}
