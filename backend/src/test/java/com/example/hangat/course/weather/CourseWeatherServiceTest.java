package com.example.hangat.course.weather;

import com.example.hangat.domain.weather.WeatherClient;
import com.example.hangat.domain.weather.model.ShortTermItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseWeatherServiceTest {

    private final WeatherClient weatherClient = mock(WeatherClient.class);
    private final CourseWeatherService service = new CourseWeatherService(weatherClient);

    @Test
    void requestsTheProvidedRegionGridAndReturnsOnlyTheRequestedDate() {
        when(weatherClient.fetchShortTerm("20260826", "0500", 55, 38)).thenReturn(List.of(
                new ShortTermItem("TMP", "20260827", "0900", "25"),
                new ShortTermItem("TMP", "20260828", "0900", "26")));

        List<CourseWeatherDto> result = service.getShortTermWeather(
                "20260826", "0500", new KmaGridPoint(55, 38), LocalDate.of(2026, 8, 27));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).temperature()).isEqualByComparingTo("25");
        verify(weatherClient).fetchShortTerm("20260826", "0500", 55, 38);
    }

    @Test
    void rejectsMissingGridOrForecastDate() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.getShortTermWeather(
                "20260826", "0500", null, LocalDate.of(2026, 8, 27)));
        assertThatIllegalArgumentException().isThrownBy(() -> service.getShortTermWeather(
                "20260826", "0500", new KmaGridPoint(55, 38), null));
    }

    @Test
    void gridCoordinatesMustBePositive() {
        assertThatIllegalArgumentException().isThrownBy(() -> new KmaGridPoint(0, 38));
        assertThatIllegalArgumentException().isThrownBy(() -> new KmaGridPoint(55, 0));
    }
}
