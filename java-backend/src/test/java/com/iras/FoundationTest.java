package com.iras;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class FoundationTest {
    @Test
    void passwordHashesAreSaltedAndVerifiable() {
        var encoder = new BCryptPasswordEncoder();
        String first = encoder.encode("synthetic-test-password");
        String second = encoder.encode("synthetic-test-password");
        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches("synthetic-test-password", first)).isTrue();
        assertThat(encoder.matches("wrong-password", first)).isFalse();
    }
}