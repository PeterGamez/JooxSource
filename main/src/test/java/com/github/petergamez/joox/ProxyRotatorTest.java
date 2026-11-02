package com.github.petergamez.joox;

import com.github.petergamez.joox.config.ProxyConfig;
import com.github.petergamez.joox.config.ProxyStrategy;
import com.github.petergamez.joox.proxy.ProxyEntry;
import com.github.petergamez.joox.proxy.ProxyRotator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProxyRotatorTest {

    @Test
    void testDisabledProxyRotatorReturnsNull() {
        ProxyConfig config = new ProxyConfig();
        config.setEnabled(false);

        ProxyRotator rotator = new ProxyRotator(config);
        assertNull(rotator.nextProxy());
        assertEquals(0, rotator.getTotalCount());
    }

    @Test
    void testRoundRobinRotation() {
        ProxyConfig config = new ProxyConfig();
        config.setEnabled(true);
        config.setStrategy(ProxyStrategy.roundRobin);
        config.setProxies(List.of(
                "http://10.0.0.1:8080",
                "http://10.0.0.2:8080",
                "http://10.0.0.3:8080"
        ));

        ProxyRotator rotator = new ProxyRotator(config);
        assertEquals(3, rotator.getTotalCount());

        ProxyEntry p1 = rotator.nextProxy();
        ProxyEntry p2 = rotator.nextProxy();
        ProxyEntry p3 = rotator.nextProxy();
        ProxyEntry p4 = rotator.nextProxy();

        assertNotNull(p1);
        assertNotNull(p2);
        assertNotNull(p3);
        assertNotNull(p4);
        assertEquals(p1.getHost(), p4.getHost()); // Cycles back in round-robin
    }

    @Test
    void testFailoverStrategyAndQuarantine() {
        ProxyConfig config = new ProxyConfig();
        config.setEnabled(true);
        config.setStrategy(ProxyStrategy.failover);
        config.setCooldownSeconds(10);
        config.setProxies(List.of(
                "http://primary:8080",
                "http://secondary:8080"
        ));

        ProxyRotator rotator = new ProxyRotator(config);
        ProxyEntry primary = rotator.nextProxy();
        assertEquals("primary", primary.getHost());

        // Simulate 3 failures to trigger quarantine
        rotator.markFailure(primary, new RuntimeException("Connection timed out"));
        rotator.markFailure(primary, new RuntimeException("Connection reset"));
        rotator.markFailure(primary, new RuntimeException("HTTP 502"));

        assertFalse(primary.isAvailable());

        // Next proxy should now failover to secondary
        ProxyEntry secondary = rotator.nextProxy();
        assertNotNull(secondary);
        assertEquals("secondary", secondary.getHost());

        // Reset quarantines
        rotator.resetAllQuarantines();
        assertTrue(primary.isAvailable());
    }
}

