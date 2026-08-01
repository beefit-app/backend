package com.beefit.api.infra.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class CorsConfigurationTest {

    @Test
    void productionAcceptsOnlyExactOrigins() {
        SecurityConfiguration security = configuration("prod", "https://app.beefit.example");
        var source = (UrlBasedCorsConfigurationSource) security.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/profile/me");

        CorsConfiguration cors = source.getCorsConfiguration(request);

        assertEquals(List.of("https://app.beefit.example"), cors.getAllowedOrigins());
        assertEquals(null, cors.getAllowedOriginPatterns());
    }

    @Test
    void productionRejectsWildcardOrigins() {
        SecurityConfiguration security = configuration("prod", "https://*.beefit.example");
        assertThrows(IllegalStateException.class, security::corsConfigurationSource);
    }

    private SecurityConfiguration configuration(String profile, String... origins) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        SecurityConfiguration security = new SecurityConfiguration(mock(SecurityFilter.class), environment);
        ReflectionTestUtils.setField(security, "allowedOrigins", origins);
        return security;
    }
}
