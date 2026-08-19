package com.gnagnoohc.scms.global.common.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "common_code", uniqueConstraints =
        @UniqueConstraint(name = "uq_common_code_group_code", columnNames = {"code_group", "code"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommonCode extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "code_id", nullable = false)
    private Integer codeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_code_id")
    private CommonCode parentCode;

    @Column(name = "code_group", nullable = false, length = 50) private String codeGroup;
    @Column(name = "code", nullable = false, length = 50) private String code;
    @Column(name = "code_name", nullable = false, length = 100) private String codeName;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "sort_order", nullable = false) private Integer sortOrder = 0;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    // 감사 행위자 ID는 감사 시점의 값으로 보존하기 위해 스칼라로 매핑한다.
    @Column(name = "created_by", nullable = false) private Integer createdBy;
}
