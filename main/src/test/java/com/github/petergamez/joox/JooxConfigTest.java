package com.github.petergamez.joox;

import com.github.petergamez.joox.config.JooxAudioQuality;
import com.github.petergamez.joox.config.JooxConfig;
import com.github.petergamez.joox.config.ProxyStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JooxConfigTest {

    @Test
    void testDefaultValues() {
        JooxConfig config = new JooxConfig();

        assertTrue(config.isEnabled());
        assertEquals("TH", config.getCountryCode());
        assertEquals(JooxAudioQuality.MP3_320, config.getQuality());
        assertEquals(4, config.getQualities().size());
        assertEquals(JooxAudioQuality.MP3_320, config.getQualities().get(0));
        assertTrue(config.isAllowSearch());
        assertEquals(20, config.getSearchLimit());
        assertNotNull(config.getProxy());
        assertFalse(config.getProxy().isEnabled());
    }

    @Test
    void testCustomConfiguration() {
        JooxConfig config = new JooxConfig();
        config.setEnabled(false);
        config.setCountryCode("id");
        config.setQuality(JooxAudioQuality.FLAC);
        config.setWmid("test-wmid");
        config.setSessionToken("test-session");
        config.setAllowSearch(false);
        config.setSearchLimit(50);

        assertFalse(config.isEnabled());
        assertEquals("ID", config.getCountryCode());
        assertEquals(JooxAudioQuality.FLAC, config.getQuality());
        assertEquals("test-wmid", config.getWmid());
        assertEquals("test-session", config.getSessionToken());
        assertFalse(config.isAllowSearch());
        assertEquals(50, config.getSearchLimit());
    }

    @Test
    void testProxyConfiguration() {
        JooxConfig config = new JooxConfig();
        config.getProxy().setEnabled(true);
        config.getProxy().setStrategy(ProxyStrategy.failover);
        config.getProxy().setProxies(List.of("http://127.0.0.1:8080", "socks5://127.0.0.1:1080"));

        assertTrue(config.getProxy().isEnabled());
        assertEquals(ProxyStrategy.failover, config.getProxy().getStrategy());
        assertEquals(2, config.getProxy().getProxies().size());
    }
}

