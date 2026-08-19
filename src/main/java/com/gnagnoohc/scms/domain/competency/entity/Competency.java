package com.gnagnoohc.scms.domain.competency.entity;

import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Getter
@Table(name = "competency")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Competency extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "competency_id", nullable = false) private Integer competencyId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_competency_id") private Competency parentCompetency;
    @Column(name = "competency_code", nullable = false, unique = true, length = 20) private String competencyCode;
    @Column(name = "competency_name", nullable = false, length = 100) private String competencyName;
    @Column(name = "english_name", length = 150) private String englishName;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "display_order", nullable = false) private Integer displayOrder;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "created_by", nullable = false) private Integer createdBy;
}
