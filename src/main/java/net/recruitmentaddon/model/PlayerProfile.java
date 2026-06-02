package net.recruitmentaddon.model;

/**
 * EarthMC account details for a player, from the official API.
 * Empty {@code town}/{@code nation} mean the player has none. {@code registeredMs}
 * is the account registration time (epoch millis), 0 if unknown.
 */
public record PlayerProfile(String name, String town, String nation, long registeredMs) {

    public boolean townless() {
        return town == null || town.isBlank();
    }

    public boolean nationless() {
        return nation == null || nation.isBlank();
    }

    /** Account age in whole seconds, or {@code Long.MAX_VALUE} if the registration time is unknown. */
    public long ageSeconds() {
        if (registeredMs <= 0) return Long.MAX_VALUE;
        long ageMs = System.currentTimeMillis() - registeredMs;
        return ageMs < 0 ? 0 : ageMs / 1_000L;
    }

    /** True if the account registered within the last {@code maxSeconds} seconds. */
    public boolean registeredWithinSeconds(int maxSeconds) {
        return ageSeconds() <= maxSeconds;
    }
}
