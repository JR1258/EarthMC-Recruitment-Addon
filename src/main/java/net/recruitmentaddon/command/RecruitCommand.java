package net.recruitmentaddon.command;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.gui.RecruitmentConfigScreen;

/** Client command {@code /recruit} — enable, disable, and open settings. */
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
                    .executes(ctx -> openSettings(ctx.getSource())))));
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

    private static void feedback(FabricClientCommandSource source, String msg) {
        source.sendFeedback(Component.literal("§b[Recruitment] §7" + msg));
    }
}
