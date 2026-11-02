package com.github.petergamez.joox;

import com.github.petergamez.joox.proxy.ProxyRotator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Diagnostic and management REST controller for JOOX Plugin.
 */
@RestController
@RequestMapping("/v4/joox")
@ConditionalOnProperty(prefix = "plugins.joox.rest-api", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JooxRestHandler {

    private final JooxPluginLoader pluginLoader;

    public JooxRestHandler(JooxPluginLoader pluginLoader) {
        this.pluginLoader = pluginLoader;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(
            @RequestHeader(value = "X-Joox-Password", required = false) String jooxPassword) {
        if (!isAuthorized(jooxPassword)) {
            return unauthorizedResponse();
        }

        JooxPluginConfig config = pluginLoader.getConfig();
        ProxyRotator rotator = pluginLoader.getSourceManager().getProxyRotator();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("enabled", config.isEnabled());
        response.put("countryCode", config.getCountryCode());
        response.put("qualities", config.getQualities().stream().map(Enum::name).collect(Collectors.toList()));
        response.put("allowSearch", config.isAllowSearch());
        response.put("proxyEnabled", config.getProxy().isEnabled());
        response.put("totalProxies", rotator.getTotalCount());
        response.put("availableProxies", rotator.getAvailableCount());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/proxies")
    public ResponseEntity<Map<String, Object>> getProxies(
            @RequestHeader(value = "X-Joox-Password", required = false) String jooxPassword) {
        if (!isAuthorized(jooxPassword)) {
            return unauthorizedResponse();
        }

        ProxyRotator rotator = pluginLoader.getSourceManager().getProxyRotator();

        List<Map<String, Object>> proxyList = rotator.getEntries().stream().map(entry -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("host", entry.getHost());
            map.put("port", entry.getPort());
            map.put("type", entry.getType().name());
            map.put("available", entry.isAvailable());
            map.put("consecutiveFailures", entry.getConsecutiveFailures());
            map.put("totalSuccesses", entry.getTotalSuccesses());
            map.put("quarantinedUntil", entry.getQuarantinedUntil());
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("strategy", pluginLoader.getConfig().getProxy().getStrategy().name());
        response.put("proxies", proxyList);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/proxies/reset")
    public ResponseEntity<Map<String, Object>> resetProxies(
            @RequestHeader(value = "X-Joox-Password", required = false) String jooxPassword) {
        if (!isAuthorized(jooxPassword)) {
            return unauthorizedResponse();
        }

        ProxyRotator rotator = pluginLoader.getSourceManager().getProxyRotator();
        rotator.resetAllQuarantines();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "All proxy quarantines have been reset");
        response.put("availableProxies", rotator.getAvailableCount());

        return ResponseEntity.ok(response);
    }

    private boolean isAuthorized(String jooxPassword) {
        String requiredPassword = pluginLoader.getConfig().getRestApi().getPassword();
        if (requiredPassword == null || requiredPassword.trim().isEmpty()) {
            return true;
        }
        if (jooxPassword == null) {
            return false;
        }
        return MessageDigest.isEqual(
                requiredPassword.trim().getBytes(StandardCharsets.UTF_8),
                jooxPassword.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    private ResponseEntity<Map<String, Object>> unauthorizedResponse() {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", "Unauthorized");
        error.put("message", "Invalid or missing X-Joox-Password header");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
}

