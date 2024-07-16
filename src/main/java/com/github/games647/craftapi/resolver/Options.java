package com.github.games647.craftapi.resolver;

import com.github.games647.craftapi.cache.Cache;
import com.github.games647.craftapi.cache.MemoryCache;

import java.net.ProxySelector;
import java.util.concurrent.Executor;

public class Options {

    private Executor executor;
    private Cache cache = new MemoryCache();

    private int maxNameRequests = 600;
    private ProxySelector proxySelector = ProxySelector.getDefault();

    public Executor getExecutor() {
        return executor;
    }

    public Cache getCache() {
        return cache;
    }

    public int getMaxNameRequests() {
        return maxNameRequests;
    }

    public ProxySelector getProxySelector() {
        return proxySelector;
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
     * @param proxySelector proxy selector that should be used
     */
    public void setProxySelector(ProxySelector proxySelector) {
        this.proxySelector = proxySelector;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    /**
     * @param maxNameRequests maximum amount of name to UUID requests that will be established to Mojang directly
     *                        without proxies. (Between 0 and 600 within 10 minutes)
     */
    public void setMaxNameRequests(int maxNameRequests) {
        this.maxNameRequests = Math.max(600, maxNameRequests);
    }
}
