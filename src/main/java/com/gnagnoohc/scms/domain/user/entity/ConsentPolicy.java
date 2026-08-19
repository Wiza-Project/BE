package com.gnagnoohc.scms.domain.user.entity;

import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(name = "consent_policy", uniqueConstraints = @UniqueConstraint(
        name = "uq_consent_policy_type_module_version",
        columnNames = {"consent_type", "module_code", "version"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentPolicy extends BaseCreatedAtEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_policy_id", nullable = false) private Integer consentPolicyId;
    @Column(name = "consent_type", nullable = false, length = 40) private String consentType;
    @Column(name = "module_code", nullable = false, length = 30) private String moduleCode;
    @Column(name = "version", nullable = false, length = 30) private String version;
    @Column(name = "title", nullable = false, length = 200) private String title;
    @Column(name = "content", nullable = false, columnDefinition = "text") private String content;
    @Column(name = "is_required", nullable = false) private boolean required = true;
    @Column(name = "effective_from", nullable = false) private Instant effectiveFrom;
    @Column(name = "effective_to") private Instant effectiveTo;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "created_by", nullable = false) private Integer createdBy;
}
