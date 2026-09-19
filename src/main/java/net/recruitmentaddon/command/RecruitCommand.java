package net.recruitmentaddon.command;

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
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
            dispatcher.register(ClientCommands.literal("recruit")
                .requires(FabricClientCommandSource::attended)
                .executes(ctx -> openSettings(ctx.getSource()))
                .then(ClientCommands.literal("on")
                    .executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(ClientCommands.literal("off")
                    .executes(ctx -> setEnabled(ctx.getSource(), false)))
                .then(ClientCommands.literal("settings")
                    .executes(ctx -> openSettings(ctx.getSource())))
                .then(ClientCommands.literal("townless")
                    .executes(ctx -> townlessToggle(ctx.getSource()))
                    .then(ClientCommands.literal("on")
                        .executes(ctx -> townlessSetEnabled(ctx.getSource(), true)))
                    .then(ClientCommands.literal("off")
                        .executes(ctx -> townlessSetEnabled(ctx.getSource(), false)))
                    .then(ClientCommands.literal("move")
                        .executes(ctx -> townlessOpenEditor(ctx.getSource())))
                    .then(ClientCommands.literal("minage")
                        .then(ClientCommands.argument("age", StringArgumentType.word())
                            .executes(ctx -> townlessSetMinAge(ctx.getSource(), StringArgumentType.getString(ctx, "age"))))))));
    }

    private static int setEnabled(FabricClientCommandSource source, boolean on) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.enabled = on;
        c.save();
        feedback(source, "Join alerts " + (on ? "§aenabled" : "§cdisabled") + "§7.");
        return 1;
    }

    private static int openSettings(FabricClientCommandSource source) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.setScreen(new RecruitmentConfigScreen(mc.gui.screen())));
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

    private static int townlessSetMinAge(FabricClientCommandSource source, String age) {
        if (TownlessTracker.parseAgeMs(age) <= 0) {
            feedback(source, "§cInvalid format. Use e.g. §f1d§c, §f12h§c, §f30m§c, §f90s§c.");
            return 0;
        }
        RecruitmentConfig c = RecruitmentAddon.config();
        c.townlessMinAge = age;
        c.save();
        feedback(source, "Townless min account age set to §f" + age + "§7.");
        return 1;
    }

    private static int townlessOpenEditor(FabricClientCommandSource source) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.setScreen(new TownlessHudEditorScreen()));
        return 1;
    }

    private static void feedback(FabricClientCommandSource source, String msg) {
        source.sendFeedback(Component.literal("§b[Recruitment] §7" + msg));
    }
}
