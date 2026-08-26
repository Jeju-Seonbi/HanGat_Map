package com.example.hangat.course.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.UnknownContentTypeException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class GeminiCourseAiProvider implements CourseAiProvider {

    private static final String JSON_MIME_TYPE = "application/json";

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final CourseAiPrompt prompt;
    private final ObjectMapper objectMapper;

    public GeminiCourseAiProvider(
            GeminiProperties properties,
            CourseAiPrompt prompt,
            ObjectMapper objectMapper
    ) {
        this(createRestClient(properties), properties, prompt, objectMapper);
    }

    GeminiCourseAiProvider(
            RestClient restClient,
            GeminiProperties properties,
            CourseAiPrompt prompt,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.prompt = prompt;
        this.objectMapper = objectMapper;
    }

    private static RestClient createRestClient(GeminiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public CourseAiResultDto generate(CourseAiInputDto input) {
        validateConfiguration();
        GeminiCallPhase phase = GeminiCallPhase.BUILD_REQUEST;

        try {
            GeminiGenerateRequest request = buildRequest(input);
            phase = GeminiCallPhase.HTTP_EXCHANGE;
            String responseBody = restClient.post()
                    .uri("/models/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .body(request)
                    .retrieve()
                    .body(String.class);

            phase = GeminiCallPhase.PARSE_ENVELOPE;
            GeminiGenerateResponse response = parseResponseEnvelope(responseBody);
            phase = GeminiCallPhase.PARSE_RESULT;
            return parseResult(response);
        } catch (CourseAiException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            ProviderErrorDetails details = providerErrorDetails(exception.getResponseBodyAsString());
            if (exception.getStatusCode().value() == 429) {
                throw new CourseAiException(
                        CourseAiFailureType.RATE_LIMIT,
                        "Gemini API 요청 한도를 초과했습니다. PHASE=" + phase + ", "
                                + diagnosticContext(exception.getStatusCode(), details)
                );
            }
            throw new CourseAiException(
                    CourseAiFailureType.PROVIDER_ERROR,
                    providerErrorMessage(exception.getStatusCode(), details, phase)
            );
        } catch (ResourceAccessException exception) {
            throw new CourseAiException(
                    CourseAiFailureType.PROVIDER_ERROR,
                    "Gemini API 통신에 실패했습니다. PHASE=" + phase
                            + ", NETWORK=" + networkFailureType(exception)
                            + ", HOST=" + endpointHost()
                            + ", MODEL=" + safeToken(properties.model()),
                    exception
            );
        } catch (Exception exception) {
            throw new CourseAiException(
                    CourseAiFailureType.PROVIDER_ERROR,
                    "Gemini API 호출에 실패했습니다. PHASE="
                            + diagnosticPhase(phase, exception)
                            + ", " + exceptionTypeDiagnostics(exception)
                            + ", HOST=" + endpointHost()
                            + ", MODEL=" + safeToken(properties.model())
            );
        }
    }

    GeminiGenerateRequest buildRequest(CourseAiInputDto input) {
        return new GeminiGenerateRequest(
                new GeminiContent(null, List.of(new GeminiPart(prompt.systemInstruction()))),
                List.of(new GeminiContent("user", List.of(new GeminiPart(prompt.userPrompt(input))))),
                new GeminiGenerationConfig(JSON_MIME_TYPE, responseJsonSchema())
        );
    }

    CourseAiResultDto parseResult(GeminiGenerateResponse response) {
        String text = responseText(response);
        try {
            return objectMapper.readValue(text, CourseAiResultDto.class);
        } catch (JsonProcessingException exception) {
            throw new CourseAiException(
                    CourseAiFailureType.INVALID_RESPONSE,
                    "Gemini 응답을 AI 코스 결과로 변환할 수 없습니다.",
                    exception
            );
        }
    }

    private GeminiGenerateResponse parseResponseEnvelope(String responseBody) {
        if (isBlank(responseBody)) {
            throw invalidResponse();
        }
        try {
            return objectMapper.readValue(responseBody, GeminiGenerateResponse.class);
        } catch (JsonProcessingException exception) {
            throw new CourseAiException(
                    CourseAiFailureType.INVALID_RESPONSE,
                    "Gemini API 응답 JSON이 유효하지 않습니다.",
                    exception
            );
        }
    }

    private void validateConfiguration() {
        if (isBlank(properties.baseUrl())
                || isBlank(properties.model())
                || isBlank(properties.apiKey())) {
            throw new CourseAiException(
                    CourseAiFailureType.CONFIGURATION_ERROR,
                    "Gemini API 설정이 완료되지 않았습니다."
            );
        }
    }

    private String responseText(GeminiGenerateResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw invalidResponse();
        }
        GeminiCandidate candidate = response.candidates().get(0);
        if (candidate == null || candidate.content() == null
                || candidate.content().parts() == null || candidate.content().parts().isEmpty()
                || candidate.content().parts().get(0) == null
                || isBlank(candidate.content().parts().get(0).text())) {
            throw invalidResponse();
        }
        return candidate.content().parts().get(0).text();
    }

    private CourseAiException invalidResponse() {
        return new CourseAiException(
                CourseAiFailureType.INVALID_RESPONSE,
                "Gemini API가 유효한 구조화 응답을 반환하지 않았습니다."
        );
    }

    private String providerErrorMessage(
            HttpStatusCode status,
            ProviderErrorDetails details,
            GeminiCallPhase phase
    ) {
        String summary = status.is5xxServerError()
                ? "Gemini API 서버 오류가 발생했습니다."
                : "Gemini API 요청이 거부되었습니다.";
        return summary + " PHASE=" + phase + ", " + diagnosticContext(status, details);
    }

    private ProviderErrorDetails providerErrorDetails(String responseBody) {
        if (isBlank(responseBody)) {
            return ProviderErrorDetails.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root == null) {
                return ProviderErrorDetails.empty();
            }
            JsonNode error = root.path("error");
            String status = safeToken(error.path("status").asText(null));
            String reason = null;
            JsonNode details = error.path("details");
            if (details.isArray()) {
                for (JsonNode detail : details) {
                    String candidate = safeToken(detail.path("reason").asText(null));
                    if (candidate != null) {
                        reason = candidate;
                        break;
                    }
                }
            }
            return new ProviderErrorDetails(status, reason);
        } catch (JsonProcessingException ignored) {
            return ProviderErrorDetails.empty();
        }
    }

    private String diagnosticContext(HttpStatusCode status, ProviderErrorDetails details) {
        StringBuilder diagnostic = new StringBuilder("HTTP_STATUS=")
                .append(status.value());
        if (details.status() != null) {
            diagnostic.append(", GOOGLE_STATUS=").append(details.status());
        }
        if (details.reason() != null) {
            diagnostic.append(", GOOGLE_REASON=").append(details.reason());
        }
        return diagnostic
                .append(", HOST=").append(endpointHost())
                .append(", MODEL=").append(safeToken(properties.model()))
                .toString();
    }

    private String endpointHost() {
        try {
            String host = URI.create(properties.baseUrl()).getHost();
            return host == null ? "unknown" : safeToken(host);
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private String networkFailureType(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof UnknownHostException) {
                return "DNS";
            }
            if (current instanceof HttpConnectTimeoutException) {
                return "CONNECT_TIMEOUT";
            }
            if (current instanceof HttpTimeoutException) {
                return "READ_TIMEOUT";
            }
            if (current instanceof SocketTimeoutException) {
                String message = current.getMessage();
                return message != null && message.toLowerCase(Locale.ROOT).contains("connect")
                        ? "CONNECT_TIMEOUT"
                        : "READ_TIMEOUT";
            }
            if (current instanceof ConnectException) {
                return "CONNECT";
            }
            current = current.getCause();
        }
        return "IO";
    }

    private String safeToken(String value) {
        if (isBlank(value)) {
            return null;
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 100));
    }

    private String safeExceptionType(Throwable exception) {
        String simpleName = exception.getClass().getSimpleName();
        return isBlank(simpleName) ? "UnknownException" : safeToken(simpleName);
    }

    private String exceptionTypeDiagnostics(Exception exception) {
        StringBuilder diagnostics = new StringBuilder("EXCEPTION_TYPE=")
                .append(safeExceptionType(exception));
        Throwable directCause = exception.getCause();
        if (directCause == null || directCause == exception) {
            return diagnostics.toString();
        }

        diagnostics.append(", CAUSE_TYPE=").append(safeExceptionType(directCause));
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(exception);
        Throwable rootCause = directCause;
        int depth = 0;
        while (rootCause.getCause() != null
                && visited.add(rootCause)
                && depth++ < 32) {
            Throwable next = rootCause.getCause();
            if (visited.contains(next)) {
                break;
            }
            rootCause = next;
        }
        if (rootCause != directCause) {
            diagnostics.append(", ROOT_CAUSE_TYPE=")
                    .append(safeExceptionType(rootCause));
        }
        return diagnostics.toString();
    }

    private GeminiCallPhase diagnosticPhase(GeminiCallPhase phase, Exception exception) {
        if (hasCause(exception, HttpMessageNotWritableException.class)) {
            return GeminiCallPhase.REQUEST_SERIALIZATION;
        }
        if (exception instanceof UnknownContentTypeException
                || hasCause(exception, HttpMessageNotReadableException.class)
                || (exception.getClass() == RestClientException.class
                && hasCause(exception, IOException.class))) {
            return GeminiCallPhase.RESPONSE_EXTRACTION;
        }
        return phase;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;
        int depth = 0;
        while (current != null && visited.add(current) && depth++ < 32) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Map<String, Object> responseJsonSchema() {
        Map<String, Object> item = Map.of(
                "type", "object",
                "properties", Map.of(
                        "candidateId", Map.of("type", "string"),
                        "startTime", Map.of(
                                "type", "string",
                                "description", "24시간제 HH:mm 형식의 방문 시작 시각"),
                        "recommendationReason", Map.of("type", "string")
                ),
                "required", List.of("candidateId", "startTime", "recommendationReason")
        );
        Map<String, Object> day = Map.of(
                "type", "object",
                "properties", Map.of(
                        "date", Map.of("type", "string", "format", "date"),
                        "items", Map.of("type", "array", "items", item)
                ),
                "required", List.of("date", "items")
        );
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "contractVersion", Map.of("type", "string"),
                        "days", Map.of("type", "array", "items", day)
                ),
                "required", List.of("contractVersion", "days")
        );
    }

    record GeminiGenerateRequest(
            GeminiContent systemInstruction,
            List<GeminiContent> contents,
            GeminiGenerationConfig generationConfig
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GeminiContent(String role, List<GeminiPart> parts) {
    }

    record GeminiPart(String text) {
    }

    record GeminiGenerationConfig(
            String responseMimeType,
            Map<String, Object> responseJsonSchema
    ) {
    }

    record GeminiGenerateResponse(List<GeminiCandidate> candidates) {
    }

    record GeminiCandidate(GeminiContent content) {
    }

    private record ProviderErrorDetails(String status, String reason) {
        private static ProviderErrorDetails empty() {
            return new ProviderErrorDetails(null, null);
        }
    }

    private enum GeminiCallPhase {
        BUILD_REQUEST,
        REQUEST_SERIALIZATION,
        HTTP_EXCHANGE,
        RESPONSE_EXTRACTION,
        PARSE_ENVELOPE,
        PARSE_RESULT
    }
}
