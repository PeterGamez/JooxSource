package com.github.petergamez.joox;

import com.github.petergamez.joox.config.JooxConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Lavalink v4 plugin configuration binding for JOOX Audio Source.
 * Binds properties from application.yml under 'plugins.joox'.
 */
@Component
@ConfigurationProperties(prefix = "plugins.joox")
public class JooxPluginConfig extends JooxConfig {

    private RestApiConfig restApi = new RestApiConfig();

    public static class RestApiConfig {
        private boolean enabled = true;
        private String password = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password != null ? password : "";
        }
    }

    public RestApiConfig getRestApi() {
        return restApi;
    }

    public void setRestApi(RestApiConfig restApi) {
        this.restApi = restApi != null ? restApi : new RestApiConfig();
    }
}

