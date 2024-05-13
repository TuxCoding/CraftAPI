package com.github.games647.craftapi.model.auth;

import com.github.games647.craftapi.model.Profile;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a logged in Mojang account.
 */
public class MinecraftAccount {

    private final Profile profile;
    private final UUID accessToken;
    private final Duration expiresIn;

    /**
     * Creates a new Mojang account.
     *
     * @param profile the selected game profile
     * @param accessToken access token for authentication instead of the password
     */
    public MinecraftAccount(Profile profile, UUID accessToken, Duration expiresIn) {
        this.profile = profile;
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
    }

    /**
     * @return Minecraft profile that associated this account
     */
    public Profile getProfile() {
        return profile;
    }

    /**
     * @return access token for authentication against Mojang servers
     */
    public UUID getAccessToken() {
        return accessToken;
    }

    public Duration getExpiresIn() {
        return expiresIn;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof MinecraftAccount) {
            MinecraftAccount account = (MinecraftAccount) other;
            return Objects.equals(profile, account.profile) &&
                    Objects.equals(accessToken, account.accessToken);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(profile, accessToken);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{' +
                "profile=" + profile +
                '}';
    }
}
