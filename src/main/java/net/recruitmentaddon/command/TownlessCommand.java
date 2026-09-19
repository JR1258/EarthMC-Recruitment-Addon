package net.recruitmentaddon.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.alert.TownlessTracker;

/** Client command {@code /townless} — toggle and configure the townless player HUD. */
public final class TownlessCommand {

    private TownlessCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
            dispatcher.register(ClientCommands.literal("townless")
                .executes(ctx -> toggle(ctx.getSource()))
                .then(ClientCommands.literal("toggle")
                    .executes(ctx -> toggle(ctx.getSource())))
                .then(ClientCommands.literal("on")
                    .executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(ClientCommands.literal("off")
                    .executes(ctx -> setEnabled(ctx.getSource(), false)))
                .then(ClientCommands.literal("minage")
                    .then(ClientCommands.argument("age", StringArgumentType.word())
                        .executes(ctx -> setMinAge(ctx.getSource(), StringArgumentType.getString(ctx, "age")))))));
    }

    private static int toggle(FabricClientCommandSource source) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.townlessHudEnabled = !c.townlessHudEnabled;
        c.save();
        feedback(source, "Townless HUD " + (c.townlessHudEnabled ? "§aenabled" : "§cdisabled") + "§7.");
        return 1;
    }

    private static int setEnabled(FabricClientCommandSource source, boolean on) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.townlessHudEnabled = on;
        c.save();
        feedback(source, "Townless HUD " + (on ? "§aenabled" : "§cdisabled") + "§7.");
        return 1;
    }

    private static int setMinAge(FabricClientCommandSource source, String age) {
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

    private static void feedback(FabricClientCommandSource source, String msg) {
        source.sendFeedback(Component.literal("§b[Recruitment] §7" + msg));
    }
}
