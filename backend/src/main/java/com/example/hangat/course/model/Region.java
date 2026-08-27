package com.example.hangat.course.model;

import com.example.hangat.common.model.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "regions")
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false)),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false))
})
@Getter
@NoArgsConstructor
public class Region extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, unique = true, length = 30)
    private String name;

    @Column(name = "center_lat", precision = 10, scale = 7)
    private BigDecimal centerLatitude;

    @Column(name = "center_lng", precision = 10, scale = 7)
    private BigDecimal centerLongitude;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "kma_grid_x")
    private Integer kmaGridX;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "kma_grid_y")
    private Integer kmaGridY;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "display_order", nullable = false, unique = true)
    private Integer displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public static Region reference(String code, String name, int displayOrder) {
        Region region = new Region();
        region.code = code;
        region.name = name;
        region.displayOrder = displayOrder;
        region.active = true;
        return region;
    }
}
