package com.gnagnoohc.scms.domain.academic.dto;

import com.gnagnoohc.scms.domain.academic.entity.StudentAcademicChange;
import com.gnagnoohc.scms.domain.academic.entity.StudentAcademicDetail;
import com.gnagnoohc.scms.domain.academic.support.ResidentNumberMask;
import com.gnagnoohc.scms.domain.user.entity.AppUser;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 학생 본인 조회(GET /api/students/academic-record)와 교직원 상세 조회
 * (GET /api/staff/students/{studentId})가 공유하는 응답 모양 — 두 화면의 "기본정보 +
 * 변동이력" 탭이 같은 구성이라 응답도 하나로 통일한다.
 *
 * <p>{@code student_academic_detail} 행이 없는 학생은 신상정보 관련 필드가 전부
 * {@code null}("미입력")로 내려간다 — 404가 아니다(설계 문서 3-1장).</p>
 */
public record StudentAcademicRecordResponse(
        String studentId,
        String name,
        String phone,
        String email,
        String status,

        String majorCode,
        String majorName,
        Integer grade,

        /** 주민번호는 저장하지 않는다 — birthDate+gender로 조립한 마스킹 값만 내려준다. */
        String residentNoMasked,

        String zipcode,
        String addressBasic,
        String addressDetail,
        String guardianPhone,

        String advisorName,
        Integer completedSemesters,
        /** completedSemesters > 8 (4년제 고정 판정). */
        boolean semesterExceeded,
        String degreeName,
        String degreeNo,

        /** AC100(입학) 최초 행의 change_date. 변동이력이 없으면 null. */
        LocalDate admissionDate,
        /** AC400(졸업) 행의 change_date. */
        LocalDate graduationDate,
        /** 최초 변동 행의 변동사유(보통 "신입학"). */
        String admissionType,
        /** 입학일자의 연도. */
        Integer curriculumYear,
        /** (change_date, change_id) 내림차순 첫 행의 변동사유 — 없으면 변동유형명으로 대체. */
        String latestChangeReason,
        /** 최신 AC200(휴학) 행의 복학예정 학년도/학기. */
        Integer scheduledReturnYear,
        String scheduledReturnSemesterCode,

        List<AcademicChangeItemResponse> changes
) {

    private static final String ADMISSION_TYPE_CODE = "AC100";
    private static final String GRADUATION_TYPE_CODE = "AC400";
    private static final String LEAVE_TYPE_CODE = "AC200";
    private static final int FOUR_YEAR_SEMESTER_LIMIT = 8;

    /**
     * @param changes {@link com.gnagnoohc.scms.domain.academic.repository.StudentAcademicChangeRepository
     *                #findAllByStudentIdWithCodes} 결과 — change_date 오름차순으로 정렬되어 있어야 한다.
     */
    public static StudentAcademicRecordResponse of(
            AppUser user, StudentAcademicDetail detail, List<StudentAcademicChange> changes) {

        List<AcademicChangeItemResponse> changeItems = IntStream.range(0, changes.size())
                .mapToObj(i -> AcademicChangeItemResponse.of(i + 1, changes.get(i)))
                .toList();

        LocalDate admissionDate = changes.stream()
                .filter(c -> ADMISSION_TYPE_CODE.equals(c.getChangeTypeCode().getCode()))
                .map(StudentAcademicChange::getChangeDate)
                .min(LocalDate::compareTo)
                .orElse(null);

        LocalDate graduationDate = changes.stream()
                .filter(c -> GRADUATION_TYPE_CODE.equals(c.getChangeTypeCode().getCode()))
                .map(StudentAcademicChange::getChangeDate)
                .max(LocalDate::compareTo)
                .orElse(null);

        StudentAcademicChange firstChange = changes.isEmpty() ? null : changes.get(0);
        StudentAcademicChange latestChange = changes.isEmpty() ? null : changes.get(changes.size() - 1);
        StudentAcademicChange latestLeave = changes.stream()
                .filter(c -> LEAVE_TYPE_CODE.equals(c.getChangeTypeCode().getCode()))
                .reduce((first, second) -> second) // 오름차순 정렬 전제 → 마지막 것이 최신
                .orElse(null);

        Short completedSemesters = detail != null ? detail.getCompletedSemesters() : null;

        return new StudentAcademicRecordResponse(
                user.getUniversityNo(),
                user.getUserName(),
                user.getPhone(),
                user.getEmail(),
                user.getAcademicStatus(),

                detail != null && detail.getMajorCode() != null ? detail.getMajorCode().getCode() : null,
                detail != null && detail.getMajorCode() != null ? detail.getMajorCode().getCodeName() : null,
                detail != null && detail.getGrade() != null ? detail.getGrade().intValue() : null,

                detail != null ? ResidentNumberMask.mask(detail.getBirthDate(), detail.getGender()) : null,

                detail != null ? detail.getZipcode() : null,
                detail != null ? detail.getAddressBasic() : null,
                detail != null ? detail.getAddressDetail() : null,
                detail != null ? detail.getGuardianPhone() : null,

                detail != null && detail.getAdvisorUser() != null ? detail.getAdvisorUser().getUserName() : null,
                completedSemesters != null ? completedSemesters.intValue() : null,
                completedSemesters != null && completedSemesters > FOUR_YEAR_SEMESTER_LIMIT,
                detail != null ? detail.getDegreeName() : null,
                detail != null ? detail.getDegreeNo() : null,

                admissionDate,
                graduationDate,
                changeReasonLabel(firstChange),
                admissionDate != null ? admissionDate.getYear() : null,
                changeReasonLabel(latestChange),
                latestLeave != null && latestLeave.getScheduledReturnYear() != null
                        ? latestLeave.getScheduledReturnYear().intValue() : null,
                latestLeave != null ? latestLeave.getScheduledReturnSemesterCode() : null,

                changeItems
        );
    }

    private static String changeReasonLabel(StudentAcademicChange change) {
        if (change == null) {
            return null;
        }
        return change.getChangeReasonCode() != null
                ? change.getChangeReasonCode().getCodeName()
                : change.getChangeTypeCode().getCodeName();
    }
}
