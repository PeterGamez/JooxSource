package com.github.petergamez.joox.proxy;

import com.github.petergamez.joox.config.ProxyConfig;
import com.github.petergamez.joox.config.ProxyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe proxy manager supporting Round-Robin, Random, and Failover rotation
 * with automatic quarantine cooldown for unstable proxies.
 */
public class ProxyRotator {

    private static final Logger log = LoggerFactory.getLogger(ProxyRotator.class);

    private final ProxyConfig config;
    private final List<ProxyEntry> entries = new CopyOnWriteArrayList<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    public ProxyRotator(ProxyConfig config) {
        this.config = config != null ? config : new ProxyConfig();
        reloadProxies();
    }

    public synchronized void reloadProxies() {
        entries.clear();
        if (config.isEnabled() && config.getProxies() != null) {
            for (String url : config.getProxies()) {
                if (url == null || url.trim().isEmpty()) {
                    continue;
                }
                try {
                    entries.add(new ProxyEntry(url.trim()));
                } catch (Exception e) {
                    log.warn("Failed to parse proxy entry '{}': {}", url, e.getMessage());
                }
            }
        }
        log.info("Loaded {} active proxies with strategy {}", entries.size(), config.getStrategy());
    }

    /**
     * Retrieves the next available proxy according to the configured rotation strategy.
     *
     * @return ProxyEntry or null if proxy rotation is disabled or no proxies are available.
     */
    public ProxyEntry nextProxy() {
        if (!config.isEnabled() || entries.isEmpty()) {
            return null;
        }

        ProxyStrategy strategy = config.getStrategy() != null ? config.getStrategy() : ProxyStrategy.roundRobin;
        int size = entries.size();

        for (int attempts = 0; attempts < size; attempts++) {
            ProxyEntry candidate;
            switch (strategy) {
                case random:
                    candidate = entries.get(ThreadLocalRandom.current().nextInt(size));
                    break;
                case failover:
                    // In failover mode, always try the first healthy proxy in the priority list
                    candidate = entries.get(attempts);
                    break;
                case roundRobin:
                default:
                    int index = Math.abs(roundRobinIndex.getAndIncrement() % size);
                    candidate = entries.get(index);
                    break;
            }

            if (candidate.isAvailable()) {
                return candidate;
            }
        }

        // If all proxies are currently quarantined, log warning and fallback to least failed proxy
        log.warn("All {} proxies are quarantined or failing. Selecting lowest failure proxy fallback.", size);
        return entries.stream()
                .min((p1, p2) -> Integer.compare(p1.getConsecutiveFailures(), p2.getConsecutiveFailures()))
                .orElse(null);
    }

    public void markSuccess(ProxyEntry entry) {
        if (entry != null) {
            entry.markSuccess();
        }
    }

    public void markFailure(ProxyEntry entry, Exception cause) {
        if (entry != null) {
            entry.markFailure(config.getCooldownSeconds());
            log.warn("Proxy {} reported failure ({}/{}): {}",
                    entry, entry.getConsecutiveFailures(), config.getRetryLimit(),
                    cause != null ? cause.getMessage() : "Unknown");
        }
    }

    public void resetAllQuarantines() {
        for (ProxyEntry entry : entries) {
            entry.resetQuarantine();
        }
    }

    public List<ProxyEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public int getTotalCount() {
        return entries.size();
    }

    public long getAvailableCount() {
        return entries.stream().filter(ProxyEntry::isAvailable).count();
    }
}

