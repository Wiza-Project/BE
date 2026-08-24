package com.gnagnoohc.scms.domain.academic.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 학적변동이력(1:N, append-only). 학생 화면 "학적변동목록"과 교직원 모달 "변동이력" 탭이
 * 같은 모양이라 테이블 하나를 공유한다
 *
 * <p>{@code updated_at}이 없다 — 잘못 넣은 행은 수정이 아니라 정정 행 추가로 처리한다.
 * 입학일자/졸업일자/최종변동/학기초과/교육과정년도는 이 테이블에서 파생시키는 값이라
 * 컬럼으로 별도 저장하지 않는다.</p>
 *
 * <p>이번 티켓엔 이 이력을 입력하는 쓰기 API가 없다 — 시드 SQL이 {@code app_user}
 * {@code academic_status}까지 매핑에 맞춰 함께 채운다.</p>
 */
@Entity
@Getter
@Table(name = "student_academic_change")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentAcademicChange extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "student_academic_change_id", nullable = false)
    private Integer studentAcademicChangeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @Column(name = "change_date", nullable = false)
    private LocalDate changeDate;

    /** 변동코드(ACADEMIC_CHANGE_TYPE 그룹): AC100 입학 / AC200 휴학 / AC300 복학 / AC400 졸업 / AC500 제적 / AC600 자퇴 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_type_code_id", nullable = false)
    private CommonCode changeTypeCode;

    /** 변동사유(ACADEMIC_CHANGE_REASON 그룹). {@code parent_code_id}로 변동코드에 종속된다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_reason_code_id")
    private CommonCode changeReasonCode;

    /** 병무구분. MVP는 공통코드 없이 자유값(휴학/복학 행에만 의미 있음). */
    @Column(name = "military_status", length = 20) private String militaryStatus;
    /** 복학예정 학년도(휴학 행에만). */
    @Column(name = "scheduled_return_year") private Short scheduledReturnYear;
    /** 복학예정 학기(SEMESTER 그룹 값, 휴학 행에만). */
    @Column(name = "scheduled_return_semester_code", length = 20) private String scheduledReturnSemesterCode;
    /** 예: "2023학년도 1학기" 같은 부기. */
    @Column(name = "note", length = 500) private String note;

    /** 이 변동을 기록한 교직원. 감사 목적 스칼라 — {@code StoredFile.createdBy}와 같은 관례. */
    @Column(name = "created_by", nullable = false) private Integer createdBy;
}
