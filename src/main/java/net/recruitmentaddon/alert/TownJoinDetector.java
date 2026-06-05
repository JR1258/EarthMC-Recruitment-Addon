package net.recruitmentaddon.alert;

import net.recruitmentaddon.RecruitmentConfig;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TownJoinDetector {

    private TownJoinDetector() {}

    public static String joinedPlayer(String message, RecruitmentConfig config) {
        if (message == null || config == null || blank(config.townJoinPhrase)) return null;
        Pattern joinLine = Pattern.compile(
                "(?i)(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16})\\s+"
                        + phrasePattern(config.townJoinPhrase)
                        + "(?![A-Za-z0-9_])"
        );
        Matcher matcher = joinLine.matcher(message);
        String player = null;
        int playerStart = -1;
        while (matcher.find()) {
            player = matcher.group(1);
            playerStart = matcher.start(1);
        }
        if (player == null || looksLikePlayerChat(message, playerStart)) return null;
        return player;
    }

    private static String phrasePattern(String phrase) {
        String trimmed = phrase.trim();
        String[] words = trimmed.split("\\s+");
        StringBuilder pattern = new StringBuilder();
        for (String word : words) {
            if (pattern.length() > 0) pattern.append("\\s+");
            pattern.append(Pattern.quote(word));
        }
        return pattern.toString();
    }

    private static boolean looksLikePlayerChat(String message, int playerStart) {
        if (playerStart <= 0) return false;
        String beforePlayer = message.substring(0, playerStart).toLowerCase(Locale.ROOT);
        int colonAt = beforePlayer.lastIndexOf(':');
        if (colonAt < 0) return false;
        return !beforePlayer.substring(0, colonAt).contains("towny");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
