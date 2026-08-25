package com.gnagnoohc.scms.domain.career.entity;

import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Table(name = "company_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_account_id", nullable = false)
    private Integer companyAccountId;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "business_registration_no", nullable = false, unique = true, length = 30)
    private String businessRegistrationNo;

    @Column(name = "login_id", nullable = true, unique = true, length = 100)
    private String loginId;

    @Column(name = "password_hash", nullable = true, length = 255)
    private String passwordHash;

    @Column(name = "representative_name", nullable = true, length = 100)
    private String representativeName;

    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    @Column(name = "contact_phone", nullable = true, length = 30)
    private String contactPhone;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "address", nullable = true, length = 500)
    private String address;

    @Column(name = "verification_status", nullable = false, length = 20)
    private String verificationStatus = "PENDING";

    @Column(name = "verified_by", nullable = true)
    private Integer verifiedBy;

    @Column(name = "verified_at", nullable = true)
    private Instant verifiedAt;

    @Column(name = "account_status", nullable = false, length = 20)
    private String accountStatus = "ACTIVE";

    /**
     * 기업 등록 시 필수/선택 메타데이터를 안전하게 주입하기 위한 전용 빌더 생성자
     */
    @Builder
    private CompanyAccount(String companyName, String businessRegistrationNo, String loginId,
                           String passwordHash, String representativeName, String contactName,
                           String contactPhone, String contactEmail, String address,
                           String verificationStatus, String accountStatus) {
        this.companyName = companyName;
        this.businessRegistrationNo = businessRegistrationNo;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.representativeName = representativeName;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        this.address = address;
        this.verificationStatus = (verificationStatus != null) ? verificationStatus : "PENDING";
        this.accountStatus = (accountStatus != null) ? accountStatus : "ACTIVE";
    }

    /**
     * 교직원 승인 처리
     */
    public void verify(Integer reviewerUserId, Instant verifiedAt) {
        this.verificationStatus = "VERIFIED";
        this.verifiedBy = reviewerUserId;
        this.verifiedAt = verifiedAt;
        this.accountStatus = "ACTIVE";
    }

    /**
     * 교직원 반려 처리
     */
    public void reject(Integer reviewerUserId) {
        this.verificationStatus = "REJECTED";
        this.verifiedBy = reviewerUserId;
        this.accountStatus = "INACTIVE";
    }

}
