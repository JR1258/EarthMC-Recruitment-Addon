package net.recruitmentaddon.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;

import java.util.List;

public final class TownlessHud {

    private TownlessHud() {}

    public static void render(DrawContext ctx) {
        RecruitmentConfig config = RecruitmentAddon.config();
        if (config == null || !config.townlessHudEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null || mc.player == null) return;
        List<String> players = capped(RecruitmentAddon.townlessTracker().getDisplayList(), config.townlessHudMaxPlayers);
        if (players.isEmpty()) return;
        renderAt(ctx, mc.textRenderer, players, config.townlessHudX, config.townlessHudY);
    }

    static void renderAt(DrawContext ctx, TextRenderer tr, List<String> players, int x, int y) {
        int lh = tr.fontHeight + 2;
        String header = "Townless (" + players.size() + ")";
        int maxW = tr.getWidth(header);
        for (String name : players) maxW = Math.max(maxW, tr.getWidth(name));
        ctx.fill(x - 3, y - 3, x + maxW + 3, y + lh * (players.size() + 1) + 3, 0x80000000);
        ctx.drawTextWithShadow(tr, header, x, y, 0xFFAAAAAA);
        y += lh;
        for (String name : players) {
            ctx.drawTextWithShadow(tr, name, x, y, 0xFFFFFFFF);
            y += lh;
        }
    }

    static int hudWidth(TextRenderer tr, List<String> players) {
        String header = "Townless (" + players.size() + ")";
        int w = tr.getWidth(header);
        for (String name : players) w = Math.max(w, tr.getWidth(name));
        return w + 6;
    }

    static int hudHeight(TextRenderer tr, List<String> players) {
        return (tr.fontHeight + 2) * (players.size() + 1) + 6;
    }

    private static List<String> capped(List<String> list, int max) {
        if (max > 0 && list.size() > max) return list.subList(0, max);
        return list;
    }
}
