package com.example.hangat.course.ai;

import org.springframework.stereotype.Service;
import java.util.UUID;
import static com.example.hangat.course.ai.CourseAiDiagnosticRecorder.CallKind.*;
import static com.example.hangat.course.ai.CourseAiDiagnosticRecorder.Outcome.*;

@Service
public class CourseAiGenerationService {

    private final CourseAiProvider provider;
    private final CourseAiResultValidator validator;
    private final DeterministicCourseFallback fallback;
    private final CourseAiDiagnosticRecorder diagnostics;

    @org.springframework.beans.factory.annotation.Autowired
    public CourseAiGenerationService(
            CourseAiProvider provider,
            CourseAiResultValidator validator,
            DeterministicCourseFallback fallback,
            CourseAiDiagnosticRecorder diagnostics
    ) {
        this.provider = provider;
        this.validator = validator;
        this.fallback = fallback;
        this.diagnostics = diagnostics;
    }

    public CourseAiGenerationService(CourseAiProvider provider, CourseAiResultValidator validator,
                                     DeterministicCourseFallback fallback) {
        this(provider, validator, fallback, null);
    }

    public CourseAiGenerationService(CourseAiProvider provider, CourseAiResultValidator validator) {
        this(provider, validator, new DeterministicCourseFallback());
    }

    public CourseAiResultDto generate(CourseAiInputDto input) {
        UUID traceId = UUID.randomUUID();
        UUID initialFailureId;
        CourseAiResultDto result = null;
        CourseAiValidationException validationFailure;
        try {
            result = provider.generate(input);
            validator.validate(input, result);
            return result;
        } catch (CourseAiValidationException exception) {
            validationFailure = exception;
            initialFailureId = record(traceId, INITIAL, exception);
        } catch (CourseAiException providerFailure) {
            return recordedFallback(input, record(traceId, INITIAL, providerFailure));
        }

        try {
            CourseAiResultDto corrected = provider.generateCorrection(
                    input, result, validationFailure.getCode(), validationFailure.getMessage());
            validator.validate(input, corrected);
            complete(initialFailureId, CORRECTION_SUCCEEDED);
            return corrected;
        } catch (CourseAiException finalFailure) {
            return recordedFallback(input, initialFailureId, record(traceId, CORRECTION, finalFailure));
        }
    }

    private UUID record(UUID traceId, CourseAiDiagnosticRecorder.CallKind kind, CourseAiException failure) {
        return diagnostics == null ? null : diagnostics.record(traceId, CourseAiDiagnosticContext.jobId(), kind,
                failure.getDiagnostic() == null ? CourseAiDiagnostic.unknown(failure) : failure.getDiagnostic());
    }

    private void complete(UUID id, CourseAiDiagnosticRecorder.Outcome outcome) {
        if (diagnostics != null) diagnostics.complete(id, outcome);
    }

    private CourseAiResultDto recordedFallback(CourseAiInputDto input, UUID... failureIds) {
        boolean succeeded = false;
        try {
            CourseAiResultDto result = validatedFallback(input);
            succeeded = true;
            return result;
        } finally {
            for (UUID id : failureIds) complete(id, succeeded ? FALLBACK_SUCCEEDED : FALLBACK_FAILED);
        }
    }

    private CourseAiResultDto validatedFallback(CourseAiInputDto input) {
        try {
            CourseAiResultDto deterministic = fallback.generate(input);
            validator.validate(input, deterministic);
            return deterministic;
        } catch (CourseAiException failure) {
            if (failure.getFailureType() == CourseAiFailureType.TEMPORARILY_UNAVAILABLE) throw failure;
            throw new CourseAiException(CourseAiFailureType.TEMPORARILY_UNAVAILABLE,
                    "필수 조건을 유지한 대체 코스를 구성할 수 없습니다.", failure);
        }
    }
}
