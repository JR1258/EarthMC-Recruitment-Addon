package net.recruitmentaddon.alert;

import net.recruitmentaddon.RecruitmentConfig;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TownJoinDetector {

    private static final Pattern USERNAME = Pattern.compile("([A-Za-z0-9_]{3,16})");

    private TownJoinDetector() {}

    public static String joinedPlayer(String message, RecruitmentConfig config) {
        if (message == null || config == null || blank(config.townJoinPhrase)) return null;
        String lower = message.toLowerCase(Locale.ROOT);
        String phrase = config.townJoinPhrase.toLowerCase(Locale.ROOT);
        int phraseAt = lower.indexOf(phrase);
        if (phraseAt < 0) return null;

        String before = message.substring(0, phraseAt);
        Matcher matcher = USERNAME.matcher(before);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        return last;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
