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
    private static final String DEFAULT_GLOBAL_AD_MESSAGE =
            "Looking for a town? New players welcome. Message me or ask in chat for an invite!";

    /** Master switch. When off, no join alerts are posted. */
    public boolean enabled = true;

    /**
     * A player counts as "new" only if their account was registered within this many
     * seconds. Because a brand-new player's account is created the moment they first
     * join, this keeps alerts to genuine first-time joiners and filters out established
     * players who merely re-appear in the list. Lower it for stricter matching.
     */
    public int newPlayerMaxSeconds = 90;

    /** Suppresses duplicate alerts for the same player during this many minutes. */
    public int alertCooldownMinutes = 10;

    /** Delays the recruit copy prompt after a new-player join is confirmed. */
    public int alertDelaySeconds = 0;

    /** Plays a short UI sound when a recruit or follow-up prompt is posted. */
    public boolean soundEnabled = true;

    /** Periodically reminds you to post a public recruitment ad. */
    public boolean globalAdReminderEnabled = false;

    /** Minutes between public recruitment ad reminders. */
    public int globalAdReminderMinutes = 5;

    /** Uses /showitem as the public ad reminder action instead of copying text. */
    public boolean globalAdUseShowItem = false;

    /** Message copied by the public recruitment ad reminder. */
    public String globalAdMessage =
            DEFAULT_GLOBAL_AD_MESSAGE;

    /**
     * Message copied when the recruit prompt is clicked. {player} is substituted with
     * the new player's name. The mod never sends this automatically.
     */
    public String recruitMessage =
            "/msg {player} Hey {player}! Welcome to EarthMC — looking for a town? Message me!";

    /** Plain phrase used to detect Towny success messages. */
    public String townJoinPhrase = "joined the town";

    /** Copy buttons shown after a Towny join-success message is detected. */
    public List<FollowUpMessage> followUpMessages = new ArrayList<>(List.of(
            new FollowUpMessage("Welcome", "Welcome to the town, {player}!"),
            new FollowUpMessage("Rules", "/msg {player} Quick note: please read the town rules when you have a minute.")
    ));

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
                    cfg.ensureDefaults();
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

    private void ensureDefaults() {
        if (excludedPlayers == null) excludedPlayers = new ArrayList<>();
        if (followUpMessages == null) followUpMessages = new ArrayList<>();
        if (globalAdReminderMinutes < 5) globalAdReminderMinutes = 5;
        if (globalAdMessage == null || globalAdMessage.isBlank()) globalAdMessage = DEFAULT_GLOBAL_AD_MESSAGE;
    }

    public static class FollowUpMessage {
        public String title;
        public String message;

        public FollowUpMessage() {}

        public FollowUpMessage(String title, String message) {
            this.title = title;
            this.message = message;
        }
    }
}
