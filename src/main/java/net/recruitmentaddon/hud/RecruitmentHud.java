package net.recruitmentaddon.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.model.LivePlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Small top-left panel listing nearby recruitable players (name + distance + direction). */
public final class RecruitmentHud {

    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final int BG = 0xC0101418;
    private static final int BORDER = 0xFF3BAA6B;
    private static final int TITLE = 0xFFFFE066;
    private static final int NAME = 0xFFFFFFFF;
    private static final int DIST = 0xFF9CC7FF;

    private RecruitmentHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tick) -> {
            try {
                render(ctx);
            } catch (Exception ignored) {
            }
        });
    }

    private record Entry(String name, int distance, String direction) {}

    private static void render(DrawContext ctx) {
        RecruitmentConfig config = RecruitmentAddon.config();
        if (config == null || !config.enabled || !config.hudEnabled) return;
        List<LivePlayer> nearby = RecruitmentAddon.recruitableNearby();
        if (nearby.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity self = mc.player;
        if (self == null) return;
        TextRenderer tr = mc.textRenderer;
        int px = (int) Math.round(self.getX());
        int pz = (int) Math.round(self.getZ());

        ArrayList<Entry> entries = new ArrayList<>();
        for (LivePlayer p : nearby) {
            int dist = (int) Math.round(RecruitmentAddon.distance(px, pz, p.x(), p.z()));
            entries.add(new Entry(p.name(), dist, direction(p.x() - px, p.z() - pz)));
        }
        entries.sort(Comparator.comparingInt(Entry::distance));
        int shown = Math.min(entries.size(), Math.max(1, config.hudMaxEntries));

        String title = "Recruitable nearby (" + entries.size() + ")";
        int width = tr.getWidth(title);
        for (int i = 0; i < shown; i++) {
            Entry e = entries.get(i);
            width = Math.max(width, tr.getWidth("- " + e.name() + "  " + e.distance() + "m " + e.direction()));
        }

        int x = 4, y = 4, pad = 4, lh = tr.fontHeight + 2;
        int boxH = pad * 2 + lh * (shown + 1);
        int boxW = width + pad * 2;
        ctx.fill(x, y, x + boxW, y + boxH, BG);
        ctx.fill(x, y, x + boxW, y + 1, BORDER);

        int ty = y + pad;
        ctx.drawText(tr, title, x + pad, ty, TITLE, true);
        ty += lh;
        for (int i = 0; i < shown; i++) {
            Entry e = entries.get(i);
            int tx = x + pad;
            ctx.drawText(tr, "- " + e.name(), tx, ty, NAME, true);
            String meta = e.distance() + "m " + e.direction();
            ctx.drawText(tr, meta, x + boxW - pad - tr.getWidth(meta), ty, DIST, true);
            ty += lh;
        }
    }

    /** 8-way compass direction from a world-space delta (Minecraft: -Z = north, +X = east). */
    private static String direction(int dx, int dz) {
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        if (ang < 0) ang += 360.0;
        return DIRS[(int) Math.round(ang / 45.0) % 8];
    }
}
