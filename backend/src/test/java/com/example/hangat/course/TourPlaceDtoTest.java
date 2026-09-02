package com.example.hangat.course;

import com.example.hangat.course.model.TourPlaceDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TourPlaceDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsAllTourApiCategoryLevelsWithoutChangingExistingCategoryField() throws Exception {
        String responseItem = """
                {
                  "contentid": "126508",
                  "title": "제주 관광지",
                  "cat1": "A01",
                  "cat2": "A0101",
                  "cat3": "A01010100"
                }
                """;

        TourPlaceDto place = objectMapper.readValue(responseItem, TourPlaceDto.class);

        assertThat(place.getCategory()).isEqualTo("A01");
        assertThat(place.getCategory2()).isEqualTo("A0101");
        assertThat(place.getCategory3()).isEqualTo("A01010100");
    }
}
