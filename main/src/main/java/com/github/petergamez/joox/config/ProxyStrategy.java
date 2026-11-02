package com.github.petergamez.joox.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Proxy load balancing and failover strategies in camelCase.
 */
public enum ProxyStrategy {
    roundRobin,
    random,
    failover;

    @JsonValue
    public String toValue() {
        return name();
    }

    @JsonCreator
    public static ProxyStrategy fromString(String value) {
        if (value == null) {
            return roundRobin;
        }
        for (ProxyStrategy strategy : values()) {
            if (strategy.name().equalsIgnoreCase(value)
                    || strategy.name().equalsIgnoreCase(value.replace("-", "").replace("_", ""))) {
                return strategy;
            }
        }
        return roundRobin;
    }
}
