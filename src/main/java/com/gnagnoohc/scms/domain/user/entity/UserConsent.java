package com.gnagnoohc.scms.domain.user.entity;

import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

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

    /**
     * 동의 1건을 새로 기록한다. 기존 이력을 수정(UPDATE)하지 않고 항상 새 행을 추가하는
     * 이유는 두 가지다.
     *   1) 철회 후 재동의 — withdrawnAt이 찍힌 행을 되살리지 않는다. 되살리면 "언제 철회했다가
     *      언제 다시 동의했는지"가 사라져 이력 관리 요건(법적 근거 보존)을 못 지킨다.
     *   2) 정책 버전 변경 — consentPolicy가 최신 버전으로 바뀌면 새 정책 ID로 새 행이 생기므로,
     *      과거 버전에 대한 동의 기록도 그대로 남는다.
     * 실제 호출은 UserConsentService.agree() 에서만 하고, 그 안에서 중복/철회 여부를 먼저 판단한다.
     */
    public static UserConsent create(AppUser user, ConsentPolicy consentPolicy, Instant consentedAt) {
        UserConsent consent = new UserConsent();
        consent.user = user;
        consent.consentPolicy = consentPolicy;
        consent.consentedAt = consentedAt;
        return consent;

    }

    /**
     * 철회 처리. 이미 철회된 행이면 기존 withdrawnAt을 보존하기 위해 예외를 던진다.
     */
    public void withdraw(Instant withdrawnAt) {
        Objects.requireNonNull(withdrawnAt, "withdrawnAt must not be null");
        if (this.withdrawnAt != null) {
            throw new IllegalStateException("already withdrawn");
        }
        this.withdrawnAt = withdrawnAt;
    }
}
