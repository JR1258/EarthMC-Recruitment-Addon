package net.recruitmentaddon.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;

import java.util.List;

/** Overlay that lets the player drag the townless HUD to any position. */
public class TownlessHudEditorScreen extends Screen {

    private static final List<String> PLACEHOLDER = List.of("Player1", "Player2");
    private static final int SNAP = 12;

    private int hudX, hudY;
    private boolean dragging = false;
    private int dragOffsetX, dragOffsetY;

    public TownlessHudEditorScreen() {
        super(Component.literal("Position Townless HUD"));
        RecruitmentConfig c = RecruitmentAddon.config();
        this.hudX = c.townlessHudX;
        this.hudY = c.townlessHudY;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 26, 100, 20).build());
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        Font font = Minecraft.getInstance().font;
        List<String> players = liveOrPlaceholder();
        int w = TownlessHud.hudWidth(font, players);
        int h = TownlessHud.hudHeight(font, players);

        // thin 1px outline around the HUD background — draw as 4 separate lines so
        // the HUD's own semi-transparent bg renders cleanly on top
        int bx1 = hudX - 4, by1 = hudY - 4;
        int bx2 = hudX + w - 2, by2 = hudY + h - 2;
        int outline = 0x99FFFFFF;
        ctx.fill(bx1,     by1,     bx2,     by1 + 1, outline); // top
        ctx.fill(bx1,     by2 - 1, bx2,     by2,     outline); // bottom
        ctx.fill(bx1,     by1 + 1, bx1 + 1, by2 - 1, outline); // left
        ctx.fill(bx2 - 1, by1 + 1, bx2,     by2 - 1, outline); // right

        TownlessHud.renderAt(ctx, font, players, hudX, hudY);
        ctx.centeredText(font, Component.literal("§7Drag to position · Done or Esc to save"), this.width / 2, 6, 0xFFCCCCCC);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() == 0) {
            Font font = Minecraft.getInstance().font;
            List<String> players = liveOrPlaceholder();
            int w = TownlessHud.hudWidth(font, players);
            int h = TownlessHud.hudHeight(font, players);
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
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging && event.button() == 0) {
            Font font = Minecraft.getInstance().font;
            List<String> players = liveOrPlaceholder();
            int w = TownlessHud.hudWidth(font, players);
            int h = TownlessHud.hudHeight(font, players);

            int newX = Math.max(3, Math.min(this.width - w + 3, (int) event.x() - dragOffsetX));
            int newY = Math.max(3, Math.min(this.height - h + 3, (int) event.y() - dragOffsetY));

            // snap to screen edges (bg occupies newX-3 .. newX+w-3, newY-3 .. newY+h-3)
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
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.townlessHudX = hudX;
        c.townlessHudY = hudY;
        c.save();
        Minecraft.getInstance().gui.setScreen(null);
    }

    private List<String> liveOrPlaceholder() {
        List<String> live = RecruitmentAddon.townlessTracker().getDisplayList();
        return live.isEmpty() ? PLACEHOLDER : live;
    }
}
