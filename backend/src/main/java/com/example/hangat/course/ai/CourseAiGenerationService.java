package com.example.hangat.course.ai;

import org.springframework.stereotype.Service;

@Service
public class CourseAiGenerationService {

    private final CourseAiProvider provider;
    private final CourseAiResultValidator validator;
    private final DeterministicCourseFallback fallback;

    @org.springframework.beans.factory.annotation.Autowired
    public CourseAiGenerationService(
            CourseAiProvider provider,
            CourseAiResultValidator validator,
            DeterministicCourseFallback fallback
    ) {
        this.provider = provider;
        this.validator = validator;
        this.fallback = fallback;
    }

    public CourseAiGenerationService(CourseAiProvider provider, CourseAiResultValidator validator) {
        this(provider, validator, new DeterministicCourseFallback());
    }

    public CourseAiResultDto generate(CourseAiInputDto input) {
        CourseAiResultDto result = null;
        CourseAiValidationException validationFailure;
        try {
            result = provider.generate(input);
            validator.validate(input, result);
            return result;
        } catch (CourseAiValidationException exception) {
            validationFailure = exception;
        } catch (CourseAiException providerFailure) {
            return validatedFallback(input);
        }

        try {
            CourseAiResultDto corrected = provider.generateCorrection(
                    input, result, validationFailure.getCode(), validationFailure.getMessage());
            validator.validate(input, corrected);
            return corrected;
        } catch (CourseAiException finalFailure) {
            return validatedFallback(input);
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
