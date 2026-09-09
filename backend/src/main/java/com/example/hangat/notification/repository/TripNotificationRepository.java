package com.example.hangat.notification.repository;

import com.example.hangat.notification.model.entity.TripNotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/** 여행 확정 / 알림 수신 설정의 일반 JPA 조회·저장. 잠금 조회는 전용 저장소가 담당한다. */
public interface TripNotificationRepository extends JpaRepository<TripNotificationSettings, Long> {
}
