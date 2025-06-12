package com.github.games647.craftapi.resolver;

import com.github.games647.craftapi.InstantAdapter;
import com.github.games647.craftapi.NamePredicate;
import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.craftapi.cache.Cache;
import com.github.games647.craftapi.model.skin.Skin;
import com.github.games647.craftapi.model.skin.SkinProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/**
 * Base class for fetching Minecraft related data.
 */
public abstract class AbstractResolver implements Closeable {

    protected final Predicate<String> validNamePredicate = new NamePredicate();

    protected final Cache cache;

    protected final Gson gson = new GsonBuilder()
            .registerTypeAdapter(UUID.class, new UUIDAdapter())
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .create();

    protected final HttpClient client;
    protected final HttpClient proxyClient;

    public AbstractResolver(Options options) {
        cache = options.getCache();

        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5));
        Executor executor = options.getExecutor();
        if (executor != null) {
            builder = builder.executor(executor);
        }

        client = builder.build();
        HttpClient.Builder proxyBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .proxy(options.getProxySelector());
        if (executor != null) {
            proxyBuilder = proxyBuilder.executor(executor);
        }

        proxyClient = proxyBuilder.build();
    }

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
                .timeout(Duration.ofSeconds(5))
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

    @Override
    public void close() throws IOException {
        closeClient(client);
        closeClient(proxyClient);
    }

    private void closeClient(HttpClient proxyClient) throws IOException {
        if (proxyClient instanceof AutoCloseable) {
            AutoCloseable closeableClient = (AutoCloseable) client;
            try {
                closeableClient.close();
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
    }
}
