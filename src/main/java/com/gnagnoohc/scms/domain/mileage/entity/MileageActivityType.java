package com.gnagnoohc.scms.domain.mileage.entity;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 마일리지를 적립할 활동의 분류·프로그램 유형·핵심역량·적립 경로를 정의한다. */
@Entity @Getter @Table(name = "mileage_activity_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MileageActivityType extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_type_id", nullable = false) private Integer activityTypeId;
    /** 외부활동처럼 핵심역량을 기준으로 하는 활동 유형에서 사용한다. 비교과 프로그램 유형 정책에서는 null일 수 있다. */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "competency_id") private Competency competency;
    /** 비교과 프로그램 이수 정책에서 사용하는 프로그램 유형(PROGRAM_TYPE)이다. */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "program_type_code_id") private CommonCode programTypeCode;
    @Column(name = "activity_code", nullable = false, unique = true, length = 40) private String activityCode;
    @Column(name = "category_code", nullable = false, length = 40) private String categoryCode;
    @Column(name = "activity_name", nullable = false, length = 150) private String activityName;
    @Column(name = "earning_route", nullable = false, length = 30) private String earningRoute;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "created_by", nullable = false) private Integer createdBy;

    public static MileageActivityType create(Competency competency, String activityCode,
                                             String categoryCode, String activityName,
                                             String earningRoute, Integer createdBy) {
        MileageActivityType activityType = new MileageActivityType();
        activityType.competency = competency;
        activityType.activityCode = activityCode;
        activityType.categoryCode = categoryCode;
        activityType.activityName = activityName;
        activityType.earningRoute = earningRoute;
        activityType.createdBy = createdBy;
        return activityType;
    }

    /** 프로그램 유형별 비교과 활동 유형을 생성한다. */
    public static MileageActivityType createForProgramType(CommonCode programTypeCode,
                                                           String activityCode,
                                                           String categoryCode,
                                                           String activityName,
                                                           String earningRoute,
                                                           Integer createdBy) {
        MileageActivityType activityType = create(
                null, activityCode, categoryCode, activityName, earningRoute, createdBy);
        activityType.programTypeCode = programTypeCode;
        return activityType;
    }
}
