package com.example.hangat.course.ai;

import com.example.hangat.course.ai.CourseAiInputDto.CandidateFactDto;
import com.example.hangat.course.ai.CourseAiInputDto.CongestionFactDto;
import com.example.hangat.course.ai.CourseAiInputDto.GenerationMetadataDto;
import com.example.hangat.course.ai.CourseAiInputDto.PlaceConstraintDto;
import com.example.hangat.course.ai.CourseAiInputDto.PlaceIdentityDto;
import com.example.hangat.course.ai.CourseAiInputDto.SelectedRegionDto;
import com.example.hangat.course.ai.CourseAiInputDto.SelectedStyleDto;
import com.example.hangat.course.ai.CourseAiInputDto.TourCategoryDto;
import com.example.hangat.course.ai.CourseAiInputDto.TravelFactDto;
import com.example.hangat.course.ai.CourseAiInputDto.TripConditionDto;
import com.example.hangat.course.ai.CourseAiInputDto.UserPreferencesDto;
import com.example.hangat.course.model.CongestionLevel;
import com.example.hangat.course.model.GenerationReason;
import com.example.hangat.course.model.PreferenceType;
import com.example.hangat.course.model.Transport;
import com.example.hangat.course.travel.DistanceCalculationMethod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiCourseAiProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void sendsProductionSmokeFixtureThroughMessageConverterWithoutCredential() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test/v1beta");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiCourseAiProvider provider = provider(builder.build(), "test-secret");

        server.expect(once(), requestTo("http://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-secret"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").isNotEmpty())
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.type").value("object"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("test-secret"))))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(
                        org.hamcrest.Matchers.containsString("\"startDate\":\"2026-09-10\"")))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(
                        org.hamcrest.Matchers.containsString("\"fixedTime\":\"09:00:00\"")))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"contractVersion\\":\\"1.0\\",\\"days\\":[]}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.generate(smokeInput()).days()).isEmpty();
        server.verify();
    }

    @Test
    void serializesProductionSmokeBuildRequestLocally() throws Exception {
        GeminiCourseAiProvider provider = provider(RestClient.create(), "test-secret");

        String requestJson = objectMapper.writeValueAsString(provider.buildRequest(smokeInput()));
        JsonNode request = objectMapper.readTree(requestJson);
        JsonNode input = objectMapper.readTree(
                request.path("contents").path(0).path("parts").path(0).path("text").asText());

        assertThat(request.path("systemInstruction").path("parts").path(0).path("text").asText())
                .isNotBlank();
        assertThat(request.path("generationConfig").path("responseMimeType").asText())
                .isEqualTo("application/json");
        assertThat(request.path("generationConfig").path("responseJsonSchema").path("type").asText())
                .isEqualTo("object");
        assertThat(input.path("tripCondition").path("startDate").asText())
                .isEqualTo("2026-09-10");
        assertThat(input.path("userPreferences").path("requiredPlaces")
                .path(0).path("fixedTime").asText()).isEqualTo("09:00:00");
        assertThat(input.path("candidates").size()).isEqualTo(3);
        assertThat(requestJson).doesNotContain("test-secret");
    }

    @Test
    void mapsBadRequestWithSafeDiagnostics() {
        assertFailureForStatus(
                400, "INVALID_ARGUMENT", "INVALID_JSON_PAYLOAD",
                CourseAiFailureType.PROVIDER_ERROR);
    }

    @Test
    void mapsForbiddenWithSafeDiagnostics() {
        assertFailureForStatus(
                403, "PERMISSION_DENIED", "API_KEY_INVALID",
                CourseAiFailureType.PROVIDER_ERROR);
    }

    @Test
    void mapsNotFoundWithSafeDiagnostics() {
        assertFailureForStatus(
                404, "NOT_FOUND", "MODEL_NOT_FOUND",
                CourseAiFailureType.PROVIDER_ERROR);
    }

    @Test
    void mapsRateLimitWithoutChangingFailureType() {
        assertFailureForStatus(
                429, "RESOURCE_EXHAUSTED", "RATE_LIMIT_EXCEEDED",
                CourseAiFailureType.RATE_LIMIT);
    }

    @Test
    void mapsServerErrorWithSafeDiagnostics() {
        assertFailureForStatus(
                500, "INTERNAL", "BACKEND_ERROR",
                CourseAiFailureType.PROVIDER_ERROR);
    }

    @Test
    void mapsMalformedSuccessEnvelopeToInvalidResponse() {

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
    }

    @Test
    void mapsMissingApiKeyToConfigurationError() {
        assertFailure(provider(RestClient.create(), ""), CourseAiFailureType.CONFIGURATION_ERROR);
    }

    @Test
    void mapsUnhandledExceptionWithSafeTypeOnly() {
        RestClient restClient = mock(RestClient.class);
        when(restClient.post()).thenThrow(new RestClientException("sensitive request detail"));

        assertThatThrownBy(() -> provider(restClient, "test-secret").generate(input()))
                .isInstanceOfSatisfying(CourseAiException.class, exception -> {
                    assertThat(exception.getFailureType())
                            .isEqualTo(CourseAiFailureType.PROVIDER_ERROR);
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getMessage())
                            .contains("PHASE=HTTP_EXCHANGE")
                            .contains("EXCEPTION_TYPE=RestClientException")
                            .doesNotContain("CAUSE_TYPE=")
                            .doesNotContain("ROOT_CAUSE_TYPE=")
                            .contains("HOST=gemini.test")
                            .contains("MODEL=gemini-test")
                            .doesNotContain("sensitive request detail")
                            .doesNotContain("test-secret");
                });
    }

    @Test
    void mapsGenericCauseChainWithoutMessagesOrCredential() {
        RestClient restClient = mock(RestClient.class);
        RestClientException failure = new RestClientException(
                "sensitive top detail",
                new HttpMessageNotWritableException(
                        "sensitive conversion detail",
                        new IllegalArgumentException("sensitive root detail")));
        when(restClient.post()).thenThrow(failure);

        assertThatThrownBy(() -> provider(restClient, "test-secret").generate(input()))
                .isInstanceOfSatisfying(CourseAiException.class, exception -> {
                    assertThat(exception.getFailureType())
                            .isEqualTo(CourseAiFailureType.PROVIDER_ERROR);
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getMessage())
                            .contains("PHASE=REQUEST_SERIALIZATION")
                            .contains("EXCEPTION_TYPE=RestClientException")
                            .contains("CAUSE_TYPE=HttpMessageNotWritableException")
                            .contains("ROOT_CAUSE_TYPE=IllegalArgumentException")
                            .doesNotContain("sensitive top detail")
                            .doesNotContain("sensitive conversion detail")
                            .doesNotContain("sensitive root detail")
                            .doesNotContain("test-secret");
                });
    }

    @Test
    void identifiesResponseExtractionFromRestClientIoCause() {
        RestClient restClient = mock(RestClient.class);
        when(restClient.post()).thenThrow(new RestClientException(
                "sensitive extraction detail", new IOException("sensitive io detail")));

        assertThatThrownBy(() -> provider(restClient, "test-secret").generate(input()))
                .isInstanceOfSatisfying(CourseAiException.class, exception -> {
                    assertThat(exception.getFailureType())
                            .isEqualTo(CourseAiFailureType.PROVIDER_ERROR);
                    assertThat(exception.getMessage())
                            .contains("PHASE=RESPONSE_EXTRACTION")
                            .contains("EXCEPTION_TYPE=RestClientException")
                            .contains("CAUSE_TYPE=IOException")
                            .doesNotContain("sensitive extraction detail")
                            .doesNotContain("sensitive io detail")
                            .doesNotContain("test-secret");
                });
    }

    @Test
    void preservesResourceAccessNetworkDiagnostics() {
        RestClient restClient = mock(RestClient.class);
        when(restClient.post()).thenThrow(new ResourceAccessException(
                "sensitive network detail", new SocketTimeoutException("sensitive timeout detail")));

        assertThatThrownBy(() -> provider(restClient, "test-secret").generate(input()))
                .isInstanceOfSatisfying(CourseAiException.class, exception -> {
                    assertThat(exception.getFailureType())
                            .isEqualTo(CourseAiFailureType.PROVIDER_ERROR);
                    assertThat(exception.getMessage())
                            .contains("PHASE=HTTP_EXCHANGE")
                            .contains("NETWORK=READ_TIMEOUT")
                            .doesNotContain("sensitive network detail")
                            .doesNotContain("sensitive timeout detail")
                            .doesNotContain("test-secret");
                });
    }

    @Test
    void identifiesBuildRequestPhaseWithoutLeakingMessage() {
        CourseAiPrompt failingPrompt = mock(CourseAiPrompt.class);
        when(failingPrompt.systemInstruction())
                .thenThrow(new IllegalStateException("sensitive build detail"));

        assertThatThrownBy(() -> provider(
                RestClient.create(), "test-secret", failingPrompt).generate(input()))
                .isInstanceOfSatisfying(CourseAiException.class, exception -> {
                    assertThat(exception.getFailureType())
                            .isEqualTo(CourseAiFailureType.PROVIDER_ERROR);
                    assertThat(exception.getMessage())
                            .contains("PHASE=BUILD_REQUEST")
                            .contains("EXCEPTION_TYPE=IllegalStateException")
                            .doesNotContain("sensitive build detail")
                            .doesNotContain("test-secret");
                });
    }

    private void assertFailureForStatus(
            int status,
            String googleStatus,
            String googleReason,
            CourseAiFailureType expected
    ) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test/v1beta");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiCourseAiProvider provider = provider(builder.build(), "test-secret");
        server.expect(once(), requestTo("http://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"code":%d,"message":"sensitive upstream detail",
                                "status":"%s","details":[{"reason":"%s"}]}}
                                """.formatted(status, googleStatus, googleReason)));

        assertThatThrownBy(() -> provider.generate(input()))
                .isInstanceOfSatisfying(CourseAiException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(expected);
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getMessage())
                            .contains("PHASE=HTTP_EXCHANGE")
                            .contains("HTTP_STATUS=" + status)
                            .contains("GOOGLE_STATUS=" + googleStatus)
                            .contains("GOOGLE_REASON=" + googleReason)
                            .contains("HOST=gemini.test")
                            .contains("MODEL=gemini-test")
                            .doesNotContain("sensitive upstream detail")
                            .doesNotContain("test-secret");
                });
        server.verify();
    }

    private void assertFailure(GeminiCourseAiProvider provider, CourseAiFailureType expected) {
        assertThatThrownBy(() -> provider.generate(input()))
                .isInstanceOfSatisfying(CourseAiException.class,
                        exception -> assertThat(exception.getFailureType()).isEqualTo(expected));
    }

    private GeminiCourseAiProvider provider(RestClient restClient, String apiKey) {
        return provider(restClient, apiKey, new CourseAiPrompt(objectMapper));
    }

    private GeminiCourseAiProvider provider(
            RestClient restClient,
            String apiKey,
            CourseAiPrompt prompt
    ) {
        GeminiProperties properties = new GeminiProperties(
                "http://gemini.test/v1beta", apiKey, "gemini-test",
                Duration.ofSeconds(1), Duration.ofSeconds(1));
        return new GeminiCourseAiProvider(restClient, properties, prompt, objectMapper);
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

    private CourseAiInputDto smokeInput() {
        LocalDate firstDay = LocalDate.of(2026, 9, 10);
        PlaceIdentityDto wantIdentity = new PlaceIdentityDto("want-1", null, null, null);
        PlaceConstraintDto want = new PlaceConstraintDto(
                wantIdentity, "성산일출봉", "제주특별자치도 서귀포시 성산읍",
                33.458, 126.942, "관광지", PreferenceType.WANT,
                firstDay, LocalTime.of(9, 0));
        PlaceConstraintDto avoid = new PlaceConstraintDto(
                new PlaceIdentityDto("avoid-1", null, null, null),
                "금지장소", null, null, null, null, PreferenceType.AVOID, null, null);

        CandidateFactDto wantedCandidate = candidate(
                wantIdentity, "성산일출봉", PreferenceType.WANT, List.of("NATURE"),
                firstDay, new BigDecimal("27.50"), CongestionLevel.QUIET, 33.458, 126.942);
        CandidateFactDto natureCandidate = candidate(
                new PlaceIdentityDto("normal-1", null, null, null),
                "비자림", null, List.of("NATURE"), firstDay,
                new BigDecimal("52.00"), CongestionLevel.NORMAL, 33.491, 126.811);
        CandidateFactDto cafeCandidate = candidate(
                new PlaceIdentityDto("normal-2", null, null, null),
                "월정리 카페거리", null, List.of("CAFE"), firstDay,
                null, null, 33.556, 126.795);

        return new CourseAiInputDto(
                "1.0",
                new TripConditionDto(firstDay, firstDay.plusDays(1), 2, 300000, Transport.RENTAL_CAR),
                new UserPreferencesDto(
                        List.of(new SelectedRegionDto(null, "EAST", "동부")),
                        List.of(
                                new SelectedStyleDto(null, "NATURE", "자연", BigDecimal.ONE),
                                new SelectedStyleDto(null, "CAFE", "카페", BigDecimal.ONE)),
                        List.of(want), List.of(avoid), null),
                List.of(wantedCandidate, natureCandidate, cafeCandidate),
                List.of(new TravelFactDto(
                        "want-1", "성산일출봉", "normal-1", "비자림",
                        new BigDecimal("18.42"), DistanceCalculationMethod.HAVERSINE,
                        null, null, Transport.RENTAL_CAR, null, null)),
                new GenerationMetadataDto(
                        GenerationReason.INITIAL, "smoke-v1", "smoke-request"));
    }

    private CandidateFactDto candidate(
            PlaceIdentityDto identity,
            String name,
            PreferenceType preferenceType,
            List<String> styles,
            LocalDate date,
            BigDecimal rate,
            CongestionLevel level,
            double latitude,
            double longitude
    ) {
        List<CongestionFactDto> congestion = rate == null
                ? List.of()
                : List.of(new CongestionFactDto(date, rate, level));
        return new CandidateFactDto(
                identity, name, "제주특별자치도", latitude, longitude,
                new TourCategoryDto("A01", null, null), "EAST", preferenceType,
                styles, congestion, null);
    }
}
