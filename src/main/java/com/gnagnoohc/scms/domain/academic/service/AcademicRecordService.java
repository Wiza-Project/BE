package com.gnagnoohc.scms.domain.academic.service;

import com.gnagnoohc.scms.domain.academic.dto.AdminStudentListItemResponse;
import com.gnagnoohc.scms.domain.academic.dto.AdminStudentSearchConditionDTO;
import com.gnagnoohc.scms.domain.academic.dto.AdminStudentSummaryResponse;
import com.gnagnoohc.scms.domain.academic.dto.StudentAcademicRecordResponse;
import com.gnagnoohc.scms.domain.academic.entity.StudentAcademicChange;
import com.gnagnoohc.scms.domain.academic.entity.StudentAcademicDetail;
import com.gnagnoohc.scms.domain.academic.repository.AcademicRecordQueryRepository;
import com.gnagnoohc.scms.domain.academic.repository.StudentAcademicChangeRepository;
import com.gnagnoohc.scms.domain.academic.repository.StudentAcademicDetailRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 학적조회 API 서비스.조회만 다룬다 — 학적 데이터·변동이력은 시드 SQL로
 * 적재되고, 이 서비스는 쓰기 동작이 전혀 없다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicRecordService {

    private static final String STUDENT_USER_TYPE = "STUDENT";

    private final AppUserRepository appUserRepository;
    private final StudentAcademicDetailRepository detailRepository;
    private final StudentAcademicChangeRepository changeRepository;
    private final AcademicRecordQueryRepository queryRepository;

    /** GET /api/students/academic-record — 로그인한 학생 본인 조회. */
    public StudentAcademicRecordResponse getMyRecord(Integer userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return buildRecord(user);
    }

    /**
     * GET /api/staff/students/{studentId} — 교직원 상세 조회. {@code studentId}는
     * {@code app_user.user_id}(내부 PK)다. 대상이 없거나 학생이 아니면 "학적 상세가
     * 없다"가 아니라 "학생을 찾을 수 없다"는 뜻이라 USER_NOT_FOUND를 재사용한다
     */
    public StudentAcademicRecordResponse getStudentDetail(Integer studentId) {
        AppUser user = appUserRepository.findById(studentId)
                .filter(u -> STUDENT_USER_TYPE.equals(u.getUserType()))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return buildRecord(user);
    }

    /**
     * GET /api/staff/students — 페이징 목록. {@code student_academic_detail} 행이 없는
     * 학생도 목록엔 포함되고(전공/학년만 "미입력"), 입학일자는 페이지 단위 in절 조회로
     * 채운다
     */
    public PageResponse<AdminStudentListItemResponse> listStudents(
            AdminStudentSearchConditionDTO condition, Pageable pageable) {
        Page<AdminStudentListItemResponse> page = queryRepository.searchStudents(condition, pageable);

        List<Integer> studentIds = page.getContent().stream()
                .map(AdminStudentListItemResponse::userId)
                .toList();
        Map<Integer, LocalDate> admissionDates = queryRepository.findAdmissionDates(studentIds);

        Page<AdminStudentListItemResponse> enriched = page.map(
                item -> item.withAdmissionDate(admissionDates.get(item.userId())));
        return PageResponse.from(enriched);
    }

    /** GET /api/staff/students/summary — 상단 통계 타일. */
    public AdminStudentSummaryResponse getSummary() {
        return new AdminStudentSummaryResponse(queryRepository.countTotalStudents(), queryRepository.countByStatus());
    }

    private StudentAcademicRecordResponse buildRecord(AppUser user) {
        StudentAcademicDetail detail = detailRepository.findWithDetailsByUserId(user.getUserId()).orElse(null);
        List<StudentAcademicChange> changes = changeRepository.findAllByStudentIdWithCodes(user.getUserId());
        return StudentAcademicRecordResponse.of(user, detail, changes);
    }
}
