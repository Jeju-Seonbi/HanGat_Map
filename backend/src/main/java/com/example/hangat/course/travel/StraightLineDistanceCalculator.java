package com.example.hangat.course.travel;

import org.springframework.stereotype.Component;

import java.util.OptionalDouble;

@Component
public class StraightLineDistanceCalculator {

    static final double EARTH_MEAN_RADIUS_KM = 6371.0088;
    private static final double COMPARISON_EPSILON_KM = 1.0e-9;

    public OptionalDouble calculateKilometers(
            Double fromLatitude,
            Double fromLongitude,
            Double toLatitude,
            Double toLongitude
    ) {
        if (!isValidLatitude(fromLatitude)
                || !isValidLongitude(fromLongitude)
                || !isValidLatitude(toLatitude)
                || !isValidLongitude(toLongitude)) {
            return OptionalDouble.empty();
        }

        double fromLatitudeRadians = Math.toRadians(fromLatitude);
        double toLatitudeRadians = Math.toRadians(toLatitude);
        double latitudeDelta = toLatitudeRadians - fromLatitudeRadians;
        double longitudeDelta = Math.toRadians(toLongitude - fromLongitude);

        double haversine = Math.pow(Math.sin(latitudeDelta / 2.0), 2.0)
                + Math.cos(fromLatitudeRadians)
                * Math.cos(toLatitudeRadians)
                * Math.pow(Math.sin(longitudeDelta / 2.0), 2.0);
        double centralAngle = 2.0 * Math.asin(Math.sqrt(Math.min(1.0, haversine)));
        return OptionalDouble.of(EARTH_MEAN_RADIUS_KM * centralAngle);
    }

    public boolean isWithinKilometers(
            Double fromLatitude,
            Double fromLongitude,
            Double toLatitude,
            Double toLongitude,
            double maximumDistanceKm
    ) {
        if (!Double.isFinite(maximumDistanceKm) || maximumDistanceKm < 0.0) {
            throw new IllegalArgumentException("거리 반경은 0 이상의 유한한 값이어야 합니다.");
        }
        OptionalDouble distance = calculateKilometers(
                fromLatitude, fromLongitude, toLatitude, toLongitude);
        return distance.isPresent() && distance.getAsDouble() <= maximumDistanceKm + COMPARISON_EPSILON_KM;
    }

    private boolean isValidLatitude(Double latitude) {
        return latitude != null && Double.isFinite(latitude)
                && latitude >= -90.0 && latitude <= 90.0;
    }

    private boolean isValidLongitude(Double longitude) {
        return longitude != null && Double.isFinite(longitude)
                && longitude >= -180.0 && longitude <= 180.0;
    }
}
