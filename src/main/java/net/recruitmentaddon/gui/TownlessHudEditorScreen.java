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

import java.util.List;

/** Translucent overlay that lets the player drag the townless HUD to any position. */
public class TownlessHudEditorScreen extends Screen {

    private static final List<String> PLACEHOLDER = List.of("Player1", "Player2");

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
        List<String> players = liveOrPlaceholder();
        int w = TownlessHud.hudWidth(tr, players);
        int h = TownlessHud.hudHeight(tr, players);
        ctx.fill(hudX - 4, hudY - 4, hudX + w + 1, hudY + h + 1, 0xFF5599FF);
        TownlessHud.renderAt(ctx, tr, players, hudX, hudY);
        ctx.drawCenteredTextWithShadow(tr, "§7Drag · Done or Esc to save", this.width / 2, 6, 0xFFCCCCCC);
    }

    @Override
    public boolean mouseClicked(Click event, boolean bl) {
        if (event.button() == 0) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            List<String> players = liveOrPlaceholder();
            int w = TownlessHud.hudWidth(tr, players);
            int h = TownlessHud.hudHeight(tr, players);
            double mx = event.x(), my = event.y();
            if (mx >= hudX - 4 && mx <= hudX + w + 1 && my >= hudY - 4 && my <= hudY + h + 1) {
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
            hudX = Math.max(0, Math.min(this.width - 10, (int) event.x() - dragOffsetX));
            hudY = Math.max(0, Math.min(this.height - 10, (int) event.y() - dragOffsetY));
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

    private List<String> liveOrPlaceholder() {
        List<String> live = RecruitmentAddon.townlessTracker().getDisplayList();
        return live.isEmpty() ? PLACEHOLDER : live;
    }
}
