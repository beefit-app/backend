package com.beefit.api.infra.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionHttpsFilterTest {

    private final ProductionHttpsFilter filter = new ProductionHttpsFilter();

    @Test
    void rejectsInsecureProductionRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/profile/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(426, response.getStatus());
    }

    @Test
    void acceptsSecureProductionRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/profile/me");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }
}
