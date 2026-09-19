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

        List<String> players = RecruitmentAddon.townlessTracker().getDisplayList();
        if (players.isEmpty()) return;

        TextRenderer tr = mc.textRenderer;
        int x = 4;
        int y = 4;
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
}
