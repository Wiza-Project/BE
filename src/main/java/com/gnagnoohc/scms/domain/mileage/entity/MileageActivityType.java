package com.gnagnoohc.scms.domain.mileage.entity;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Getter @Table(name = "mileage_activity_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MileageActivityType extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_type_id", nullable = false) private Integer activityTypeId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "competency_id", nullable = false) private Competency competency;
    @Column(name = "activity_code", nullable = false, unique = true, length = 40) private String activityCode;
    @Column(name = "category_code", nullable = false, length = 40) private String categoryCode;
    @Column(name = "activity_name", nullable = false, length = 150) private String activityName;
    @Column(name = "earning_route", nullable = false, length = 30) private String earningRoute;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "created_by", nullable = false) private Integer createdBy;
}
