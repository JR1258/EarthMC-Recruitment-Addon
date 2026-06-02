package net.recruitmentaddon.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.recruitmentaddon.RecruitmentConfig;
import net.recruitmentaddon.alert.GlobalAdReminder;
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
                .then(ClientCommandManager.literal("sound")
                    .then(ClientCommandManager.literal("on").executes(ctx -> setSound(ctx.getSource(), true)))
                    .then(ClientCommandManager.literal("off").executes(ctx -> setSound(ctx.getSource(), false))))
                .then(ClientCommandManager.literal("window")
                    .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(10, 300))
                        .executes(ctx -> setWindow(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
                .then(ClientCommandManager.literal("cooldown")
                    .then(ClientCommandManager.argument("minutes", IntegerArgumentType.integer(0, 1440))
                        .executes(ctx -> setCooldown(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "minutes")))))
                .then(ClientCommandManager.literal("delay")
                    .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(0, 300))
                        .executes(ctx -> setDelay(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
                .then(ClientCommandManager.literal("message")
                    .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> setRecruitMessage(ctx.getSource(), StringArgumentType.getString(ctx, "text")))))
                .then(ClientCommandManager.literal("townphrase")
                    .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> setTownPhrase(ctx.getSource(), StringArgumentType.getString(ctx, "text")))))
                .then(ClientCommandManager.literal("ad")
                    .then(ClientCommandManager.literal("on").executes(ctx -> setGlobalAd(ctx.getSource(), true)))
                    .then(ClientCommandManager.literal("off").executes(ctx -> setGlobalAd(ctx.getSource(), false)))
                    .then(ClientCommandManager.literal("test").executes(ctx -> testGlobalAd(ctx.getSource())))
                    .then(ClientCommandManager.literal("showitem")
                        .then(ClientCommandManager.literal("on").executes(ctx -> setGlobalAdShowItem(ctx.getSource(), true)))
                        .then(ClientCommandManager.literal("off").executes(ctx -> setGlobalAdShowItem(ctx.getSource(), false))))
                    .then(ClientCommandManager.literal("interval")
                        .then(ClientCommandManager.argument("minutes", IntegerArgumentType.integer(5, 300))
                            .executes(ctx -> setGlobalAdInterval(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "minutes")))))
                    .then(ClientCommandManager.literal("message")
                        .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                            .executes(ctx -> setGlobalAdMessage(ctx.getSource(), StringArgumentType.getString(ctx, "text"))))))
                .then(ClientCommandManager.literal("test").executes(ctx -> test(ctx.getSource())))
                .then(ClientCommandManager.literal("followup")
                    .then(ClientCommandManager.literal("list").executes(ctx -> followUpList(ctx.getSource())))
                    .then(ClientCommandManager.literal("test")
                        .then(ClientCommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> followUpTest(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                    .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("title", StringArgumentType.word())
                            .executes(ctx -> followUpRemove(ctx.getSource(), StringArgumentType.getString(ctx, "title")))))
                    .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("title", StringArgumentType.word())
                            .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> followUpSet(ctx.getSource(), StringArgumentType.getString(ctx, "title"),
                                        StringArgumentType.getString(ctx, "message"), false)))))
                    .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("title", StringArgumentType.word())
                            .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> followUpSet(ctx.getSource(), StringArgumentType.getString(ctx, "title"),
                                        StringArgumentType.getString(ctx, "message"), true))))))
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
                + c.newPlayerMaxSeconds + " sec §7| cooldown: §f" + c.alertCooldownMinutes
                + " min §7| delay: §f" + c.alertDelaySeconds + " sec §7| sound: " + onOff(c.soundEnabled));
        feedback(source, "§7Recruit copy text: §f" + c.recruitMessage);
        feedback(source, "§7Global ad reminder: " + onOff(c.globalAdReminderEnabled)
                + " §7| every §f" + c.globalAdReminderMinutes + " min §7| showitem: " + onOff(c.globalAdUseShowItem));
        feedback(source, "§7Towny phrase: §f" + c.townJoinPhrase + " §7| follow-ups: §f" + c.followUpMessages.size());
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
        feedback(source, "§7Posting a sample alert (click copies text for §f" + name + "§7):");
        JoinAlerter.postRecruitMessage(name, c);
        return 1;
    }

    private static int setSound(FabricClientCommandSource source, boolean on) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.soundEnabled = on;
        c.save();
        feedback(source, "Sound " + (on ? "§aenabled" : "§cdisabled") + "§7.");
        return 1;
    }

    private static int setWindow(FabricClientCommandSource source, int seconds) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.newPlayerMaxSeconds = seconds;
        c.save();
        feedback(source, "New-player window set to §f" + seconds + " seconds§7.");
        return 1;
    }

    private static int setCooldown(FabricClientCommandSource source, int minutes) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.alertCooldownMinutes = minutes;
        c.save();
        feedback(source, "Recruit alert cooldown set to §f" + minutes + " minutes§7.");
        return 1;
    }

    private static int setDelay(FabricClientCommandSource source, int seconds) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.alertDelaySeconds = seconds;
        c.save();
        feedback(source, "Recruit alert delay set to §f" + seconds + " seconds§7.");
        return 1;
    }

    private static int setRecruitMessage(FabricClientCommandSource source, String text) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.recruitMessage = text;
        c.save();
        feedback(source, "Recruit copy text updated.");
        return 1;
    }

    private static int setTownPhrase(FabricClientCommandSource source, String text) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.townJoinPhrase = text;
        c.save();
        feedback(source, "Towny join phrase set to §f" + text + "§7.");
        return 1;
    }

    private static int setGlobalAd(FabricClientCommandSource source, boolean on) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.globalAdReminderEnabled = on;
        c.save();
        feedback(source, "Global ad reminder " + (on ? "§aenabled" : "§cdisabled") + "§7.");
        return 1;
    }

    private static int setGlobalAdInterval(FabricClientCommandSource source, int minutes) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.globalAdReminderMinutes = minutes;
        c.save();
        feedback(source, "Global ad reminder interval set to §f" + minutes + " minutes§7.");
        return 1;
    }

    private static int setGlobalAdShowItem(FabricClientCommandSource source, boolean on) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.globalAdUseShowItem = on;
        c.save();
        feedback(source, "Global ad /showitem mode " + (on ? "§aenabled" : "§cdisabled") + "§7.");
        return 1;
    }

    private static int setGlobalAdMessage(FabricClientCommandSource source, String text) {
        RecruitmentConfig c = RecruitmentAddon.config();
        c.globalAdMessage = text;
        c.save();
        feedback(source, "Global ad copy text updated.");
        return 1;
    }

    private static int testGlobalAd(FabricClientCommandSource source) {
        GlobalAdReminder.postGlobalAdPrompt(RecruitmentAddon.config());
        feedback(source, "Posted a sample global ad copy prompt.");
        return 1;
    }

    private static int followUpList(FabricClientCommandSource source) {
        RecruitmentConfig c = RecruitmentAddon.config();
        if (c.followUpMessages.isEmpty()) {
            feedback(source, "No follow-up messages configured.");
            return 1;
        }
        for (RecruitmentConfig.FollowUpMessage followUp : c.followUpMessages) {
            feedback(source, "§f" + followUp.title + "§7: " + followUp.message);
        }
        return 1;
    }

    private static int followUpTest(FabricClientCommandSource source, String player) {
        JoinAlerter.postFollowUpMessages(player, RecruitmentAddon.config());
        feedback(source, "Posted sample follow-up buttons for §f" + player + "§7.");
        return 1;
    }

    private static int followUpRemove(FabricClientCommandSource source, String title) {
        RecruitmentConfig c = RecruitmentAddon.config();
        boolean removed = c.followUpMessages.removeIf(f -> f.title != null && f.title.equalsIgnoreCase(title));
        if (!removed) {
            feedback(source, "No follow-up named §f" + title + "§7.");
            return 1;
        }
        c.save();
        feedback(source, "Removed follow-up §f" + title + "§7.");
        return 1;
    }

    private static int followUpSet(FabricClientCommandSource source, String title, String message, boolean replaceOnly) {
        RecruitmentConfig c = RecruitmentAddon.config();
        for (RecruitmentConfig.FollowUpMessage followUp : c.followUpMessages) {
            if (followUp.title != null && followUp.title.equalsIgnoreCase(title)) {
                followUp.title = title;
                followUp.message = message;
                c.save();
                feedback(source, "Updated follow-up §f" + title + "§7.");
                return 1;
            }
        }
        if (replaceOnly) {
            feedback(source, "No follow-up named §f" + title + "§7. Use §f/recruit followup add§7.");
            return 1;
        }
        c.followUpMessages.add(new RecruitmentConfig.FollowUpMessage(title, message));
        c.save();
        feedback(source, "Added follow-up §f" + title + "§7.");
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
