package net.recruitmentaddon.api;

import net.fabricmc.loader.api.FabricLoader;
import net.recruitmentaddon.model.PlayerProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Optional bridge into EarthMC Map Addon's player-detail cache. This keeps the
 * recruitment addon standalone, while reusing data Towny Map already loaded.
 * It intentionally does not ask Towny Map to perform new lookups, so recruiting
 * checks cannot compete with town popup requests in the map addon.
 */
final class TownyMapProfileBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("RecruitmentAddon");
    private static final String TOWNY_MAP_MOD_ID = "townymapaddon";
    private static final TownyMapProfileBridge DISABLED = new TownyMapProfileBridge(null);

    private final Method profileMethod;

    private TownyMapProfileBridge(Method profileMethod) {
        this.profileMethod = profileMethod;
    }

    static TownyMapProfileBridge create() {
        if (!FabricLoader.getInstance().isModLoaded(TOWNY_MAP_MOD_ID)) return DISABLED;
        try {
            Class<?> modClass = Class.forName("net.townymap.TownyMapMod");
            Method profile = modClass.getMethod("recruitmentPlayerProfile", String.class);
            LOGGER.info("[Recruitment] Using Towny Map profile cache bridge");
            return new TownyMapProfileBridge(profile);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.debug("[Recruitment] Towny Map profile bridge unavailable: {}", e.getMessage());
            return DISABLED;
        }
    }

    boolean available() {
        return profileMethod != null;
    }

    PlayerProfile cachedProfile(String name) {
        if (!available() || name == null || name.isBlank()) return null;
        try {
            Object profile = profileMethod.invoke(null, name);
            if (profile == null) return null;
            String profileName = stringValue(profile, "name", name);
            String town = stringValue(profile, "town", "");
            String nation = stringValue(profile, "nation", "");
            long registeredMs = longValue(profile, "registeredMs", 0L);
            if (registeredMs <= 0L) return null;
            return new PlayerProfile(profileName, town, nation, registeredMs);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("[Recruitment] Towny Map cached profile read failed: {}", e.getMessage());
            return null;
        }
    }

    private static String stringValue(Object target, String methodName, String fallback) throws ReflectiveOperationException {
        Object value = target.getClass().getMethod(methodName).invoke(target);
        return value instanceof String string ? string : fallback;
    }

    private static long longValue(Object target, String methodName, long fallback) throws ReflectiveOperationException {
        Object value = target.getClass().getMethod(methodName).invoke(target);
        return value instanceof Number number ? number.longValue() : fallback;
    }
}
