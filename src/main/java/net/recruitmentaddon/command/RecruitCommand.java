package net.recruitmentaddon.command;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.gui.RecruitmentConfigScreen;

/** Client command {@code /recruit} — enable, disable, and open settings. */
public final class RecruitCommand {

    private RecruitCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
            dispatcher.register(ClientCommandManager.literal("recruit")
                .executes(ctx -> openSettings(ctx.getSource()))
                .then(ClientCommandManager.literal("on")
                    .executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(ClientCommandManager.literal("off")
                    .executes(ctx -> setEnabled(ctx.getSource(), false)))
                .then(ClientCommandManager.literal("settings")
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
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> mc.setScreen(new RecruitmentConfigScreen(mc.currentScreen)));
        return 1;
    }

    private static void feedback(FabricClientCommandSource source, String msg) {
        source.sendFeedback(Text.literal("§b[Recruitment] §7" + msg));
    }
}
