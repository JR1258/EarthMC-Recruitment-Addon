package net.recruitmentaddon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.world.World;
import net.recruitmentaddon.alert.JoinAlerter;
import net.recruitmentaddon.api.EarthMcData;
import net.recruitmentaddon.command.RecruitCommand;
import net.recruitmentaddon.hud.RecruitmentHud;
import net.recruitmentaddon.model.LivePlayer;
import net.recruitmentaddon.model.PlayerProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecruitmentAddon implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("RecruitmentAddon");
    private static final int POLL_INTERVAL_TICKS = 100; // 5 seconds

    private static RecruitmentConfig config;
    private static EarthMcData data;
    /** Recruitable players currently within range — consumed by the HUD (added next). */
    private static volatile List<LivePlayer> recruitableNearby = List.of();

    private long tickCounter = 0;
    private final JoinAlerter joinAlerter = new JoinAlerter();

    @Override
    public void onInitializeClient() {
        LOGGER.info("EarthMC Recruitment Addon initialising...");
        config = RecruitmentConfig.load();
        data = new EarthMcData(config);

        RecruitmentHud.register();
        RecruitCommand.register();

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            recruitableNearby = List.of();
            joinAlerter.reset();
            if (data != null) data.clear();
        });
    }

    public static RecruitmentConfig config() { return config; }
    public static EarthMcData data() { return data; }
    public static List<LivePlayer> recruitableNearby() { return recruitableNearby; }

    private void onClientTick(MinecraftClient client) {
        if (++tickCounter % POLL_INTERVAL_TICKS != 0) return;
        try {
            if (!isActiveOnEarthMc(client)) {
                recruitableNearby = List.of();
                return;
            }
            data.pollOnline();
            updateNearby(client.player);
            joinAlerter.update(data.onlinePlayers(), data, config);
        } catch (Exception e) {
            LOGGER.debug("[Recruitment] tick failed: {}", e.getMessage());
        }
    }

    /** Filters online players to nearby recruitable ones and requests their profiles. */
    private void updateNearby(ClientPlayerEntity self) {
        if (self == null) {
            recruitableNearby = List.of();
            return;
        }
        int px = (int) Math.round(self.getX());
        int pz = (int) Math.round(self.getZ());
        String selfName = self.getName().getString();
        int range = config.hudRange;

        // 1) collect online players within range (excluding self / excluded)
        List<LivePlayer> inRange = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (LivePlayer p : data.onlinePlayers()) {
            if (p.name().equalsIgnoreCase(selfName)) continue;
            if (isExcluded(p.name())) continue;
            if (range > 0 && distance(px, pz, p.x(), p.z()) > range) continue;
            inRange.add(p);
            names.add(p.name());
        }

        // 2) make sure we have profiles for them (batched, cached)
        if (!names.isEmpty()) data.requestProfiles(names);

        // 3) keep only the recruitable ones we already have data for
        List<LivePlayer> recruitable = new ArrayList<>();
        for (LivePlayer p : inRange) {
            PlayerProfile profile = data.profile(p.name());
            if (profile != null && isRecruitable(profile)) recruitable.add(p);
        }
        recruitableNearby = List.copyOf(recruitable);
    }

    public static boolean isRecruitable(PlayerProfile profile) {
        return switch (config.recruitableMode) {
            case 1 -> profile.townless() || profile.nationless();
            case 2 -> profile.newlyRegistered(config.newPlayerMaxDays);
            default -> profile.townless();
        };
    }

    public static boolean isExcluded(String name) {
        for (String excluded : config.excludedPlayers) {
            if (excluded.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public static double distance(int x1, int z1, int x2, int z2) {
        double dx = x1 - x2, dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean isActiveOnEarthMc(MinecraftClient client) {
        if (client.player == null || client.world == null) return false;
        if (client.world.getRegistryKey() != World.OVERWORLD) return false;
        if (!config.earthmcOnly) return true;
        ServerInfo server = client.getCurrentServerEntry();
        return server != null && server.address != null
                && server.address.toLowerCase(Locale.ROOT).contains("earthmc.net");
    }
}
