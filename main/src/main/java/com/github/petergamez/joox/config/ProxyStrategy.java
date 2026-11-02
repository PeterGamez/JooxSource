package com.github.petergamez.joox.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Proxy load balancing and routing strategies matching Lavalink conventions.
 */
public enum ProxyStrategy {
    LoadBalance,
    NanoSwitch,
    Failover;

    @JsonValue
    public String toValue() {
        return name();
    }

    @JsonCreator
    public static ProxyStrategy fromString(String value) {
        if (value == null) {
            return LoadBalance;
        }
        for (ProxyStrategy strategy : values()) {
            if (strategy.name().equalsIgnoreCase(value)) {
                return strategy;
            }
        }
        return LoadBalance;
    }
}
