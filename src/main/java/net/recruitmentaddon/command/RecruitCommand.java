package net.recruitmentaddon.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.alert.JoinAlerter;
import net.recruitmentaddon.RecruitmentAddon;

/** Client command {@code /recruit} — toggle, test, and manage exclusions. */
public final class RecruitCommand {

    private RecruitCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
            dispatcher.register(ClientCommandManager.literal("recruit")
                .executes(ctx -> status(ctx.getSource()))
                .then(ClientCommandManager.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(ClientCommandManager.literal("on").executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(ClientCommandManager.literal("off").executes(ctx -> setEnabled(ctx.getSource(), false)))
                .then(ClientCommandManager.literal("test").executes(ctx -> test(ctx.getSource())))
                .then(ClientCommandManager.literal("exclude")
                    .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> exclude(ctx.getSource(), StringArgumentType.getString(ctx, "player"), true))))
                    .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> exclude(ctx.getSource(), StringArgumentType.getString(ctx, "player"), false))))
                    .then(ClientCommandManager.literal("list").executes(ctx -> excludeList(ctx.getSource()))))));
    }

    private static int status(FabricClientCommandSource source) {
        RecruitmentConfig c = RecruitmentAddon.config();
        feedback(source, "Join alerts are " + onOff(c.enabled) + " §7| new-player window: §f"
                + c.newPlayerMaxDays + " day" + (c.newPlayerMaxDays == 1 ? "" : "s"));
        feedback(source, "§7Recruit message: §f" + c.recruitMessage);
        return 1;
    }

    private static int setEnabled(FabricClientCommandSource source, boolean on) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.enabled = on;
        c.save();
        feedback(source, "Join alerts " + (on ? "§aenabled" : "§cdisabled") + "§7.");
        return 1;
    }

    private static int test(FabricClientCommandSource source) {
        RecruitmentConfig c = RecruitmentAddon.config();
        MinecraftClient mc = MinecraftClient.getInstance();
        String name = mc.player != null ? mc.player.getGameProfile().name() : "NewPlayer";
        feedback(source, "§7Posting a sample alert (clicking it will message §f" + name + "§7):");
        JoinAlerter.postRecruitMessage(name, c);
        return 1;
    }

    private static int exclude(FabricClientCommandSource source, String player, boolean add) {
        RecruitmentConfig c = RecruitmentAddon.config();
        boolean present = c.excludedPlayers.stream().anyMatch(n -> n.equalsIgnoreCase(player));
        if (add) {
            if (present) { feedback(source, player + " is already excluded."); return 1; }
            c.excludedPlayers.add(player);
            c.save();
            feedback(source, "§a" + player + " is now excluded.");
        } else {
            if (!present) { feedback(source, player + " is not excluded."); return 1; }
            c.excludedPlayers.removeIf(n -> n.equalsIgnoreCase(player));
            c.save();
            feedback(source, "§a" + player + " is no longer excluded.");
        }
        return 1;
    }

    private static int excludeList(FabricClientCommandSource source) {
        RecruitmentConfig c = RecruitmentAddon.config();
        if (c.excludedPlayers.isEmpty()) feedback(source, "No excluded players.");
        else feedback(source, "Excluded: §f" + String.join(", ", c.excludedPlayers));
        return 1;
    }

    private static String onOff(boolean v) {
        return v ? "§aon" : "§coff";
    }

    private static void feedback(FabricClientCommandSource source, String msg) {
        source.sendFeedback(Text.literal("§b[Recruitment] §7" + msg));
    }
}
