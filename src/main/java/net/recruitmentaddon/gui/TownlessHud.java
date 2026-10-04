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
        if (mc.currentScreen != null || mc.player == null) return;
        List<TownlessTracker.Entry> players = capped(RecruitmentAddon.townlessTracker().getDisplayList(), config.townlessHudMaxPlayers);
        if (players.isEmpty()) return;
        renderAt(ctx, mc.textRenderer, players, config.townlessHudX, config.townlessHudY);
    }

    static void renderAt(DrawContext ctx, TextRenderer tr, List<TownlessTracker.Entry> players, int x, int y) {
        long now = System.currentTimeMillis();
        int lh = tr.fontHeight + 2;
        String header = "Townless (" + players.size() + ")";
        int maxW = tr.getWidth(header);
        for (TownlessTracker.Entry e : players) {
            maxW = Math.max(maxW, tr.getWidth(e.displayName()) + tr.getWidth("  " + formatAge(now - e.registeredMs())));
        }
        ctx.fill(x - 3, y - 3, x + maxW + 3, y + lh * (players.size() + 1) + 3, 0x80000000);
        ctx.drawTextWithShadow(tr, header, x, y, 0xFFAAAAAA);
        y += lh;
        for (TownlessTracker.Entry e : players) {
            long ageMs = now - e.registeredMs();
            int nameW = tr.getWidth(e.displayName());
            ctx.drawTextWithShadow(tr, e.displayName(), x, y, 0xFFFFFFFF);
            ctx.drawTextWithShadow(tr, "  " + formatAge(ageMs), x + nameW, y, ageColor(ageMs));
            y += lh;
        }
    }

    static int hudWidth(TextRenderer tr, List<TownlessTracker.Entry> players) {
        long now = System.currentTimeMillis();
        String header = "Townless (" + players.size() + ")";
        int w = tr.getWidth(header);
        for (TownlessTracker.Entry e : players) {
            w = Math.max(w, tr.getWidth(e.displayName()) + tr.getWidth("  " + formatAge(now - e.registeredMs())));
        }
        return w + 6;
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
