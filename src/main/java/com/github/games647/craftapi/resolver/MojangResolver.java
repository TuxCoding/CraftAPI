package com.github.games647.craftapi.resolver;

import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.craftapi.model.NameHistory;
import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.model.auth.MinecraftAccount;
import com.github.games647.craftapi.model.auth.Verification;
import com.github.games647.craftapi.model.skin.ChangeSkin;
import com.github.games647.craftapi.model.skin.Model;
import com.github.games647.craftapi.model.skin.SkinProperty;
import com.github.games647.craftapi.model.skin.Textures;
import com.github.games647.craftapi.resolver.ratelimiter.RateLimiter;
import com.github.games647.craftapi.resolver.ratelimiter.TickingRateLimiter;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import javax.net.ssl.HttpsURLConnection;
import java.awt.image.RenderedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Resolver that contacts Mojang.
 */
public class MojangResolver extends AbstractResolver implements AuthResolver, ProfileResolver {

    static {
        // A try to fix https://bugs.openjdk.org/browse/JDK-8197807
        HttpsURLConnection.getDefaultSSLSocketFactory();
    }

    //profile
    private static final String UUID_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String BACKUP_UUID_URL = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
    private boolean useBackupUuidUrl;

    //skin
    private static final String CHANGE_SKIN_URL = "https://api.mojang.com/user/profile/%s/skin";
    private static final String RESET_SKIN_URL = "https://api.mojang.com/user/profile/%s/skin";
    private static final String SKIN_URL = "https://sessionserver.mojang.com/session/minecraft/profile/%s" +
            "?unsigned=false";

    //authentication
    private static final String HAS_JOINED_URL_PROXY_CHECK = "https://sessionserver.mojang.com/session/minecraft/" +
            "hasJoined?username=%s&serverId=%s&ip=%s";
    private static final String HAS_JOINED_URL_RAW = "https://sessionserver.mojang.com/session/minecraft/hasJoined?" +
            "username=%s&serverId=%s";

    private final RateLimiter profileLimiter;

    public MojangResolver(Options options) {
        super(options);

        profileLimiter = new TickingRateLimiter(
                Ticker.systemTicker(), options.getMaxNameRequests(),
                TimeUnit.MINUTES.toMillis(10)
        );
    }

    @Override
    public Optional<Verification> hasJoined(String username, String serverHash, InetAddress hostIp)
            throws IOException {
        String url;
        if (hostIp == null || hostIp instanceof Inet6Address) {
            // Mojang currently doesn't check the IPv6 address correct. The prevent-proxy even doesn't work with
            // a vanilla server
            url = String.format(HAS_JOINED_URL_RAW, username, serverHash);
        } else {
            String encodedIP = URLEncoder.encode(hostIp.getHostAddress(), StandardCharsets.UTF_8);
            url = String.format(HAS_JOINED_URL_PROXY_CHECK, username, serverHash, encodedIP);
        }

        HttpRequest req = createJSONGet(url);
        try {
            HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());

            int responseCode = resp.statusCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                return Optional.empty();
            }

            return Optional.of(readJson(resp.body(), Verification.class));
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void changeSkin(MinecraftAccount account, URL toUrl, Model skinModel) throws IOException {
        String url = String.format(CHANGE_SKIN_URL, UUIDAdapter.toMojangId(account.getProfile().getId()));

        String payload = gson.toJson(new ChangeSkin(skinModel, toUrl));
        HttpRequest req = createJSONReq(url)
                .header("Authorization", "Bearer " + account.getAccessToken())
                .POST(BodyPublishers.ofString(payload))
                .build();

        try {
            HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());

            int responseCode = resp.statusCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Response code is not Ok: " + responseCode);
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void changeSkin(MinecraftAccount account, RenderedImage pngImage, Model skinModel) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean resetSkin(MinecraftAccount account) throws IOException {
        String url = String.format(RESET_SKIN_URL, account.getProfile().getId());

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + account.getAccessToken())
                .DELETE()
                .build();
        try {
            HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());

            int responseCode = resp.statusCode();
            return responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Override
    public ImmutableSet<Profile> findProfiles(String... names) throws IOException, RateLimitException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ImmutableList<NameHistory> findNames(UUID uuid) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Optional<Profile> findProfile(String name) throws IOException, RateLimitException {
        Optional<Profile> optProfile = cache.getByName(name);
        if (optProfile.isPresent() || !validNamePredicate.test(name)) {
            return optProfile;
        }

        String url = (useBackupUuidUrl ? BACKUP_UUID_URL : UUID_URL) + name;
        HttpRequest req = createJSONGet(url);

        HttpClient client = this.client;
        if (!profileLimiter.tryAcquire()) {
            if (proxyClient == null) {
                throw new RateLimitException();
            }

            client = proxyClient;
        }

        return findProfile(client, req);
    }

    protected Optional<Profile> findProfile(HttpClient client, HttpRequest req)
            throws IOException, RateLimitException {
        try {
            HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());

            int responseCode = resp.statusCode();
            if (responseCode == RateLimitException.RATE_LIMIT_RESPONSE_CODE) {
                if (client.proxy().isPresent() || proxyClient == null) {
                    // was from the proxy executor or there are no proxies available
                    throw new RateLimitException();
                }

                // another try with a proxy
                return findProfile(proxyClient, req);
            }

            if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                if (useBackupUuidUrl) {
                    throw new IOException("Both Mojang APIs returned 403 Forbidden");
                }

                useBackupUuidUrl = true;
                // extract only the username and query parameters
                String backupUrl = BACKUP_UUID_URL + req.uri().getPath()
                    .substring(req.uri().getPath().lastIndexOf('/') + 1);
                HttpRequest backupReq = createJSONGet(backupUrl);
                return findProfile(client, backupReq);
            }

            if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                return Optional.empty();
            }

            //todo: print errorstream on IOException
            Profile profile = readJson(resp.body(), Profile.class);
            System.out.println("Parsed " + profile);
            cache.add(profile);
            return Optional.of(profile);
        } catch (InterruptedException interruptedException) {
            return Optional.empty();
        } catch (FileNotFoundException fileNotFoundException) {
            //new API treats not found as cracked
            return Optional.empty();
        }
    }

    @Override
    public Optional<Profile> findProfile(String name, Instant time) throws IOException, RateLimitException {
        Optional<Profile> optProfile = cache.getByName(name);
        if (optProfile.isPresent() || !validNamePredicate.test(name)) {
            return optProfile;
        }

        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Optional<SkinProperty> downloadSkin(UUID uuid) throws IOException, RateLimitException {
        Optional<SkinProperty> optSkin = cache.getSkin(uuid);
        if (optSkin.isPresent()) {
            return optSkin;
        }

        String url = String.format(SKIN_URL, UUIDAdapter.toMojangId(uuid));
        HttpRequest req = createJSONGet(url);
        try {
            HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());

            int responseCode = resp.statusCode();
            if (responseCode == RateLimitException.RATE_LIMIT_RESPONSE_CODE) {
                throw new RateLimitException();
            }

            if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                return Optional.empty();
            }

            Textures texturesModel = readJson(resp.body(), Textures.class);
            SkinProperty property = texturesModel.getProperties()[0];

            cache.addSkin(uuid, property);
            return Optional.of(property);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }
}
