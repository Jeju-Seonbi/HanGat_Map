package com.example.hangat.course.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiPropertiesTest {

    @Test
    void usesGeminiSpecificTimeoutDefaults() {
        GeminiProperties properties = new GeminiProperties(
                "https://generativelanguage.googleapis.com/v1beta",
                "test-key",
                "gemini-3.6-flash",
                null,
                null);

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void preservesExplicitTimeoutOverrides() {
        GeminiProperties properties = new GeminiProperties(
                "https://generativelanguage.googleapis.com/v1beta",
                "test-key",
                "gemini-3.6-flash",
                Duration.ofSeconds(5),
                Duration.ofSeconds(30));

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
