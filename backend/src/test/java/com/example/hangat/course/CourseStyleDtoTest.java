package com.example.hangat.course;

import com.example.hangat.course.model.CourseStyleDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourseStyleDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsDecimalWeightFromFrontendContract() throws Exception {
        CourseStyleDto style = objectMapper.readValue(
                """
                {
                  "tag_id": 1,
                  "code": "NATURE",
                  "name": "자연",
                  "weight": 0.7500
                }
                """,
                CourseStyleDto.class
        );

        assertThat(style.getWeight()).isEqualByComparingTo("0.7500");
    }
}
