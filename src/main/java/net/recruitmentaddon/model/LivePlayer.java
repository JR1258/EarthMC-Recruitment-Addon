package net.recruitmentaddon.model;

/** An online player and their world position, from the squaremap players feed. */
public record LivePlayer(String name, int x, int z) {}
