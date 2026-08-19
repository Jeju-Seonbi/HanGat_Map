package com.example.hangat.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.LocalDateTime;

/** 생성·수정 시각 공통 컬럼 (Nexus 컨벤션 - 타입만 LocalDateTime으로 현대화) */
@MappedSuperclass
@Getter
public class BaseEntity {

    @Column(name = "create_date", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "update_date", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
