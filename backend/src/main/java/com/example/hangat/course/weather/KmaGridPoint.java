package com.example.hangat.course.weather;

public record KmaGridPoint(int nx, int ny) {

    public KmaGridPoint {
        if (nx <= 0 || ny <= 0) {
            throw new IllegalArgumentException("기상청 격자 좌표는 1 이상이어야 합니다.");
        }
    }
}
