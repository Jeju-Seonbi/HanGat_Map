package com.example.hangat.course.ai;

import com.example.hangat.course.ai.CourseAiInputDto.TripConditionDto;
import com.example.hangat.course.ai.CourseAiInputDto.UserPreferencesDto;
import com.example.hangat.course.model.Transport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiCourseAiProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void sendsStructuredJsonRequestAndParsesResultWithoutCredentialInPrompt() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test/v1beta");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiCourseAiProvider provider = provider(builder.build(), "test-secret");

        server.expect(once(), requestTo("http://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-secret"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.type").value("object"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("test-secret"))))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"contractVersion\\":\\"1.0\\",\\"days\\":[]}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.generate(input()).days()).isEmpty();
        server.verify();
    }

    @Test
    void mapsRateLimitServerErrorInvalidJsonAndMissingKey() {
        assertFailureForStatus(429, CourseAiFailureType.RATE_LIMIT);
        assertFailureForStatus(500, CourseAiFailureType.PROVIDER_ERROR);

        RestClient.Builder invalidBuilder = RestClient.builder().baseUrl("http://gemini.test/v1beta");
        MockRestServiceServer invalidServer = MockRestServiceServer.bindTo(invalidBuilder).build();
        GeminiCourseAiProvider invalidProvider = provider(invalidBuilder.build(), "test-secret");
        invalidServer.expect(once(), requestTo("http://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"not-json"}]}}]}
                        """, MediaType.APPLICATION_JSON));
        assertFailure(invalidProvider, CourseAiFailureType.INVALID_RESPONSE);

        RestClient.Builder malformedBuilder = RestClient.builder().baseUrl("http://gemini.test/v1beta");
        MockRestServiceServer malformedServer = MockRestServiceServer.bindTo(malformedBuilder).build();
        GeminiCourseAiProvider malformedProvider = provider(malformedBuilder.build(), "test-secret");
        malformedServer.expect(once(), requestTo("http://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertFailure(malformedProvider, CourseAiFailureType.INVALID_RESPONSE);

        assertFailure(provider(RestClient.create(), ""), CourseAiFailureType.CONFIGURATION_ERROR);
    }

    private void assertFailureForStatus(int status, CourseAiFailureType expected) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test/v1beta");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiCourseAiProvider provider = provider(builder.build(), "test-secret");
        server.expect(once(), requestTo("http://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andRespond(status == 500
                        ? withServerError()
                        : withStatus(org.springframework.http.HttpStatus.valueOf(status)));
        assertFailure(provider, expected);
    }

    private void assertFailure(GeminiCourseAiProvider provider, CourseAiFailureType expected) {
        assertThatThrownBy(() -> provider.generate(input()))
                .isInstanceOfSatisfying(CourseAiException.class,
                        exception -> assertThat(exception.getFailureType()).isEqualTo(expected));
    }

    private GeminiCourseAiProvider provider(RestClient restClient, String apiKey) {
        GeminiProperties properties = new GeminiProperties(
                "http://gemini.test/v1beta", apiKey, "gemini-test",
                Duration.ofSeconds(1), Duration.ofSeconds(1));
        return new GeminiCourseAiProvider(
                restClient, properties, new CourseAiPrompt(objectMapper), objectMapper);
    }

    private CourseAiInputDto input() {
        return new CourseAiInputDto(
                "1.0",
                new TripConditionDto(
                        LocalDate.parse("2026-08-27"), LocalDate.parse("2026-08-29"),
                        2, 500000, Transport.RENTAL_CAR),
                new UserPreferencesDto(List.of(), List.of(), List.of(), List.of(), null),
                List.of(), List.of(), null);
    }
}
