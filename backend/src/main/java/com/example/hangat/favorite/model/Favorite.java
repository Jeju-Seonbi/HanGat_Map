package com.example.hangat.favorite.model;

import com.example.hangat.map.model.entity.Place;
import com.example.hangat.user.model.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 회원이 찜한 장소.
 * 장소 이름이 아닌 DB의 장소 ID로 연결하며, 같은 회원의 중복 찜을 금지한다.
 */
@Entity
@Table(
        name = "favorite_places",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_favorite_user_place",
                columnNames = {"user_id", "place_id"}
        ),
        indexes = {
                @Index(
                        name = "idx_favorite_user_created",
                        columnList = "user_id, created_at, id"
                ),
                @Index(
                        name = "idx_favorite_place",
                        columnList = "place_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "user_id", nullable = false)
        @OnDelete(action = OnDeleteAction.CASCADE)
        private User user;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "place_id", nullable = false)
        @OnDelete(action = OnDeleteAction.CASCADE)
        private Place place;

        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt;

        /** 인증된 회원과 실제 장소 엔티티로만 찜을 생성한다. */
        public Favorite(User user, Place place) {
                this.user = user;
                this.place = place;
        }

        @PrePersist
        void onCreate() {
                createdAt = LocalDateTime.now();
        }
}
