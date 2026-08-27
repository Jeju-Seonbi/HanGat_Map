package com.example.hangat.course.model;

import com.example.hangat.common.model.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "place_source_mappings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_place_source_identity",
                columnNames = {"source_code", "source_place_id"})
)
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false)),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false))
})
@Getter
@NoArgsConstructor
public class PlaceSourceMapping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "source_code", nullable = false, length = 30)
    private String sourceCode;

    @Column(name = "source_place_id", nullable = false, length = 100)
    private String sourcePlaceId;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "source_updated_at")
    private LocalDateTime sourceUpdatedAt;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    @Column(name = "data_hash", length = 64)
    private String dataHash;

    @Column(name = "raw_payload", columnDefinition = "JSON")
    private String rawPayload;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public static PlaceSourceMapping active(
            Place place,
            String sourceCode,
            String sourcePlaceId
    ) {
        PlaceSourceMapping mapping = new PlaceSourceMapping();
        mapping.place = place;
        mapping.sourceCode = sourceCode;
        mapping.sourcePlaceId = sourcePlaceId;
        mapping.lastSyncedAt = LocalDateTime.now();
        mapping.active = true;
        return mapping;
    }
}
