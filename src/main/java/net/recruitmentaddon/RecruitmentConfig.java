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

    /** Master switch. When off, no join alerts are posted. */
    public boolean enabled = true;

    /**
     * A player counts as "new" if their account was registered within this many days.
     * The alert only fires for players who join while you're online <i>and</i> are this
     * new. Set to 1 for only just-registered players.
     */
    public int newPlayerMaxDays = 7;

    /**
     * Command run when the alert message is clicked. {player} is substituted with the
     * new player's name. Defaults to a private welcome message.
     */
    public String recruitMessage =
            "/msg {player} Hey {player}! Welcome to EarthMC — looking for a town? Message me!";

    /** Only run while connected to an earthmc.net server. */
    public boolean earthmcOnly = true;

    /** Players that never trigger an alert. */
    public List<String> excludedPlayers = new ArrayList<>();

    /** Official EarthMC API base (used only to look up a joiner's registration date). */
    public String earthmcApiBaseUrl = "https://api.earthmc.net/v4";

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
