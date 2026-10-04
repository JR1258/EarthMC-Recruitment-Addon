package net.recruitmentaddon.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.alert.TownlessTracker;

import java.util.List;

/** Overlay that lets the player drag the townless HUD to any position. */
public class TownlessHudEditorScreen extends Screen {

    private static List<TownlessTracker.Entry> placeholders() {
        long now = System.currentTimeMillis();
        return java.util.List.of(
            new TownlessTracker.Entry("Player1", now - 7_200_000L),
            new TownlessTracker.Entry("Player2", now - 172_800_000L)
        );
    }
    private static final int SNAP = 12;

    private int hudX, hudY;
    private boolean dragging = false;
    private int dragOffsetX, dragOffsetY;

    public TownlessHudEditorScreen() {
        super(Text.literal("Position Townless HUD"));
        RecruitmentConfig c = RecruitmentAddon.config();
        this.hudX = c.townlessHudX;
        this.hudY = c.townlessHudY;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
                .dimensions(this.width / 2 - 50, this.height - 26, 100, 20).build());
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        List<TownlessTracker.Entry> players = liveOrPlaceholder();
        int w = TownlessHud.hudWidth(tr, players);
        int h = TownlessHud.hudHeight(tr, players);

        int bx1 = hudX - 4, by1 = hudY - 4;
        int bx2 = hudX + w - 2, by2 = hudY + h - 2;
        int outline = 0x99FFFFFF;
        ctx.fill(bx1,     by1,     bx2,     by1 + 1, outline);
        ctx.fill(bx1,     by2 - 1, bx2,     by2,     outline);
        ctx.fill(bx1,     by1 + 1, bx1 + 1, by2 - 1, outline);
        ctx.fill(bx2 - 1, by1 + 1, bx2,     by2 - 1, outline);

        TownlessHud.renderAt(ctx, tr, players, hudX, hudY);
        ctx.drawCenteredTextWithShadow(tr, "§7Drag to position · Done or Esc to save", this.width / 2, 6, 0xFFCCCCCC);
    }

    @Override
    public boolean mouseClicked(Click event, boolean bl) {
        if (event.button() == 0) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            List<TownlessTracker.Entry> players = liveOrPlaceholder();
            int w = TownlessHud.hudWidth(tr, players);
            int h = TownlessHud.hudHeight(tr, players);
            double mx = event.x(), my = event.y();
            if (mx >= hudX - 4 && mx <= hudX + w - 2 && my >= hudY - 4 && my <= hudY + h - 2) {
                dragging = true;
                dragOffsetX = (int) mx - hudX;
                dragOffsetY = (int) my - hudY;
                return true;
            }
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseDragged(Click event, double dx, double dy) {
        if (dragging && event.button() == 0) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            List<TownlessTracker.Entry> players = liveOrPlaceholder();
            int w = TownlessHud.hudWidth(tr, players);
            int h = TownlessHud.hudHeight(tr, players);

            int newX = Math.max(3, Math.min(this.width - w + 3, (int) event.x() - dragOffsetX));
            int newY = Math.max(3, Math.min(this.height - h + 3, (int) event.y() - dragOffsetY));

            if (newX - 3 <= SNAP)                    newX = 3;
            else if (newX + w - 3 >= this.width - SNAP)  newX = this.width - w + 3;
            if (newY - 3 <= SNAP)                    newY = 3;
            else if (newY + h - 3 >= this.height - SNAP) newY = this.height - h + 3;

            hudX = newX;
            hudY = newY;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click event) {
        if (event.button() == 0) dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public void close() {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.townlessHudX = hudX;
        c.townlessHudY = hudY;
        c.save();
        this.client.setScreen(null);
    }

    private List<TownlessTracker.Entry> liveOrPlaceholder() {
        List<TownlessTracker.Entry> live = RecruitmentAddon.townlessTracker().getDisplayList();
        return live.isEmpty() ? placeholders() : live;
    }
}
