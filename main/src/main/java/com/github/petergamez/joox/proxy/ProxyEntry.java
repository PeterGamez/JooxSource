package com.github.petergamez.joox.proxy;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Encapsulates a proxy endpoint with health monitoring and failover state.
 */
public class ProxyEntry {

    private final String rawUrl;
    private final Proxy.Type type;
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long quarantinedUntil = 0L;
    private volatile long totalSuccesses = 0L;

    public ProxyEntry(String rawUrl) {
        this.rawUrl = rawUrl;
        try {
            URI uri = URI.create(rawUrl);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "http";
            if (scheme.startsWith("socks")) {
                this.type = Proxy.Type.SOCKS;
            } else {
                this.type = Proxy.Type.HTTP;
            }

            this.host = uri.getHost();
            int p = uri.getPort();
            this.port = p > 0 ? p : (this.type == Proxy.Type.SOCKS ? 1080 : 8080);

            if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
                String[] parts = uri.getUserInfo().split(":", 2);
                this.username = parts[0];
                this.password = parts.length > 1 ? parts[1] : "";
            } else {
                this.username = null;
                this.password = null;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid proxy URL format: " + rawUrl, e);
        }
    }

    public Proxy toJavaProxy() {
        return new Proxy(type, new InetSocketAddress(host, port));
    }

    public InetSocketAddress toInetSocketAddress() {
        return new InetSocketAddress(host, port);
    }

    public boolean isAvailable() {
        return System.currentTimeMillis() >= quarantinedUntil;
    }

    public void markSuccess() {
        consecutiveFailures.set(0);
        quarantinedUntil = 0L;
        totalSuccesses++;
    }

    public void markFailure(int cooldownSeconds) {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= 3) {
            quarantinedUntil = System.currentTimeMillis() + (cooldownSeconds * 1000L);
        }
    }

    public void resetQuarantine() {
        consecutiveFailures.set(0);
        quarantinedUntil = 0L;
    }

    public String getRawUrl() {
        return rawUrl;
    }

    public Proxy.Type getType() {
        return type;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean hasAuthentication() {
        return username != null && !username.isEmpty();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public long getQuarantinedUntil() {
        return quarantinedUntil;
    }

    public long getTotalSuccesses() {
        return totalSuccesses;
    }

    @Override
    public String toString() {
        return host + ":" + port + " (" + type + ")";
    }
}

