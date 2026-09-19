package net.recruitmentaddon.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

        List<String> players = RecruitmentAddon.townlessTracker().getDisplayList();
        if (players.isEmpty()) return;

        Font font = mc.font;
        int x = 4;
        int y = 4;
        int lh = font.lineHeight + 2;
        String header = "Townless (" + players.size() + ")";
        int maxW = font.width(header);
        for (String name : players) maxW = Math.max(maxW, font.width(name));

        ctx.fill(x - 3, y - 3, x + maxW + 3, y + lh * (players.size() + 1) + 3, 0x80000000);
        ctx.text(font, header, x, y, 0xFFAAAAAA);
        y += lh;
        for (String name : players) {
            ctx.text(font, name, x, y, 0xFFFFFFFF);
            y += lh;
        }
    }
}
