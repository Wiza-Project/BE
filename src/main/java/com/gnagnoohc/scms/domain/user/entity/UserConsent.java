package com.gnagnoohc.scms.domain.user.entity;

import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(name = "user_consent")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent extends BaseCreatedAtEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_consent_id", nullable = false) private Integer userConsentId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "consent_policy_id", nullable = false)
    private ConsentPolicy consentPolicy;
    @Column(name = "consented_at", nullable = false) private Instant consentedAt;
    @Column(name = "withdrawn_at") private Instant withdrawnAt;
}
