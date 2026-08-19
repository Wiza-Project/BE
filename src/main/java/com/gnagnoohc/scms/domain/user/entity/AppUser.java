package com.gnagnoohc.scms.domain.user.entity;

import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false) private Integer userId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "department_code_id") private CommonCode departmentCode;
    @Column(name = "university_no", nullable = false, unique = true, length = 30) private String universityNo;
    @Column(name = "user_type", nullable = false, length = 20) private String userType;
    @Column(name = "password_hash", nullable = false, length = 255) private String passwordHash;
    @Column(name = "user_name", nullable = false, length = 100) private String userName;
    @Column(name = "email", length = 255) private String email;
    @Column(name = "phone", length = 30) private String phone;
    @Column(name = "preferred_contact", length = 20) private String preferredContact;
    @Column(name = "academic_status", length = 20) private String academicStatus;
    @Column(name = "account_status", nullable = false, length = 20) private String accountStatus = "ACTIVE";
    @Column(name = "failed_login_count", nullable = false) private Integer failedLoginCount = 0;
    @Column(name = "locked_at") private Instant lockedAt;
    @Column(name = "last_login_at") private Instant lastLoginAt;
    @Column(name = "created_by") private Integer createdBy;
}
