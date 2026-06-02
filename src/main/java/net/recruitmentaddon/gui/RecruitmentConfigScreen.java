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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

public class RecruitmentConfigScreen extends Screen {

    private static final int W = 390;
    private static final int FIELD_H = 20;
    private static final int LABEL_GAP = 12;
    private static final int ROW_GAP = 48;

    private final Screen parent;
    private final List<Label> labels = new ArrayList<>();
    private Page page = Page.GENERAL;

    public RecruitmentConfigScreen(Screen parent) {
        super(Text.literal("EarthMC Recruitment Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        labels.clear();
        RecruitmentConfig c = RecruitmentAddon.config();
        int x = this.width / 2 - W / 2;
        int y = 34;

        int tabW = W / Page.values().length;
        for (Page p : Page.values()) {
            addDrawableChild(ButtonWidget.builder(Text.literal((p == page ? "§l" : "") + p.title), b -> {
                page = p;
                init();
            }).dimensions(x + p.ordinal() * tabW, y, tabW - 2, FIELD_H).build());
        }

        y = 64;
        switch (page) {
            case GENERAL -> initGeneral(c, x, y);
            case ADS -> initAds(c, x, y);
            case MESSAGES -> initMessages(c, x, y);
            case FOLLOW_UPS -> initFollowUps(c, x, y);
            case ADVANCED -> initAdvanced(c, x, y);
        }

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
                .dimensions(x, this.height - 28, W, FIELD_H).build());
    }

    private void initGeneral(RecruitmentConfig c, int x, int y) {
        addSection("Detection", x, y);
        y += 14;
        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.enabled)
                .build(x, y, W, FIELD_H, Text.literal("Join alerts"),
                        (b, v) -> { c.enabled = v; c.save(); }));
        y += 24;
        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.soundEnabled)
                .build(x, y, W, FIELD_H, Text.literal("Sound"),
                        (b, v) -> { c.soundEnabled = v; c.save(); }));
        y += 24;
        addDrawableChild(new IntSlider(x, y, W, FIELD_H, 10, 300, c.newPlayerMaxSeconds,
                "New-player window (seconds)", v -> { c.newPlayerMaxSeconds = v; c.save(); }));
        y += 24;
        addDrawableChild(new IntSlider(x, y, W, FIELD_H, 0, 300, c.alertDelaySeconds,
                "Recruit prompt delay (seconds)", v -> { c.alertDelaySeconds = v; c.save(); }));
        y += 24;
        addDrawableChild(new IntSlider(x, y, W, FIELD_H, 0, 120, c.alertCooldownMinutes,
                "Duplicate cooldown (minutes)", v -> { c.alertCooldownMinutes = v; c.save(); }));
    }

    private void initAds(RecruitmentConfig c, int x, int y) {
        addSection("Global Chat Ad Reminder", x, y);
        y += 14;
        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.globalAdReminderEnabled)
                .build(x, y, W, FIELD_H, Text.literal("Global ad reminder"),
                        (b, v) -> { c.globalAdReminderEnabled = v; c.save(); }));
        y += 24;
        addDrawableChild(new IntSlider(x, y, W, FIELD_H, 5, 300, c.globalAdReminderMinutes,
                "Ad reminder interval (minutes)", v -> { c.globalAdReminderMinutes = v; c.save(); }));
        y += 24;
        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.globalAdUseShowItem)
                .build(x, y, W, FIELD_H, Text.literal("Use /showitem"),
                        (b, v) -> { c.globalAdUseShowItem = v; c.save(); }));
        y += 32;
        addTextField("Global ad copy message", c.globalAdMessage, x, y, W, 512, s -> {
            c.globalAdMessage = s;
            c.save();
        });
    }

    private void initMessages(RecruitmentConfig c, int x, int y) {
        addSection("Copy Text", x, y);
        y += 14;
        addTextField("Recruit copy message ({player} = name)", c.recruitMessage, x, y, W, 512, s -> {
            c.recruitMessage = s;
            c.save();
        });
        y += ROW_GAP;
        addTextField("Towny trigger phrase found in chat", c.townJoinPhrase, x, y, W, 160, s -> {
            c.townJoinPhrase = s;
            c.save();
        });
    }

    private void initFollowUps(RecruitmentConfig c, int x, int y) {
        addSection("Town Join Follow-Ups", x, y);
        y += 14;
        if (c.followUpMessages.isEmpty()) {
            addLabel("No follow-up messages configured.", x, y + 6);
            y += 28;
        }

        int index = 0;
        for (RecruitmentConfig.FollowUpMessage followUp : c.followUpMessages) {
            int itemY = y + index * 66;
            if (itemY > this.height - 104) break;
            final RecruitmentConfig.FollowUpMessage item = followUp;
            final int removeIndex = index;
            addTextField("Button title", safe(item.title), x, itemY, W - 88, 80, s -> {
                item.title = s;
                c.save();
            });
            addDrawableChild(ButtonWidget.builder(Text.literal("Remove"), b -> {
                if (removeIndex < c.followUpMessages.size()) {
                    c.followUpMessages.remove(removeIndex);
                    c.save();
                    init();
                }
            }).dimensions(x + W - 78, itemY + LABEL_GAP, 78, FIELD_H).build());
            addTextField("Message copied by this button", safe(item.message), x, itemY + 34, W, 512, s -> {
                item.message = s;
                c.save();
            });
            index++;
        }

        int addY = Math.min(this.height - 56, y + index * 66 + 4);
        addDrawableChild(ButtonWidget.builder(Text.literal("Add Follow-Up"), b -> {
            c.followUpMessages.add(new RecruitmentConfig.FollowUpMessage("New", "Welcome, {player}!"));
            c.save();
            init();
        }).dimensions(x, addY, W, FIELD_H).build());
    }

    private void initAdvanced(RecruitmentConfig c, int x, int y) {
        addSection("Scope", x, y);
        y += 14;
        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.earthmcOnly)
                .build(x, y, W, FIELD_H, Text.literal("Only on earthmc.net"),
                        (b, v) -> { c.earthmcOnly = v; c.save(); }));
        y += 32;
        addTextField("EarthMC API base URL", c.earthmcApiBaseUrl, x, y, W, 256, s -> {
            c.earthmcApiBaseUrl = s;
            c.save();
        });
        y += ROW_GAP;
        addTextField("Excluded players (comma-separated)", String.join(", ", c.excludedPlayers), x, y, W, 512, s -> {
            c.excludedPlayers = parseCsv(s);
            c.save();
        });
    }

    private void addTextField(String label, String value, int x, int y, int width, int maxLength, TextConsumer changed) {
        addLabel(label, x, y);
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, y + LABEL_GAP, width, FIELD_H, Text.literal(label));
        field.setMaxLength(maxLength);
        field.setText(value == null ? "" : value);
        field.setChangedListener(changed::accept);
        addDrawableChild(field);
    }

    private void addSection(String text, int x, int y) {
        labels.add(new Label("§b" + text, x, y));
    }

    private void addLabel(String text, int x, int y) {
        labels.add(new Label("§7" + text, x, y));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, 0xFFFFFFFF);
        for (Label label : labels) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(label.text()), label.x(), label.y(), 0xFFFFFFFF);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    private static List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private enum Page {
        GENERAL("General"),
        ADS("Ads"),
        MESSAGES("Messages"),
        FOLLOW_UPS("Follow"),
        ADVANCED("Advanced");

        private final String title;

        Page(String title) {
            this.title = title;
        }
    }

    private record Label(String text, int x, int y) {}

    @FunctionalInterface
    private interface TextConsumer {
        void accept(String value);
    }

    private static final class IntSlider extends SliderWidget {
        private final int min, max;
        private final String label;
        private final IntConsumer apply;

        IntSlider(int x, int y, int w, int h, int min, int max, int initial, String label, IntConsumer apply) {
            super(x, y, w, h, Text.empty(), clampValue(min, max, initial));
            this.min = min;
            this.max = max;
            this.label = label;
            this.apply = apply;
            updateMessage();
        }

        private static double clampValue(int min, int max, int initial) {
            int clamped = Math.max(min, Math.min(max, initial));
            return (double) (clamped - min) / (max - min);
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
