package net.recruitmentaddon.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;

import java.util.List;

public final class TownlessHud {

    private TownlessHud() {}

    public static void render(GuiGraphicsExtractor ctx) {
        RecruitmentConfig config = RecruitmentAddon.config();
        if (config == null || !config.townlessHudEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        List<String> players = capped(RecruitmentAddon.townlessTracker().getDisplayList(), config.townlessHudMaxPlayers);
        if (players.isEmpty()) return;
        renderAt(ctx, mc.font, players, config.townlessHudX, config.townlessHudY);
    }

    static void renderAt(GuiGraphicsExtractor ctx, Font font, List<String> players, int x, int y) {
        int lh = font.lineHeight + 2;
        String header = "Townless (" + players.size() + ")";
        int maxW = font.width(header);
        for (String name : players) maxW = Math.max(maxW, font.width(name));
        ctx.fill(x - 3, y - 3, x + maxW + 3, y + lh * (players.size() + 1) + 3, 0x80000000);
        ctx.text(font, Component.literal(header), x, y, 0xFFAAAAAA);
        y += lh;
        for (String name : players) {
            ctx.text(font, Component.literal(name), x, y, 0xFFFFFFFF);
            y += lh;
        }
    }

    static int hudWidth(Font font, List<String> players) {
        String header = "Townless (" + players.size() + ")";
        int w = font.width(header);
        for (String name : players) w = Math.max(w, font.width(name));
        return w + 6;
    }

    static int hudHeight(Font font, List<String> players) {
        return (font.lineHeight + 2) * (players.size() + 1) + 6;
    }

    private static List<String> capped(List<String> list, int max) {
        if (max > 0 && list.size() > max) return list.subList(0, max);
        return list;
    }
}
