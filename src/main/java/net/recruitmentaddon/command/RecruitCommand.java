package net.recruitmentaddon.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.alert.TownlessTracker;
import net.recruitmentaddon.gui.RecruitmentConfigScreen;
import net.recruitmentaddon.gui.TownlessHudEditorScreen;

/** Client command {@code /recruit} — enable, disable, open settings, and configure the townless HUD. */
public final class RecruitCommand {

    private RecruitCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            dispatcher.register(ClientCommands.literal("recruit")
                .executes(ctx -> openSettings(ctx.getSource()))
                .then(ClientCommands.literal("on")
                    .executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(ClientCommands.literal("off")
                    .executes(ctx -> setEnabled(ctx.getSource(), false)))
                .then(ClientCommands.literal("settings")
                    .executes(ctx -> openSettings(ctx.getSource())))
                .then(ClientCommands.literal("apikey")
                    .executes(ctx -> apiKeyStatus(ctx.getSource()))
                    .then(ClientCommands.literal("clear")
                        .executes(ctx -> setApiKey(ctx.getSource(), "")))
                    .then(ClientCommands.argument("key", StringArgumentType.greedyString())
                        .executes(ctx -> setApiKey(ctx.getSource(), StringArgumentType.getString(ctx, "key")))))
                .then(ClientCommands.literal("townless")
                    .executes(ctx -> townlessToggle(ctx.getSource()))
                    .then(ClientCommands.literal("on")
                        .executes(ctx -> townlessSetEnabled(ctx.getSource(), true)))
                    .then(ClientCommands.literal("off")
                        .executes(ctx -> townlessSetEnabled(ctx.getSource(), false)))
                    .then(ClientCommands.literal("move")
                        .executes(ctx -> townlessOpenEditor(ctx.getSource())))
                    .then(ClientCommands.literal("maxage")
                        .then(ClientCommands.argument("days", IntegerArgumentType.integer(0, 30))
                            .executes(ctx -> townlessSetMaxAge(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "days")))))));
            dispatcher.register(ClientCommands.literal("rnext")
                .executes(ctx -> recruitNext(ctx.getSource())));
        });
    }

    private static int setEnabled(FabricClientCommandSource source, boolean on) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.enabled = on;
        c.save();
        feedback(source, "Join alerts " + (on ? "§aenabled" : "§cdisabled") + "§7.");
        return 1;
    }

    private static int apiKeyStatus(FabricClientCommandSource source) {
        String key = RecruitmentAddon.config().apiKey;
        if (key == null || key.isBlank()) {
            feedback(source, "No EarthMC API key set. Create one with §f/api key create§7, then §f/recruit apikey <key>§7.");
        } else {
            feedback(source, "EarthMC API key set (" + maskKey(key) + "). §f/recruit apikey clear§7 removes it.");
        }
        return 1;
    }

    private static int setApiKey(FabricClientCommandSource source, String key) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.apiKey = key == null ? "" : key.trim();
        c.save();
        feedback(source, c.apiKey.isEmpty() ? "EarthMC API key cleared." : "EarthMC API key saved (" + maskKey(c.apiKey) + ").");
        return 1;
    }

    /** Shows only the last 4 characters so the key never appears in full in chat or screenshots. */
    private static String maskKey(String key) {
        return key.length() <= 4 ? "****" : "…" + key.substring(key.length() - 4);
    }

    private static int openSettings(FabricClientCommandSource source) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new RecruitmentConfigScreen(mc.screen)));
        return 1;
    }

    private static int townlessToggle(FabricClientCommandSource source) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.townlessHudEnabled = !c.townlessHudEnabled;
        c.save();
        feedback(source, "Townless HUD " + (c.townlessHudEnabled ? "§aenabled" : "§cdisabled") + "§7.");
        return 1;
    }

    private static int townlessSetEnabled(FabricClientCommandSource source, boolean on) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.townlessHudEnabled = on;
        c.save();
        feedback(source, "Townless HUD " + (on ? "§aenabled" : "§cdisabled") + "§7.");
        return 1;
    }

    private static int townlessSetMaxAge(FabricClientCommandSource source, int days) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.townlessMaxAgeDays = days;
        c.save();
        feedback(source, "Townless account age limit set to §f" + (days == 0 ? "off" : days + "d") + "§7.");
        return 1;
    }

    private static int townlessOpenEditor(FabricClientCommandSource source) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new TownlessHudEditorScreen()));
        return 1;
    }

    private static int recruitNext(FabricClientCommandSource source) {
        net.recruitmentaddon.alert.TownlessTracker tracker = RecruitmentAddon.townlessTracker();
        if (tracker == null) { feedback(source, "§cTownless tracker not available."); return 0; }
        java.util.List<net.recruitmentaddon.alert.TownlessTracker.Entry> list = tracker.getDisplayList();
        if (list.isEmpty()) { feedback(source, "No players in the townless HUD."); return 0; }
        net.recruitmentaddon.alert.TownlessTracker.Entry entry = list.get(0);
        String name = entry.displayName();
        RecruitmentConfig c = RecruitmentAddon.config();
        String message = c.recruitMessage
                .replace("{player}", name)
                .replace("{town}", RecruitmentAddon.selfTown());
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(message, false)));
        return 1;
    }

    private static void feedback(FabricClientCommandSource source, String msg) {
        source.sendFeedback(Component.literal("§b[Recruitment] §7" + msg));
    }
}
