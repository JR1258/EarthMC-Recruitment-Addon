package net.recruitmentaddon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.recruitmentaddon.alert.JoinAlerter;
import net.recruitmentaddon.api.EarthMcData;
import net.recruitmentaddon.command.RecruitCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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

    private long tickCounter = 0;
    private final JoinAlerter joinAlerter = new JoinAlerter();

    @Override
    public void onInitializeClient() {
        LOGGER.info("EarthMC Recruitment Addon initialising...");
        config = RecruitmentConfig.load();
        data = new EarthMcData(config);

        RecruitCommand.register();

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> joinAlerter.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            joinAlerter.reset();
            if (data != null) data.clear();
        });
    }

    public static RecruitmentConfig config() { return config; }
    public static EarthMcData data() { return data; }

    private void onClientTick(MinecraftClient client) {
        if (++tickCounter % POLL_INTERVAL_TICKS != 0) return;
        try {
            if (!isActiveOnEarthMc(client)) return;
            joinAlerter.update(currentPlayerNames(client), data, config);
        } catch (Exception e) {
            LOGGER.debug("[Recruitment] tick failed: {}", e.getMessage());
        }
    }

    /** Names currently in the client's player list (i.e. everyone connected to this server). */
    private static Set<String> currentPlayerNames(MinecraftClient client) {
        Set<String> names = new HashSet<>();
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return names;
        String self = client.player != null ? client.player.getGameProfile().name() : null;
        for (PlayerListEntry entry : handler.getPlayerList()) {
            String name = entry.getProfile() != null ? entry.getProfile().name() : null;
            if (name == null || name.isBlank()) continue;
            if (self != null && name.equalsIgnoreCase(self)) continue;
            names.add(name);
        }
        return names;
    }

    public static boolean isExcluded(String name) {
        for (String excluded : config.excludedPlayers) {
            if (excluded.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private boolean isActiveOnEarthMc(MinecraftClient client) {
        if (client.getNetworkHandler() == null) return false;
        if (!config.earthmcOnly) return true;
        ServerInfo server = client.getCurrentServerEntry();
        return server != null && server.address != null
                && server.address.toLowerCase(Locale.ROOT).contains("earthmc.net");
    }
}
