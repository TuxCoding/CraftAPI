package com.github.games647.craftapi.resolver;

import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.craftapi.model.NameHistory;
import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.model.auth.MinecraftAccount;
import com.github.games647.craftapi.model.auth.Verification;
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
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    //skin
    private static final String CHANGE_SKIN_URL = "https://api.mojang.com/user/profile/%s/skin";
    private static final String RESET_SKIN_URL = "https://api.mojang.com/user/profile/%s/skin";
    private static final String SKIN_URL = "https://sessionserver.mojang.com/session/minecraft/profile/%s" +
            "?unsigned=false";

    //authentication
    private static final String AUTH_URL = "https://authserver.mojang.com/authenticate";
    private static final String HAS_JOINED_URL_PROXY_CHECK = "https://sessionserver.mojang.com/session/minecraft/" +
            "hasJoined?username=%s&serverId=%s&ip=%s";
    private static final String HAS_JOINED_URL_RAW = "https://sessionserver.mojang.com/session/minecraft/hasJoined?" +
            "username=%s&serverId=%s";

    private int maxNameRequests = 600;
    private final RateLimiter profileLimiter = new TickingRateLimiter(
            Ticker.systemTicker(), maxNameRequests,
            TimeUnit.MINUTES.toMillis(10)
    );

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

        HttpRequest req = createJSONReq(url);
        try {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
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

        HttpURLConnection conn = getConnection(url);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        conn.addRequestProperty("Authorization", "Bearer " + account.getAccessToken());
        try (
                OutputStream out = conn.getOutputStream();
                OutputStreamWriter outWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                BufferedWriter writer = new BufferedWriter(outWriter)
        ) {
            writer.write("model=");
            if (skinModel == Model.SLIM) {
                writer.write("slim");
            }

            final String skinUrl = toUrl.toExternalForm();
            writer.write("&url=" + URLEncoder.encode(skinUrl, StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        discard(conn);
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Response code is not Ok: " + responseCode);
        }
    }

    @Override
    public void changeSkin(MinecraftAccount account, RenderedImage pngImage, Model skinModel) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean resetSkin(MinecraftAccount account) throws IOException {
        String url = String.format(RESET_SKIN_URL, account.getProfile().getId());

        HttpURLConnection conn = getConnection(url);
        conn.setRequestMethod("DELETE");
        conn.addRequestProperty("Authorization", "Bearer " + account.getAccessToken());

        int responseCode = conn.getResponseCode();
        discard(conn);
        return responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT;
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

        String url = UUID_URL + name;
        HttpRequest req = createJSONReq(url);

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
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            int responseCode = resp.statusCode();
            if (responseCode == RateLimitException.RATE_LIMIT_RESPONSE_CODE) {
                if (client.proxy().isPresent() || proxyClient == null) {
                    // was from the proxy executor or there are no proxies available
                    throw new RateLimitException();
                }

                // another try with a proxy
                return findProfile(proxyClient, req);
            }

            if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                return Optional.empty();
            }

            //todo: print errorstream on IOException
            Profile profile = readJson(resp.body(), Profile.class);
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
        HttpURLConnection conn = getConnection(url);

        int responseCode = conn.getResponseCode();
        if (responseCode == RateLimitException.RATE_LIMIT_RESPONSE_CODE) {
            discard(conn);
            throw new RateLimitException();
        }

        if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
            return Optional.empty();
        }

        Textures texturesModel = parseRequest(conn, in -> readJson(in, Textures.class));
        SkinProperty property = texturesModel.getProperties()[0];

        cache.addSkin(uuid, property);
        return Optional.of(property);
    }

    /**
     * @param proxySelector proxy selector that should be used
     */
    public void setProxySelector(ProxySelector proxySelector) {
        proxyClient = HttpClient.newBuilder().proxy(proxySelector).build();
    }

    /**
     * @param maxNameRequests maximum amount of name to UUID requests that will be established to Mojang directly
     *                        without proxies. (Between 0 and 600 within 10 minutes)
     */
    public void setMaxNameRequests(int maxNameRequests) {
        this.maxNameRequests = Math.max(600, maxNameRequests);
    }
}
