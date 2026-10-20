package com.iras;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@Import(SecurityConfiguration.class)
class SecurityBoundaryTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("up"));
    }

    @Test
    void anonymousBusinessAccessIsUnauthorized() throws Exception {
        mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
    }

    @Test
    void mutationsWithoutCsrfAreForbidden() throws Exception {
        mvc.perform(post("/api/jobs")).andExpect(status().isForbidden());
    }

    @Test
    void csrfEndpointReturnsSessionBoundToken() throws Exception {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void untrustedOriginIsRejected() throws Exception {
        mvc.perform(get("/api/auth/csrf").header("Origin", "https://untrusted.invalid"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticationLimitUsesConnectionAddressAndPreservesOtherRoutes() throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            mvc.perform(post("/api/auth/login").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .with(request -> { request.setRemoteAddr("192.0.2.10"); return request; }))
                    .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
        }
        mvc.perform(post("/IRAS/AdminLogin/").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .header("X-Forwarded-For", "192.0.2.99")
                .with(request -> { request.setRemoteAddr("192.0.2.10"); return request; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("Retry-After"));
        mvc.perform(get("/api/health").with(request -> { request.setRemoteAddr("192.0.2.10"); return request; }))
                .andExpect(status().isOk());
    }

    @Test
    void authenticationLimitExpiresAndStorageIsBounded() {
        var milliseconds = new java.util.concurrent.atomic.AtomicLong(1_000);
        var clock = new java.time.Clock() {
            public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
            public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
            public java.time.Instant instant() { return java.time.Instant.ofEpochMilli(milliseconds.get()); }
        };
        var limiter = new AuthRateLimitFilter(2, 1, clock);
        org.assertj.core.api.Assertions.assertThat(limiter.retryAfter("first")).isZero();
        org.assertj.core.api.Assertions.assertThat(limiter.retryAfter("first")).isZero();
        org.assertj.core.api.Assertions.assertThat(limiter.retryAfter("first")).isEqualTo(60);
        org.assertj.core.api.Assertions.assertThat(limiter.retryAfter("second")).isEqualTo(60);
        milliseconds.addAndGet(60_000);
        org.assertj.core.api.Assertions.assertThat(limiter.retryAfter("second")).isZero();
    }
}