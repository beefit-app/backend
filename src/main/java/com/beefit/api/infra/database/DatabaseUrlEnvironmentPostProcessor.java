package com.beefit.api.infra.database;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String[] keys = {"SPRING_DATASOURCE_URL", "DATABASE_URL", "spring.datasource.url"};

        for (String key : keys) {
            String url = environment.getProperty(key);
            if (url != null && !url.isBlank() && !url.startsWith("jdbc:")) {
                String sanitizedUrl;
                if (url.startsWith("postgres://")) {
                    sanitizedUrl = "jdbc:postgresql://" + url.substring("postgres://".length());
                } else if (url.startsWith("postgresql://")) {
                    sanitizedUrl = "jdbc:postgresql://" + url.substring("postgresql://".length());
                } else {
                    sanitizedUrl = "jdbc:" + url;
                }

                Map<String, Object> map = new HashMap<>();
                map.put("spring.datasource.url", sanitizedUrl);
                map.put("SPRING_DATASOURCE_URL", sanitizedUrl);
                map.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
                environment.getPropertySources().addFirst(new MapPropertySource("sanitizedDatabaseUrl", map));
                break;
            }
        }
    }
}
