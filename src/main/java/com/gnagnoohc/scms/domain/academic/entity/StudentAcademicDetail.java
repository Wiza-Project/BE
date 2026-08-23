package com.gnagnoohc.scms.domain.academic.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.entity.FileGroup;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 학적 신상 정보(1:1, {@code app_user} 종속).
 *
 * <p>이 프로젝트는 학교 본원 시스템에서 학적정보가 이미 채워진 학생이 넘어온 이후의
 * 서비스라, 회원가입/입학처리 절차가 따로 없어도 그 시점에 항상 확정돼 있는 값(소속학과·
 * 생년월일·성별·학년)은 NOT NULL로 건다. 반대로 이 프로젝트 안에서 별도 입력 절차가
 * 필요하거나(주소·지도교수·증명사진·학위정보) 학기마다 갱신되는 값(이수학기)은
 * nullable로 둔다.</p>
 *
 * <p>행 자체는 없을 수 있다 — 기존 학생은 이 행이 아예 없는 상태로 시작하고(조회는
 * LEFT JOIN + "미입력" 표시), 이번 티켓엔 쓰기 API가 없어 시드 SQL로만 채워진다
 * (설계 문서 8-1).</p>
 *
 * <p>{@code grade}(학년)는 이 테이블이 단일 출처다 — {@code career} 도메인의
 * {@code student_profile.student_grade}를 참조하지 않는다(설계 문서 2-1장).</p>
 */
@Entity
@Getter
@Table(name = "student_academic_detail")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentAcademicDetail extends BaseTimeEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /**
     * 소속학과(MAJOR 그룹). {@code app_user.departmentCode}(교내 행정조직)와는 별개 개념이다.
     * 학교 본원 시스템에서 이미 확정돼 넘어오는 값이라 NOT NULL.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "major_code_id", nullable = false)
    private CommonCode majorCode;

    /** 지도교수. 계정 없는 겸임교수는 이번 범위에서 다루지 않는다(설계 문서 7장). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advisor_user_id")
    private AppUser advisorUser;

    /** 증명사진. {@code stored_file}을 직접 FK로 잡지 않고 {@code file_group}을 경유한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_file_group_id")
    private FileGroup photoFileGroup;

    /** 학교 본원 시스템에서 이미 확정돼 넘어오는 값이라 NOT NULL. */
    @Column(name = "birth_date", nullable = false) private LocalDate birthDate;
    /** 'M' 또는 'F'. 주민번호를 저장하지 않는 대신 마스킹 표시를 조립하는 데 쓰인다. 학교 본원 시스템에서 이미 확정돼 넘어오는 값이라 NOT NULL. */
    @Column(name = "gender", length = 1, nullable = false) private String gender;

    @Column(name = "zipcode", length = 10) private String zipcode;
    @Column(name = "address_basic", length = 255) private String addressBasic;
    @Column(name = "address_detail", length = 255) private String addressDetail;
    /** 학생이 직접 수정할 수 있는 유일한 필드였으나, 이번 티켓은 조회 API만 다룬다(후속 티켓). */
    @Column(name = "guardian_phone", length = 30) private String guardianPhone;

    /**
     * 1~4. 단일 출처 — {@link com.gnagnoohc.scms.domain.career.entity.StudentProfile}에서
     * 참조하지 않는다. 학교 본원 시스템에서 이미 확정돼 넘어오는 값이라 NOT NULL.
     */
    @Column(name = "grade", nullable = false) private Short grade;
    /** 이수학기. 학기초과 여부(8학기 초과, 4년제 고정)는 이 값에서 파생한다. */
    @Column(name = "completed_semesters") private Short completedSemesters;
    /** 학위종별 = 학위명(졸업생만). */
    @Column(name = "degree_name", length = 50) private String degreeName;
    /** 학위등록번호 = 학위번호(졸업생만). */
    @Column(name = "degree_no", length = 50) private String degreeNo;
}
