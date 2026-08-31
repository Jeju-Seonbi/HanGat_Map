package com.example.hangat.course;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KtoPlaceCategoryResolverTest {

    @Test
    void mapsTouristCategoriesForPersistence() {
        assertThat(KtoPlaceCategoryResolver.resolve("A01", null))
                .isEqualTo("TOURIST");
        assertThat(KtoPlaceCategoryResolver.resolve("A02", null))
                .isEqualTo("TOURIST");
        assertThat(KtoPlaceCategoryResolver.resolve("A03", null))
                .isEqualTo("TOURIST");
    }

    @Test
    void mapsCafeCategoryBeforeGeneralFoodCategory() {
        assertThat(KtoPlaceCategoryResolver.resolve("A05", "A05020900"))
                .isEqualTo("CAFE");
    }

    @Test
    void mapsOtherFoodCategoryForPersistence() {
        assertThat(KtoPlaceCategoryResolver.resolve("A05", "A05020100"))
                .isEqualTo("FOOD");
    }

    @Test
    void mapsLodgingCategoryForPersistence() {
        assertThat(KtoPlaceCategoryResolver.resolve("B02", null))
                .isEqualTo("LODGING");
    }
}
