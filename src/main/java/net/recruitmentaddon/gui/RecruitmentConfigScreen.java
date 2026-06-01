package net.recruitmentaddon.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;

import java.util.function.IntConsumer;

public class RecruitmentConfigScreen extends Screen {

    private static final int W = 240;
    private final Screen parent;
    private TextFieldWidget messageField;
    private int messageLabelY;

    public RecruitmentConfigScreen(Screen parent) {
        super(Text.literal("EarthMC Recruitment Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        RecruitmentConfig c = RecruitmentAddon.config();
        int x = this.width / 2 - W / 2;
        int y = 40;
        int row = 24;

        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.enabled)
                .build(x, y, W, 20, Text.literal("Enabled"),
                       (b, v) -> { c.enabled = v; c.save(); }));
        y += row;

        addDrawableChild(CyclingButtonWidget.builder(RecruitmentConfigScreen::modeText, c.recruitableMode)
                .values(0, 1, 2)
                .build(x, y, W, 20, Text.literal("Recruit target"),
                       (b, v) -> { c.recruitableMode = v; c.save(); }));
        y += row;

        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.hudEnabled)
                .build(x, y, W, 20, Text.literal("HUD list"),
                       (b, v) -> { c.hudEnabled = v; c.save(); }));
        y += row;

        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.newPlayerAlertEnabled)
                .build(x, y, W, 20, Text.literal("New-player join alerts"),
                       (b, v) -> { c.newPlayerAlertEnabled = v; c.save(); }));
        y += row;

        addDrawableChild(new IntSlider(x, y, W, 20, 100, 3000, c.hudRange, "HUD range",
                v -> { c.hudRange = v; c.save(); }));
        y += row;

        addDrawableChild(new IntSlider(x, y, W, 20, 1, 30, c.newPlayerMaxDays, "New-player max age (days)",
                v -> { c.newPlayerMaxDays = v; c.save(); }));
        y += row + 6;

        messageLabelY = y;
        y += 11;
        messageField = new TextFieldWidget(this.textRenderer, x, y, W, 20, Text.literal("Recruit message"));
        messageField.setMaxLength(256);
        messageField.setText(c.recruitMessage);
        messageField.setChangedListener(s -> { c.recruitMessage = s; c.save(); });
        addDrawableChild(messageField);
        y += row + 6;

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
                .dimensions(x, y, W, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("§7Recruit message ({player} = their name):"),
                this.width / 2 - W / 2, messageLabelY, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    private static Text modeText(Integer mode) {
        return Text.literal(switch (mode) {
            case 1 -> "Townless or nationless";
            case 2 -> "Newly registered";
            default -> "Townless";
        });
    }

    private static final class IntSlider extends SliderWidget {
        private final int min, max;
        private final String label;
        private final IntConsumer apply;

        IntSlider(int x, int y, int w, int h, int min, int max, int initial, String label, IntConsumer apply) {
            super(x, y, w, h, Text.empty(), (double) (initial - min) / (max - min));
            this.min = min;
            this.max = max;
            this.label = label;
            this.apply = apply;
            updateMessage();
        }

        private int current() {
            return (int) Math.round(min + this.value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + current()));
        }

        @Override
        protected void applyValue() {
            apply.accept(current());
        }
    }
}
