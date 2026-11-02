package com.github.petergamez.joox.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Proxy rotation configuration.
 */
public class ProxyConfig {

    private boolean enabled = false;
    private ProxyStrategy strategy = ProxyStrategy.LoadBalance;
    private List<String> proxies = new ArrayList<>();
    private int retryLimit = 3;
    private int cooldownSeconds = 60;
    private int connectTimeoutSeconds = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ProxyStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(ProxyStrategy strategy) {
        this.strategy = strategy;
    }

    public List<String> getProxies() {
        return proxies;
    }

    public void setProxies(List<String> proxies) {
        this.proxies = proxies != null ? proxies : new ArrayList<>();
    }

    public int getRetryLimit() {
        return retryLimit;
    }

    public void setRetryLimit(int retryLimit) {
        this.retryLimit = retryLimit;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }
}
