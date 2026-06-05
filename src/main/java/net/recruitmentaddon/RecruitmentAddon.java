package net.recruitmentaddon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.recruitmentaddon.alert.GlobalAdReminder;
import net.recruitmentaddon.alert.JoinAlerter;
import net.recruitmentaddon.alert.TownJoinDetector;
import net.recruitmentaddon.api.EarthMcData;
import net.recruitmentaddon.command.RecruitCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Watches the server player list and posts a clickable recruit message whenever a
 * <i>newly-registered</i> player joins while you're online. Nothing else — no HUD,
 * no proximity, no map polling.
 */
public class RecruitmentAddon implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("RecruitmentAddon");
    private static final int POLL_INTERVAL_TICKS = 20; // ~1 second

    private static RecruitmentConfig config;
    private static EarthMcData data;
    private static JoinAlerter joinAlerter;
    private static GlobalAdReminder globalAdReminder;
    private static final Map<String, Long> followUpPromptedAt = new HashMap<>();

    private long tickCounter = 0;

    @Override
    public void onInitializeClient() {
        LOGGER.info("EarthMC Recruitment Addon initialising...");
        config = RecruitmentConfig.load();
        data = new EarthMcData(config);
        joinAlerter = new JoinAlerter();
        globalAdReminder = new GlobalAdReminder();

        RecruitCommand.register();

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            joinAlerter.reset();
            globalAdReminder.reset();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            joinAlerter.reset();
            globalAdReminder.reset();
            followUpPromptedAt.clear();
            if (data != null) data.clear();
        });
    }

    public static RecruitmentConfig config() { return config; }
    public static EarthMcData data() { return data; }

    private void onClientTick(Minecraft client) {
        if (++tickCounter % POLL_INTERVAL_TICKS != 0) return;
        try {
            if (!isActiveOnEarthMc(client)) return;
            joinAlerter.update(data, config);
            globalAdReminder.update(config);
        } catch (Exception e) {
            LOGGER.debug("[Recruitment] tick failed: {}", e.getMessage());
        }
    }

    public static void onPlayerListAddPacket(String name) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (joinAlerter == null || data == null || config == null) return;
            if (!isActiveOnEarthMcServer(client)) return;
            String self = client.player != null ? client.player.getGameProfile().name() : null;
            if (self != null && self.equalsIgnoreCase(name)) return;
            joinAlerter.onPlayerAdded(name, data, config);
        } catch (Exception e) {
            LOGGER.debug("[Recruitment] player-list add failed: {}", e.getMessage());
        }
    }

    public static void onIncomingMessage(String message, boolean trustedSystemMessage) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (joinAlerter == null || config == null) return;
            if (!isActiveOnEarthMcServer(client)) return;
            if (!trustedSystemMessage) return;
            String player = TownJoinDetector.joinedPlayer(message, config);
            if (player == null || isExcluded(player)) return;
            String key = player.toLowerCase(Locale.ROOT);
            long now = System.currentTimeMillis();
            Long previous = followUpPromptedAt.get(key);
            if (previous != null && now - previous < 30_000L) return;
            followUpPromptedAt.put(key, now);
            JoinAlerter.postFollowUpMessages(player, config);
        } catch (Exception e) {
            LOGGER.debug("[Recruitment] message hook failed: {}", e.getMessage());
        }
    }

    private boolean isActiveOnEarthMc(Minecraft client) {
        return isActiveOnEarthMcServer(client);
    }

    private static boolean isActiveOnEarthMcServer(Minecraft client) {
        if (client.getConnection() == null) return false;
        if (!config.earthmcOnly) return true;
        ServerData server = client.getCurrentServer();
        return server != null && server.ip != null
                && server.ip.toLowerCase(Locale.ROOT).contains("earthmc.net");
    }

    public static boolean isExcluded(String name) {
        for (String excluded : config.excludedPlayers) {
            if (excluded.equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
