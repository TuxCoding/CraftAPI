package com.github.games647.craftapi.resolver;

import com.github.games647.craftapi.InstantAdapter;
import com.github.games647.craftapi.NamePredicate;
import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.craftapi.cache.Cache;
import com.github.games647.craftapi.cache.MemoryCache;
import com.github.games647.craftapi.model.skin.Skin;
import com.github.games647.craftapi.model.skin.SkinProperty;
import com.github.games647.craftapi.resolver.http.RotatingSourceFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Base class for fetching Minecraft related data.
 */
public abstract class AbstractResolver {

    protected final Predicate<String> validNamePredicate = new NamePredicate();
    protected RotatingSourceFactory sslFactory;

    protected Cache cache = new MemoryCache();

    protected final Gson gson = new GsonBuilder()
            .registerTypeAdapter(UUID.class, new UUIDAdapter())
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .create();

    protected final HttpClient client = HttpClient.newHttpClient();
    protected HttpClient proxyClient;

    /**
     * Decodes the property from a skin request.
     *
     * @param property Base64 encoded skin property
     * @return decoded model
     */
    public Skin decodeSkin(SkinProperty property) {
        byte[] data = Base64.getDecoder().decode(property.getValue());
        String json = new String(data, StandardCharsets.UTF_8);

        Skin skinModel = gson.fromJson(json, Skin.class);
        skinModel.setSignature(Base64.getDecoder().decode(property.getSignature()));
        return skinModel;
    }

    /**
     * Decodes the skin for setting it in game.
     *
     * @param skinModel decoded skin model with signature
     * @return Base64 encoded skin property
     */
    public SkinProperty encodeSkin(Skin skinModel) {
        String json = gson.toJson(skinModel);

        String encodedValue = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = Base64.getEncoder().encodeToString(skinModel.getSignature());
        return new SkinProperty(encodedValue, encodedSignature);
    }

    protected <T> T readJson(String json, Class<T> classOfT) {
        return gson.fromJson(json, classOfT);
    }

    protected static Builder createJSONReq(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json");
    }

    protected static HttpRequest createJSONGet(String url) {
        return createJSONReq(url)
                .GET()
                .build();
    }

    /**
     * @return the current cache backend.
     */
    public Cache getCache() {
        return cache;
    }

    /**
     * Sets a new Mojang cache.
     *
     * @param cache cache implementation
     */
    public void setCache(Cache cache) {
        this.cache = cache;
    }

    /**
     * Set the outgoing addresses. The rotating order will be the same as in the given collection.
     *
     * @param addresses all outgoing IPv4 addresses that are available or empty to disable it.
     */
    @Deprecated
    public void setOutgoingAddresses(Collection<InetAddress> addresses) {
        if (addresses.isEmpty()) {
            sslFactory = null;
            return;
        }

        if (sslFactory == null) {
            sslFactory = new RotatingSourceFactory();
        }

        sslFactory.setOutgoingAddresses(addresses);
    }
}
