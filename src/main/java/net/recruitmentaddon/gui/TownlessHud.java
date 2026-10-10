package net.recruitmentaddon.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.alert.TownlessTracker;

import java.util.List;

public final class TownlessHud {

    private TownlessHud() {}

    public static void render(DrawContext ctx) {
        RecruitmentConfig config = RecruitmentAddon.config();
        if (config == null || !config.townlessHudEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;
        List<TownlessTracker.Entry> players = capped(RecruitmentAddon.townlessTracker().getDisplayList(), config.townlessHudMaxPlayers);
        if (players.isEmpty()) return;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int hw = hudWidth(mc.textRenderer, players);
        int hh = hudHeight(mc.textRenderer, players);
        int dx = Math.min(Math.max(config.townlessHudX, 3), sw - hw + 3);
        int dy = Math.min(Math.max(config.townlessHudY, 3), sh - hh + 3);
        renderAt(ctx, mc.textRenderer, players, dx, dy);
    }

    private static final int MAX_W = 160;
    private static final int AGE_GAP = 4;

    static void renderAt(DrawContext ctx, TextRenderer tr, List<TownlessTracker.Entry> players, int x, int y) {
        long now = System.currentTimeMillis();
        int lh = tr.fontHeight + 2;
        String header = "Townless (" + players.size() + ")";
        int naturalW = tr.getWidth(header);
        for (TownlessTracker.Entry e : players) {
            String age = formatAge(now - e.registeredMs());
            naturalW = Math.max(naturalW, tr.getWidth(e.displayName()) + AGE_GAP + tr.getWidth(age));
        }
        int boxW = Math.min(naturalW, MAX_W);
        ctx.fill(x - 3, y - 3, x + boxW + 3, y + lh * (players.size() + 1) + 3, 0x80000000);
        ctx.drawTextWithShadow(tr, header, x, y, 0xFFAAAAAA);
        y += lh;
        for (TownlessTracker.Entry e : players) {
            long ageMs = now - e.registeredMs();
            String age = formatAge(ageMs);
            int ageW = tr.getWidth(age);
            String name = truncate(tr, e.displayName(), boxW - AGE_GAP - ageW);
            ctx.drawTextWithShadow(tr, name, x, y, 0xFFFFFFFF);
            ctx.drawTextWithShadow(tr, age, x + boxW - ageW, y, ageColor(ageMs));
            y += lh;
        }
    }

    static int hudWidth(TextRenderer tr, List<TownlessTracker.Entry> players) {
        long now = System.currentTimeMillis();
        String header = "Townless (" + players.size() + ")";
        int naturalW = tr.getWidth(header);
        for (TownlessTracker.Entry e : players) {
            String age = formatAge(now - e.registeredMs());
            naturalW = Math.max(naturalW, tr.getWidth(e.displayName()) + AGE_GAP + tr.getWidth(age));
        }
        return Math.min(naturalW, MAX_W) + 6;
    }

    private static String truncate(TextRenderer tr, String text, int maxWidth) {
        if (tr.getWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        int ellipsisW = tr.getWidth(ellipsis);
        while (!text.isEmpty() && tr.getWidth(text) + ellipsisW > maxWidth)
            text = text.substring(0, text.length() - 1);
        return text.isEmpty() ? ellipsis : text + ellipsis;
    }

    static int hudHeight(TextRenderer tr, List<TownlessTracker.Entry> players) {
        return (tr.fontHeight + 2) * (players.size() + 1) + 6;
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
