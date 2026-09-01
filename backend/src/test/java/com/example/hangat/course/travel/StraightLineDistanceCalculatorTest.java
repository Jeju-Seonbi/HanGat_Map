package com.example.hangat.course.travel;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StraightLineDistanceCalculatorTest {

    private final StraightLineDistanceCalculator calculator = new StraightLineDistanceCalculator();

    @Test
    void sameCoordinateHasZeroDistance() {
        assertThat(calculator.calculateKilometers(33.4581, 126.9425, 33.4581, 126.9425))
                .hasValue(0.0);
    }

    @Test
    void twoJejuCoordinatesHavePositiveDistance() {
        OptionalDouble distance = calculator.calculateKilometers(
                33.4581, 126.9425,
                33.4913, 126.8114);

        assertThat(distance).isPresent();
        assertThat(distance.getAsDouble()).isPositive();
    }

    @Test
    void distanceIsSymmetric() {
        double forward = calculator.calculateKilometers(
                33.4581, 126.9425, 33.4913, 126.8114).orElseThrow();
        double backward = calculator.calculateKilometers(
                33.4913, 126.8114, 33.4581, 126.9425).orElseThrow();

        assertThat(forward).isEqualTo(backward);
    }

    @Test
    void missingOrNonFiniteCoordinateReturnsEmpty() {
        assertThat(calculator.calculateKilometers(null, 126.9, 33.4, 126.8)).isEmpty();
        assertThat(calculator.calculateKilometers(33.4, null, 33.4, 126.8)).isEmpty();
        assertThat(calculator.calculateKilometers(Double.NaN, 126.9, 33.4, 126.8)).isEmpty();
        assertThat(calculator.calculateKilometers(33.4, Double.POSITIVE_INFINITY, 33.4, 126.8)).isEmpty();
    }

    @Test
    void latitudeOutsideValidRangeReturnsEmpty() {
        assertThat(calculator.calculateKilometers(90.0001, 126.9, 33.4, 126.8)).isEmpty();
        assertThat(calculator.calculateKilometers(33.4, 126.9, -90.0001, 126.8)).isEmpty();
    }

    @Test
    void longitudeOutsideValidRangeReturnsEmpty() {
        assertThat(calculator.calculateKilometers(33.4, 180.0001, 33.4, 126.8)).isEmpty();
        assertThat(calculator.calculateKilometers(33.4, 126.9, 33.4, -180.0001)).isEmpty();
    }

    @Test
    void tenAndTwentyKilometerRadiusChecksAreInclusive() {
        double tenKilometerLatitudeDelta = latitudeDeltaForKilometers(10.0);
        double twentyKilometerLatitudeDelta = latitudeDeltaForKilometers(20.0);

        assertThat(calculator.isWithinKilometers(0.0, 0.0, tenKilometerLatitudeDelta, 0.0, 10.0)).isTrue();
        assertThat(calculator.isWithinKilometers(0.0, 0.0, latitudeDeltaForKilometers(10.01), 0.0, 10.0)).isFalse();
        assertThat(calculator.isWithinKilometers(0.0, 0.0, twentyKilometerLatitudeDelta, 0.0, 20.0)).isTrue();
        assertThat(calculator.isWithinKilometers(0.0, 0.0, latitudeDeltaForKilometers(20.01), 0.0, 20.0)).isFalse();
    }

    @Test
    void invalidRadiusIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                calculator.isWithinKilometers(33.4, 126.9, 33.5, 126.8, -1.0));
        assertThatIllegalArgumentException().isThrownBy(() ->
                calculator.isWithinKilometers(33.4, 126.9, 33.5, 126.8, Double.NaN));
    }

    private double latitudeDeltaForKilometers(double distanceKm) {
        return Math.toDegrees(distanceKm / StraightLineDistanceCalculator.EARTH_MEAN_RADIUS_KM);
    }
}
