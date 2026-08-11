package com.gnagnoohc.scms.domain.user.entity;

import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import com.gnagnoohc.scms.global.common.enums.Department;
import com.gnagnoohc.scms.global.common.enums.UserType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모든 사용자(학생/교직원/상담사/기업체)의 공통 계정 정보.
 *
 * ── 설계 선택에 대한 메모 ────────────────────────────────────────
 * 사용자 유형이 4종이라 세 가지 선택지가 있습니다.
 *
 *  (A) 단일 테이블 + nullable 확장 필드   ← 지금 이 방식
 *  (B) JPA 상속 (@Inheritance JOINED) 으로 Student/Staff/Company 분리
 *  (C) User 와 StudentProfile 을 1:1 로 분리
 *
 * 팀 규모와 기간을 감안해 가장 단순한 (A)로 시작하되,
 * 학생 전용 필드(학번/학과/학년)가 5개를 넘어가면 (C)로 분리하는 것을 권장합니다.
 * ERD 확정 시 이 주석을 지우고 결정 사항을 기록해 두세요.
 */
@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserType userType;

    /** STAFF 인 경우에만 값이 있습니다. 권한 판정에 사용됩니다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private Department department;

    /** STUDENT 인 경우에만 값이 있습니다. */
    @Column(length = 20)
    private String studentNo;

    @Column(length = 50)
    private String majorName;

    @Column
    private Integer gradeYear;

    /** COMPANY 인 경우에만 값이 있습니다. */
    @Column(length = 100)
    private String companyName;

    @Column(nullable = false)
    private boolean active = true;

    @Builder
    private User(String loginId, String password, String name, String email, String phone,
                 UserType userType, Department department,
                 String studentNo, String majorName, Integer gradeYear,
                 String companyName) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.userType = userType;
        this.department = department;
        this.studentNo = studentNo;
        this.majorName = majorName;
        this.gradeYear = gradeYear;
        this.companyName = companyName;
        this.active = true;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isStudent() {
        return userType == UserType.STUDENT;
    }
}
