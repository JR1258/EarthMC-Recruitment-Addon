package net.recruitmentaddon.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.recruitmentaddon.RecruitmentAddon;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.model.LivePlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Client command {@code /recruit} — toggle, scan, configure, and manage exclusions. */
public final class RecruitCommand {

    private RecruitCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
            dispatcher.register(ClientCommandManager.literal("recruit")
                .executes(ctx -> status(ctx.getSource()))
                .then(ClientCommandManager.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(ClientCommandManager.literal("on").executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(ClientCommandManager.literal("off").executes(ctx -> setEnabled(ctx.getSource(), false)))
                .then(ClientCommandManager.literal("scan").executes(ctx -> scan(ctx.getSource())))
                .then(ClientCommandManager.literal("mode")
                    .then(ClientCommandManager.argument("mode", IntegerArgumentType.integer(0, 2))
                        .executes(ctx -> setMode(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "mode")))))
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
        feedback(source, "Alerts are " + onOff(c.enabled) + "§7. Target: §f" + modeName(c.recruitableMode)
                + " §7| HUD: " + onOff(c.hudEnabled) + " §7| Join alerts: " + onOff(c.newPlayerAlertEnabled));
        return 1;
    }

    private static int setEnabled(FabricClientCommandSource source, boolean on) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.enabled = on;
        c.save();
        feedback(source, "Recruitment alerts " + (on ? "§aenabled" : "§cdisabled") + "§7.");
        return 1;
    }

    private static int setMode(FabricClientCommandSource source, int mode) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.recruitableMode = mode;
        c.save();
        feedback(source, "Target set to §f" + modeName(mode) + "§7.");
        return 1;
    }

    private static int scan(FabricClientCommandSource source) {
        List<LivePlayer> nearby = RecruitmentAddon.recruitableNearby();
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity self = mc.player;
        if (nearby.isEmpty() || self == null) {
            feedback(source, "No recruitable players nearby right now.");
            return 1;
        }
        int px = (int) Math.round(self.getX());
        int pz = (int) Math.round(self.getZ());
        List<LivePlayer> sorted = new ArrayList<>(nearby);
        sorted.sort(Comparator.comparingDouble(p -> RecruitmentAddon.distance(px, pz, p.x(), p.z())));
        feedback(source, "§eRecruitable nearby (" + sorted.size() + "):");
        for (LivePlayer p : sorted) {
            int dist = (int) Math.round(RecruitmentAddon.distance(px, pz, p.x(), p.z()));
            feedback(source, " §7- §f" + p.name() + " §7(" + dist + "m, " + p.x() + ", " + p.z() + ")");
        }
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

    private static String modeName(int mode) {
        return switch (mode) {
            case 1 -> "townless or nationless";
            case 2 -> "newly registered";
            default -> "townless";
        };
    }

    private static void feedback(FabricClientCommandSource source, String msg) {
        source.sendFeedback(Text.literal("§b[Recruitment] §7" + msg));
    }
}
