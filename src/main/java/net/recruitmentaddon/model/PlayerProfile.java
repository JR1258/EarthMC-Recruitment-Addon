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

    /** True if the account registered within the last {@code maxDays} days. */
    public boolean newlyRegistered(int maxDays) {
        if (registeredMs <= 0) return false;
        long ageMs = System.currentTimeMillis() - registeredMs;
        return ageMs >= 0 && ageMs <= maxDays * 24L * 60L * 60L * 1000L;
    }
}
