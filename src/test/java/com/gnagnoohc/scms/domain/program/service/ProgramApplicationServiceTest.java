package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationAdminListItemResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationBulkDecisionResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationCancelResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationDecisionResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationSummaryResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationSurveyResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplyResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramAttendanceRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramMileageTransactionRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramApplicationServiceTest {

    @Mock
    ExtracurricularProgramRepository programRepository;

    @Mock
    ProgramApplicationRepository applicationRepository;

    @Mock
    ProgramAttendanceRepository attendanceRepository;

    @Mock
    ProgramMileageTransactionRepository mileageTransactionRepository;

    @InjectMocks
    ProgramApplicationService programApplicationService;

    @Test
    void apply_whenWithinCapacity_returnsApplied() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatus(1, "APPLIED")).thenReturn(9L);
        when(applicationRepository.insertApplication(eq(1), eq(100), eq("APPLIED"), eq(null), any()))
                .thenReturn(1);

        ProgramApplyResponseDTO response = programApplicationService.apply(1, 100);

        assertThat(response.applicationStatus()).isEqualTo("APPLIED");
        assertThat(response.applicationStatusLabel()).isEqualTo("신청완료");
        assertThat(response.waitlistOrder()).isNull();
    }

    @Test
    void apply_whenCapacityExceeded_returnsWaitlistedWithNextOrder() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatus(1, "APPLIED")).thenReturn(10L);
        when(applicationRepository.findMaxWaitlistOrderByProgramId(1)).thenReturn(2);
        when(applicationRepository.insertApplication(eq(1), eq(100), eq("WAITLISTED"), eq(3), any()))
                .thenReturn(1);

        ProgramApplyResponseDTO response = programApplicationService.apply(1, 100);

        assertThat(response.applicationStatus()).isEqualTo("WAITLISTED");
        assertThat(response.applicationStatusLabel()).isEqualTo("대기");
        assertThat(response.waitlistOrder()).isEqualTo(3);
    }

    @Test
    void apply_beforeRecruitmentStart_throwsApplicationPeriodClosed() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.plusSeconds(3600), now.plusSeconds(7200), 10);

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));

        assertThatThrownBy(() -> programApplicationService.apply(1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_PERIOD_CLOSED);
    }

    @Test
    void apply_afterRecruitmentEnd_throwsApplicationPeriodClosed() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(7200), now.minusSeconds(3600), 10);

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));

        assertThatThrownBy(() -> programApplicationService.apply(1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_PERIOD_CLOSED);
    }

    @Test
    void apply_whenDuplicate_throwsAlreadyApplied() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatus(1, "APPLIED")).thenReturn(0L);
        when(applicationRepository.insertApplication(anyInt(), anyInt(), eq("APPLIED"), eq(null), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> programApplicationService.apply(1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_APPLIED);
    }

    @Test
    void apply_whenProgramNotFound_throwsProgramNotFound() {
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programApplicationService.apply(1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
    }

    @Test
    void approve_whenWithinCapacity_succeeds() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "WAITLISTED");

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatus(1, "APPROVED")).thenReturn(9L);

        ProgramApplicationDecisionResponseDTO response = programApplicationService.approve(1, 5, 200);

        assertThat(response.applicationStatus()).isEqualTo("APPROVED");
        assertThat(response.applicationStatusLabel()).isEqualTo("승인");
        assertThat(response.processedBy()).isEqualTo(200);
    }

    @Test
    void approve_whenCapacityFull_throwsCapacityExceeded() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED");

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatus(1, "APPROVED")).thenReturn(10L);

        assertThatThrownBy(() -> programApplicationService.approve(1, 5, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_CAPACITY_EXCEEDED);
    }

    @Test
    void approve_whenAlreadyProcessed_throwsAlreadyProcessed() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPROVED");

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> programApplicationService.approve(1, 5, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_ALREADY_PROCESSED);
    }

    @Test
    void reject_succeeds_withReason() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED");

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));

        ProgramApplicationDecisionResponseDTO response =
                programApplicationService.reject(1, 5, "정원 외 사유", 200);

        assertThat(response.applicationStatus()).isEqualTo("REJECTED");
        assertThat(response.applicationStatusLabel()).isEqualTo("반려");
        assertThat(response.decisionReason()).isEqualTo("정원 외 사유");
    }

    @Test
    void reject_whenApplicationNotFound_throwsApplicationNotFound() {
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programApplicationService.reject(1, 5, "사유", 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    void reject_whenApplicationBelongsToOtherProgram_throwsApplicationNotFound() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED");

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> programApplicationService.reject(999, 5, "사유", 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    void cancel_whenWithinRecruitmentPeriod_succeeds() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED", 100);

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));
        when(applicationRepository.updateCancellation(eq(5), eq("일정 변경"), any())).thenReturn(1);

        ProgramApplicationCancelResponseDTO response =
                programApplicationService.cancel(1, 5, 100, "일정 변경");

        assertThat(response.applicationStatus()).isEqualTo("CANCELLED");
        assertThat(response.applicationStatusLabel()).isEqualTo("취소");
        assertThat(response.cancellationReason()).isEqualTo("일정 변경");
    }

    @Test
    void cancel_whenApproved_succeeds() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPROVED", 100);

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));
        when(applicationRepository.updateCancellation(eq(5), eq(null), any())).thenReturn(1);

        ProgramApplicationCancelResponseDTO response =
                programApplicationService.cancel(1, 5, 100, null);

        assertThat(response.applicationStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_afterRecruitmentEnd_throwsApplicationNotCancelable() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(7200), now.minusSeconds(3600), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED", 100);

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> programApplicationService.cancel(1, 5, 100, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_CANCELABLE);
    }

    @Test
    void cancel_whenAlreadyRejected_throwsApplicationAlreadyCanceled() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "REJECTED", 100);

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> programApplicationService.cancel(1, 5, 100, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_ALREADY_CANCELED);
    }

    @Test
    void cancel_whenApplicationNotFound_throwsApplicationNotFound() {
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programApplicationService.cancel(1, 5, 100, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    void cancel_whenApplicationBelongsToOtherProgram_throwsApplicationNotFound() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED", 100);

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> programApplicationService.cancel(999, 5, 100, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    void cancel_whenNotOwnedByStudent_throwsApplicationNotFound() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED", 100);

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> programApplicationService.cancel(1, 5, 999, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    void cancel_whenUpdateAffectsNoRows_throwsApplicationNotCancelable() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED", 100);

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));
        when(applicationRepository.updateCancellation(eq(5), eq(null), any())).thenReturn(0);

        assertThatThrownBy(() -> programApplicationService.cancel(1, 5, 100, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_CANCELABLE);
    }

    @Test
    void listMyApplications_returnsSummariesOrderedByRepository() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ReflectionTestUtils.setField(program, "programName", "테스트 프로그램");

        Instant now = Instant.now();
        ProgramApplication applied = buildApplicationSummaryFixture(
                1, program, "APPLIED", null, now, null, null, null, null, null, null, null);
        ProgramApplication waitlisted = buildApplicationSummaryFixture(
                2, program, "WAITLISTED", 3, now.minusSeconds(60), null, null, null, null, null, null, null);
        ProgramApplication rejected = buildApplicationSummaryFixture(
                3, program, "REJECTED", null, now.minusSeconds(120), now.minusSeconds(30), "정원 외 사유",
                null, null, null, null, null);

        Pageable pageable = PageRequest.of(0, 10);
        Page<ProgramApplication> page = new PageImpl<>(List.of(applied, waitlisted, rejected), pageable, 3);
        when(applicationRepository.findAllByStudentId(100, pageable)).thenReturn(page);

        PageResponse<ProgramApplicationSummaryResponseDTO> response =
                programApplicationService.listMyApplications(100, pageable);

        assertThat(response.content()).hasSize(3);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.content().get(0).applicationStatusLabel()).isEqualTo("신청완료");
        assertThat(response.content().get(1).waitlistOrder()).isEqualTo(3);
        assertThat(response.content().get(1).applicationStatusLabel()).isEqualTo("대기");
        assertThat(response.content().get(2).decisionReason()).isEqualTo("정원 외 사유");
        assertThat(response.content().get(2).applicationStatusLabel()).isEqualTo("반려");
    }

    @Test
    void listMyApplications_whenNoApplications_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(applicationRepository.findAllByStudentId(100, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<ProgramApplicationSummaryResponseDTO> response =
                programApplicationService.listMyApplications(100, pageable);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(0);
    }

    @Test
    void listMyApplications_whenCompleted_includesCertificateInfo() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ReflectionTestUtils.setField(program, "programName", "테스트 프로그램");

        Instant issuedAt = Instant.now();
        ProgramApplication completed = buildApplicationSummaryFixture(
                1, program, "APPROVED", null, Instant.now(), null, null, null, null,
                "COMPLETED", "CERT-2026-0001", issuedAt);

        Pageable pageable = PageRequest.of(0, 10);
        when(applicationRepository.findAllByStudentId(100, pageable))
                .thenReturn(new PageImpl<>(List.of(completed), pageable, 1));

        PageResponse<ProgramApplicationSummaryResponseDTO> response =
                programApplicationService.listMyApplications(100, pageable);

        ProgramApplicationSummaryResponseDTO dto = response.content().get(0);
        assertThat(dto.completionStatus()).isEqualTo("COMPLETED");
        assertThat(dto.certificateNo()).isEqualTo("CERT-2026-0001");
        assertThat(dto.certificateIssuedAt()).isEqualTo(issuedAt);
    }

    @Test
    void listMyApplications_passesPageableThrough() {
        Pageable pageable = PageRequest.of(1, 5);
        when(applicationRepository.findAllByStudentId(100, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        programApplicationService.listMyApplications(100, pageable);

        verify(applicationRepository).findAllByStudentId(100, pageable);
    }

    @Test
    void bulkApprove_whenOneExceedsCapacity_returnsPartialSuccess() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication succeeding = buildApplicationFixture(5, program, "WAITLISTED");
        ProgramApplication failing = buildApplicationFixture(6, program, "APPLIED");

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(succeeding));
        when(applicationRepository.findByIdForUpdate(6)).thenReturn(Optional.of(failing));
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        // 첫 번째 건을 승인하는 순간 정원이 다 찼다고 가정 — 두 번째 건은 승인 시도 시 정원초과로 실패해야 한다.
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatus(1, "APPROVED"))
                .thenReturn(9L, 10L);

        ProgramApplicationBulkDecisionResponseDTO response =
                programApplicationService.bulkApprove(1, List.of(5, 6), 200);

        assertThat(response.succeeded()).hasSize(1);
        assertThat(response.succeeded().get(0).applicationId()).isEqualTo(5);
        assertThat(response.failed()).hasSize(1);
        assertThat(response.failed().get(0).applicationId()).isEqualTo(6);
        assertThat(response.failed().get(0).errorCode()).isEqualTo(ErrorCode.PROGRAM_CAPACITY_EXCEEDED.getCode());
    }

    @Test
    void bulkReject_allSucceed_returnsAllInSucceeded() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication first = buildApplicationFixture(5, program, "APPLIED");
        ProgramApplication second = buildApplicationFixture(6, program, "APPLIED");

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(first));
        when(applicationRepository.findByIdForUpdate(6)).thenReturn(Optional.of(second));

        ProgramApplicationBulkDecisionResponseDTO response =
                programApplicationService.bulkReject(1, List.of(5, 6), "정원 외 사유", 200);

        assertThat(response.succeeded()).hasSize(2);
        assertThat(response.failed()).isEmpty();
    }

    @Test
    void completeSurvey_whenApproved_succeeds() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPROVED", 100);

        when(applicationRepository.findById(5)).thenReturn(Optional.of(application));
        when(applicationRepository.markSurveyCompleted(eq(5), eq(100), any())).thenReturn(1);

        ProgramApplicationSurveyResponseDTO response = programApplicationService.completeSurvey(1, 5, 100);

        assertThat(response.applicationId()).isEqualTo(5);
        assertThat(response.surveyCompleted()).isTrue();
    }

    @Test
    void completeSurvey_whenNotApproved_throwsSurveyNotAvailable() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED", 100);

        when(applicationRepository.findById(5)).thenReturn(Optional.of(application));
        when(applicationRepository.markSurveyCompleted(eq(5), eq(100), any())).thenReturn(0);

        assertThatThrownBy(() -> programApplicationService.completeSurvey(1, 5, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SURVEY_NOT_AVAILABLE);
    }

    @Test
    void completeSurvey_whenNotOwnedByStudent_throwsApplicationNotFound() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPROVED", 100);

        when(applicationRepository.findById(5)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> programApplicationService.completeSurvey(1, 5, 999))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    void listByProgram_whenProgramExists_returnsAdminItems() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED", 100);
        ReflectionTestUtils.setField(application.getStudent(), "userName", "홍길동");
        ReflectionTestUtils.setField(application.getStudent(), "universityNo", "2021000123");

        Pageable pageable = PageRequest.of(0, 20);
        when(programRepository.existsById(1)).thenReturn(true);
        when(applicationRepository.findAllByProgramIdAndStatus(1, null, pageable))
                .thenReturn(new PageImpl<>(List.of(application), pageable, 1));

        PageResponse<ProgramApplicationAdminListItemResponseDTO> response =
                programApplicationService.listByProgram(1, null, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).studentName()).isEqualTo("홍길동");
        assertThat(response.content().get(0).studentNo()).isEqualTo("2021000123");
    }

    @Test
    void listByProgram_whenProgramNotFound_throwsProgramNotFound() {
        when(programRepository.existsById(1)).thenReturn(false);

        assertThatThrownBy(() -> programApplicationService.listByProgram(1, null, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
    }

    /**
     * ExtracurricularProgram은 protected 기본 생성자만 있고 setter/빌더가 없어(네이티브 SQL로만 값을 채우는 구조),
     * 테스트 픽스처는 리플렉션으로 생성한 뒤 필요한 필드만 직접 채워 넣는다. (ProgramServiceTest.buildProgramFixture 참고)
     */
    private ExtracurricularProgram buildProgramFixture(
            Integer programId, Instant recruitmentStartsAt, Instant recruitmentEndsAt, Integer capacity) throws Exception {
        Constructor<ExtracurricularProgram> constructor = ExtracurricularProgram.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ExtracurricularProgram program = constructor.newInstance();
        ReflectionTestUtils.setField(program, "programId", programId);
        ReflectionTestUtils.setField(program, "recruitmentStartsAt", recruitmentStartsAt);
        ReflectionTestUtils.setField(program, "recruitmentEndsAt", recruitmentEndsAt);
        ReflectionTestUtils.setField(program, "capacity", capacity);
        return program;
    }

    // ProgramApplication도 같은 이유(protected 기본 생성자, setter/빌더 없음)로 리플렉션 픽스처를 사용한다.
    private ProgramApplication buildApplicationFixture(
            Integer applicationId, ExtracurricularProgram program, String applicationStatus) throws Exception {
        Constructor<ProgramApplication> constructor = ProgramApplication.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ProgramApplication application = constructor.newInstance();
        ReflectionTestUtils.setField(application, "applicationId", applicationId);
        ReflectionTestUtils.setField(application, "program", program);
        ReflectionTestUtils.setField(application, "applicationStatus", applicationStatus);
        return application;
    }

    // listMyApplications 테스트용: 목록 응답 매핑에 필요한 필드까지 채우는 풀필드 픽스처.
    private ProgramApplication buildApplicationSummaryFixture(
            Integer applicationId, ExtracurricularProgram program, String applicationStatus,
            Integer waitlistOrder, Instant createdAt, Instant processedAt, String decisionReason,
            Instant canceledAt, String cancellationReason,
            String completionStatus, String certificateNo, Instant certificateIssuedAt) throws Exception {
        ProgramApplication application = buildApplicationFixture(applicationId, program, applicationStatus);
        ReflectionTestUtils.setField(application, "waitlistOrder", waitlistOrder);
        ReflectionTestUtils.setField(application, "createdAt", createdAt);
        ReflectionTestUtils.setField(application, "processedAt", processedAt);
        ReflectionTestUtils.setField(application, "decisionReason", decisionReason);
        ReflectionTestUtils.setField(application, "canceledAt", canceledAt);
        ReflectionTestUtils.setField(application, "cancellationReason", cancellationReason);
        ReflectionTestUtils.setField(application, "completionStatus", completionStatus);
        ReflectionTestUtils.setField(application, "certificateNo", certificateNo);
        ReflectionTestUtils.setField(application, "certificateIssuedAt", certificateIssuedAt);
        return application;
    }

    // student(AppUser)까지 채워야 하는 취소 테스트용 오버로드. AppUser도 같은 이유로 리플렉션 픽스처를 쓴다.
    private ProgramApplication buildApplicationFixture(
            Integer applicationId, ExtracurricularProgram program, String applicationStatus, Integer studentId) throws Exception {
        ProgramApplication application = buildApplicationFixture(applicationId, program, applicationStatus);

        Constructor<AppUser> studentConstructor = AppUser.class.getDeclaredConstructor();
        studentConstructor.setAccessible(true);
        AppUser student = studentConstructor.newInstance();
        ReflectionTestUtils.setField(student, "userId", studentId);
        ReflectionTestUtils.setField(application, "student", student);

        return application;
    }
}
