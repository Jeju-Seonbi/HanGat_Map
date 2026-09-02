package com.example.hangat.course.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CongestionLevel {

    QUIET("쾌적"),
    NORMAL("보통"),
    CROWDED("혼잡");

    private final String label;
}
