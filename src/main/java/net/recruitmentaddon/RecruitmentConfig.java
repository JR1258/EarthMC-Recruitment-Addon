package net.recruitmentaddon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Persistent settings, saved as {@code config/recruitmentaddon.json}. */
public class RecruitmentConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("RecruitmentAddon");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "recruitmentaddon.json";

    // ── Who counts as "recruitable" ───────────────────────────────────────────
    /** 0 = townless, 1 = townless or nationless, 2 = newly-registered players. */
    public int recruitableMode = 0;
    /** "Newly registered" threshold for mode 2 and the join alert, in days. */
    public int newPlayerMaxDays = 7;

    // ── HUD ───────────────────────────────────────────────────────────────────
    public boolean hudEnabled = true;
    /** Only list recruitable players within this many blocks (0 = unlimited). */
    public int hudRange = 1000;
    public int hudMaxEntries = 10;

    // ── New-player join alert ─────────────────────────────────────────────────
    public boolean newPlayerAlertEnabled = true;
    /**
     * Command run when the alert message is clicked. {player} is substituted with
     * the new player's name. Defaults to a private message.
     */
    public String recruitMessage =
            "/msg {player} Hey {player}! Welcome to EarthMC — looking for a town? Message me!";

    // ── General ───────────────────────────────────────────────────────────────
    public boolean earthmcOnly = true;
    public List<String> excludedPlayers = new ArrayList<>();

    // ── Endpoints (configurable per server, e.g. Aurora vs Nostra) ────────────
    public String squaremapBaseUrl = "https://map.earthmc.net";
    public String earthmcApiBaseUrl = "https://api.earthmc.net/v4";

    public String playersUrl() {
        return squaremapBaseUrl + "/tiles/players.json";
    }

    // ── Persistence ───────────────────────────────────────────────────────────
    public static RecruitmentConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (Files.exists(path)) {
            try {
                RecruitmentConfig cfg = GSON.fromJson(Files.readString(path), RecruitmentConfig.class);
                if (cfg != null) {
                    if (cfg.excludedPlayers == null) cfg.excludedPlayers = new ArrayList<>();
                    return cfg;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to read config, using defaults", e);
            }
        }
        RecruitmentConfig defaults = new RecruitmentConfig();
        defaults.save();
        return defaults;
    }

    public void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        try {
            Files.writeString(path, GSON.toJson(this));
        } catch (Exception e) {
            LOGGER.warn("Failed to write config", e);
        }
    }
}
