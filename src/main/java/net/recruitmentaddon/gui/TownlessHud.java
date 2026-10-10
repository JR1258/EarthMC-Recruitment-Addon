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
        net.minecraft.client.gui.screens.Screen screen = mc.gui.screen();
        if (screen != null && !(screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        List<TownlessTracker.Entry> players = capped(RecruitmentAddon.townlessTracker().getDisplayList(), config.townlessHudMaxPlayers);
        if (players.isEmpty()) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int hw = hudWidth(mc.font, players);
        int hh = hudHeight(mc.font, players);
        int dx = Math.min(Math.max(config.townlessHudX, 3), sw - hw + 3);
        int dy = Math.min(Math.max(config.townlessHudY, 3), sh - hh + 3);
        renderAt(ctx, mc.font, players, dx, dy);
    }

    private static final int MAX_W = 160;
    private static final int AGE_GAP = 4;

    static void renderAt(GuiGraphicsExtractor ctx, Font font, List<TownlessTracker.Entry> players, int x, int y) {
        long now = System.currentTimeMillis();
        int lh = font.lineHeight + 2;
        String header = "Townless (" + players.size() + ")";
        int naturalW = font.width(header);
        for (TownlessTracker.Entry e : players) {
            String age = formatAge(now - e.registeredMs());
            naturalW = Math.max(naturalW, font.width(e.displayName()) + AGE_GAP + font.width(age));
        }
        int boxW = Math.min(naturalW, MAX_W);
        ctx.fill(x - 3, y - 3, x + boxW + 3, y + lh * (players.size() + 1) + 3, 0x80000000);
        ctx.text(font, Component.literal(header), x, y, 0xFFAAAAAA);
        y += lh;
        for (TownlessTracker.Entry e : players) {
            long ageMs = now - e.registeredMs();
            String age = formatAge(ageMs);
            int ageW = font.width(age);
            String name = truncate(font, e.displayName(), boxW - AGE_GAP - ageW);
            ctx.text(font, Component.literal(name), x, y, 0xFFFFFFFF);
            ctx.text(font, Component.literal(age), x + boxW - ageW, y, ageColor(ageMs));
            y += lh;
        }
    }

    static int hudWidth(Font font, List<TownlessTracker.Entry> players) {
        long now = System.currentTimeMillis();
        String header = "Townless (" + players.size() + ")";
        int naturalW = font.width(header);
        for (TownlessTracker.Entry e : players) {
            String age = formatAge(now - e.registeredMs());
            naturalW = Math.max(naturalW, font.width(e.displayName()) + AGE_GAP + font.width(age));
        }
        return Math.min(naturalW, MAX_W) + 6;
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        int ellipsisW = font.width(ellipsis);
        while (!text.isEmpty() && font.width(text) + ellipsisW > maxWidth)
            text = text.substring(0, text.length() - 1);
        return text.isEmpty() ? ellipsis : text + ellipsis;
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
        if (hours < 24) return 0xFF55FF55;  // green — under 1 day
        if (hours < 72) return 0xFFFFFF55;  // yellow — 1–3 days
        return 0xFFFFFFFF;                  // white — older
    }

    private static <T> List<T> capped(List<T> list, int max) {
        if (max > 0 && list.size() > max) return list.subList(0, max);
        return list;
    }
}
