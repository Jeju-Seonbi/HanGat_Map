package com.example.hangat.course.ai;

import org.springframework.stereotype.Service;

@Service
public class CourseAiGenerationService {

    private final CourseAiProvider provider;
    private final CourseAiResultValidator validator;

    public CourseAiGenerationService(
            CourseAiProvider provider,
            CourseAiResultValidator validator
    ) {
        this.provider = provider;
        this.validator = validator;
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
        }

        CourseAiResultDto corrected = provider.generateCorrection(
                input,
                result,
                validationFailure.getCode(),
                validationFailure.getMessage()
        );
        validator.validate(input, corrected);
        return corrected;
    }
}
