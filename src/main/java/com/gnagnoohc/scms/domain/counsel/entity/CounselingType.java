package com.gnagnoohc.scms.domain.counsel.entity;

import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Getter @Table(name = "counseling_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CounselingType extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "counseling_type_id", nullable = false) private Integer counselingTypeId;
    @Column(name = "type_code", nullable = false, unique = true, length = 40) private String typeCode;
    @Column(name = "type_name", nullable = false, length = 100) private String typeName;
    @Column(name = "application_route", nullable = false, length = 30) private String applicationRoute;
    @Column(name = "counseling_method", nullable = false, length = 30) private String counselingMethod;
    @Column(name = "preceding_procedure", length = 300) private String precedingProcedure;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "created_by", nullable = false) private Integer createdBy;
}
