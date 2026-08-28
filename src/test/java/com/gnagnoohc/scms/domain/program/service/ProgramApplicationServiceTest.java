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
import com.gnagnoohc.scms.domain.program.event.ApplicationDecidedEvent;
import com.gnagnoohc.scms.domain.program.event.WaitlistSlotOpenedEvent;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramAttendanceRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramMileageTransactionRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentModuleCode;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentType;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentVerifier;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.common.notification.NotificationSender;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Mock
    NotificationSender notificationSender;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    PlatformTransactionManager transactionManager;

    @Mock
    ConsentVerifier consentVerifier;

    @InjectMocks
    ProgramApplicationService programApplicationService;

    @Test
    void apply_whenWithinCapacity_returnsApplied() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);

        mockValidProgramConsent(100, 900);
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByProgram_ProgramIdAndStudent_UserIdForUpdate(1, 100))
                .thenReturn(Optional.empty());
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
                .thenReturn(9L);
        when(applicationRepository.insertApplication(eq(1), eq(100), eq("APPLIED"), eq(null), eq(false), eq(900), any()))
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

        mockValidProgramConsent(100, 900);
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByProgram_ProgramIdAndStudent_UserIdForUpdate(1, 100))
                .thenReturn(Optional.empty());
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
                .thenReturn(10L);
        when(applicationRepository.findMaxWaitlistOrderByProgramId(1)).thenReturn(2);
        when(applicationRepository.insertApplication(eq(1), eq(100), eq("WAITLISTED"), eq(3), eq(false), eq(900), any()))
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

        mockValidProgramConsent(100, 900);
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

        mockValidProgramConsent(100, 900);
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));

        assertThatThrownBy(() -> programApplicationService.apply(1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_PERIOD_CLOSED);
    }

    // 락 획득 직후에는 마감 전이었지만(모집 마감까지 80ms), 정원 계산 쿼리가 그보다 오래 걸려(150ms)
    // 실제 저장 시점에는 이미 마감이 지난 경우를 재현한다. (e) 직전 재검사가 없다면 오래된 now로
    // 그대로 INSERT/UPDATE가 되어 마감 후 신청이 저장되는 회귀가 생긴다.
    @Test
    void apply_whenRecruitmentEndsDuringCapacityCheck_throwsApplicationPeriodClosed() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusMillis(80), 10);

        mockValidProgramConsent(100, 900);
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByProgram_ProgramIdAndStudent_UserIdForUpdate(1, 100))
                .thenReturn(Optional.empty());
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
                .thenAnswer(invocation -> {
                    Thread.sleep(150);
                    return 0L;
                });

        assertThatThrownBy(() -> programApplicationService.apply(1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_PERIOD_CLOSED);

        verify(applicationRepository, never())
                .insertApplication(anyInt(), anyInt(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void apply_whenNotAgreedToRequiredConsent_throwsRequiredConsentNotAgreed() throws Exception {
        when(consentVerifier.hasAgreedAllRequired(eq(100), eq(ConsentModuleCode.PROGRAM), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> programApplicationService.apply(1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REQUIRED_CONSENT_NOT_AGREED);

        verify(programRepository, never()).findByIdForUpdate(any());
    }

    // findCurrentValidConsent(잠금 없음)로 후보를 찾은 직후, 다른 트랜잭션의 withdraw()가 같은 동의 행의
    // 락을 먼저 잡고 철회를 커밋해버린 경합을 재현한다. requireOwnedValidConsent()가 락 획득 후 재검증에서
    // FORBIDDEN을 던지면, apply()는 이를 REQUIRED_CONSENT_NOT_AGREED로 변환해야 하고 신청을 저장해서는 안 된다.
    @Test
    void apply_whenConsentWithdrawnConcurrentlyAfterCandidateLookup_throwsRequiredConsentNotAgreed() throws Exception {
        when(consentVerifier.hasAgreedAllRequired(eq(100), eq(ConsentModuleCode.PROGRAM), any()))
                .thenReturn(true);
        when(consentVerifier.findCurrentValidConsent(
                eq(100), eq(ConsentModuleCode.PROGRAM), eq(ConsentType.TERMS_OF_SERVICE), any()))
                .thenReturn(Optional.of(buildUserConsentFixture(899)));
        when(consentVerifier.requireOwnedValidConsent(
                eq(899), eq(100), eq(ConsentModuleCode.PROGRAM), eq(ConsentType.TERMS_OF_SERVICE), any()))
                .thenReturn(buildUserConsentFixture(899));
        when(consentVerifier.findCurrentValidConsent(
                eq(100), eq(ConsentModuleCode.PROGRAM), eq(ConsentType.PERSONAL_INFO), any()))
                .thenReturn(Optional.of(buildUserConsentFixture(900)));
        when(consentVerifier.requireOwnedValidConsent(
                eq(900), eq(100), eq(ConsentModuleCode.PROGRAM), eq(ConsentType.PERSONAL_INFO), any()))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> programApplicationService.apply(1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REQUIRED_CONSENT_NOT_AGREED);

        verify(programRepository, never()).findByIdForUpdate(any());
        verify(applicationRepository, never())
                .insertApplication(anyInt(), anyInt(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void apply_whenDuplicate_throwsAlreadyApplied() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);

        mockValidProgramConsent(100, 900);
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByProgram_ProgramIdAndStudent_UserIdForUpdate(1, 100))
                .thenReturn(Optional.empty());
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
                .thenReturn(0L);
        when(applicationRepository.insertApplication(anyInt(), anyInt(), eq("APPLIED"), eq(null), anyBoolean(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> programApplicationService.apply(1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_APPLIED);
    }

    @Test
    void apply_whenActiveApplicationExists_throwsAlreadyAppliedWithoutInserting() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);
        ProgramApplication existing = buildApplicationFixture(5, program, "APPLIED", 100);

        mockValidProgramConsent(100, 900);
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByProgram_ProgramIdAndStudent_UserIdForUpdate(1, 100))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> programApplicationService.apply(1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_APPLIED);

        verify(applicationRepository, never()).insertApplication(anyInt(), anyInt(), any(), any(), anyBoolean(), any(), any());
        verify(applicationRepository, never()).reviveApplication(anyInt(), any(), any(), any(), any());
    }

    @Test
    void apply_whenRejectedApplicationExists_throwsAlreadyApplied() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);
        ProgramApplication existing = buildApplicationFixture(5, program, "REJECTED", 100);

        mockValidProgramConsent(100, 900);
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByProgram_ProgramIdAndStudent_UserIdForUpdate(1, 100))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> programApplicationService.apply(1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_APPLIED);

        verify(applicationRepository, never()).reviveApplication(anyInt(), any(), any(), any(), any());
    }

    @Test
    void apply_whenCancelledApplicationExists_revivesExistingRow() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);
        ProgramApplication existing = buildApplicationFixture(5, program, "CANCELLED", 100);

        mockValidProgramConsent(100, 900);
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByProgram_ProgramIdAndStudent_UserIdForUpdate(1, 100))
                .thenReturn(Optional.of(existing));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
                .thenReturn(0L);
        when(applicationRepository.reviveApplication(eq(5), eq("APPLIED"), eq(null), eq(900), any())).thenReturn(1);

        ProgramApplyResponseDTO response = programApplicationService.apply(1, 100);

        assertThat(response.applicationId()).isEqualTo(5);
        assertThat(response.applicationStatus()).isEqualTo("APPLIED");
        verify(applicationRepository, never()).insertApplication(anyInt(), anyInt(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void apply_whenReviveRaceLost_throwsAlreadyApplied() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);
        ProgramApplication existing = buildApplicationFixture(5, program, "CANCELLED", 100);

        mockValidProgramConsent(100, 900);
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByProgram_ProgramIdAndStudent_UserIdForUpdate(1, 100))
                .thenReturn(Optional.of(existing));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
                .thenReturn(0L);
        when(applicationRepository.reviveApplication(eq(5), eq("APPLIED"), eq(null), eq(900), any())).thenReturn(0);

        assertThatThrownBy(() -> programApplicationService.apply(1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_APPLIED);
    }

    @Test
    void apply_whenProgramNotFound_throwsProgramNotFound() throws Exception {
        mockValidProgramConsent(100, 900);
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programApplicationService.apply(1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
    }

    @Test
    void confirm_whenWithinCapacity_succeeds() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "WAITLISTED", 100);

        mockValidProgramConsent(100, 900);
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
                .thenReturn(9L);
        when(applicationRepository.confirmWaitlisted(eq(5), any())).thenReturn(1);

        ProgramApplyResponseDTO response = programApplicationService.confirm(1, 5, 100);

        assertThat(response.applicationId()).isEqualTo(5);
        assertThat(response.programId()).isEqualTo(1);
        assertThat(response.applicationStatus()).isEqualTo("APPLIED");
        assertThat(response.applicationStatusLabel()).isEqualTo("신청완료");
        assertThat(response.waitlistOrder()).isNull();
    }

    @Test
    void confirm_whenAtCapacity_throwsProgramCapacityExceeded() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "WAITLISTED", 100);

        mockValidProgramConsent(100, 900);
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
                .thenReturn(10L);

        assertThatThrownBy(() -> programApplicationService.confirm(1, 5, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_CAPACITY_EXCEEDED);

        verify(applicationRepository, never()).confirmWaitlisted(any(), any());
    }

    @Test
    void confirm_whenApplicationNotWaitlisted_throwsApplicationAlreadyProcessed() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED", 100);

        mockValidProgramConsent(100, 900);
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> programApplicationService.confirm(1, 5, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_ALREADY_PROCESSED);

        verify(applicationRepository, never()).confirmWaitlisted(any(), any());
    }

    // 메서드 진입 시 게이트(hasAgreedAllRequired)는 통과했지만(동의 락은 잡음), 프로그램/신청 행 락 대기와
    // 정원 계산으로 시간이 흐르는 사이 정책 유효기간이 만료되어 저장 직전 재검증에서 걸리는 경우를 재현한다.
    // 동의 락은 동시 withdraw()만 막을 뿐 시간 경과에 따른 유효기간 만료까지는 막지 못하므로, applyDecision
    // 직전에 hasAgreedAllRequired를 다시 확인하지 않으면 이미 만료된 동의로 승인이 저장되는 회귀가 생긴다.
    @Test
    void confirm_whenConsentNoLongerValidAtDecisionTime_throwsRequiredConsentNotAgreed() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "WAITLISTED", 100);

        // 첫 호출(메서드 진입 시 게이트) true, 두 번째 호출(applyDecision 직전 재검증) false 순으로 답하도록
        // 스텁한다 — mockValidProgramConsent()의 hasAgreedAllRequired 스텁을 그대로 쓰면 이 시퀀스로
        // 덮어써져 첫 스텁이 한 번도 안 쓰인 것으로 판정되어 UnnecessaryStubbingException이 나므로, 락
        // 재검증에 필요한 나머지 동의 목만 여기서 직접 세팅한다.
        Integer termsConsentId = 899;
        Integer personalInfoConsentId = 900;
        when(consentVerifier.hasAgreedAllRequired(eq(100), eq(ConsentModuleCode.PROGRAM), any()))
                .thenReturn(true, false);
        when(consentVerifier.findCurrentValidConsent(
                eq(100), eq(ConsentModuleCode.PROGRAM), eq(ConsentType.TERMS_OF_SERVICE), any()))
                .thenReturn(Optional.of(buildUserConsentFixture(termsConsentId)));
        when(consentVerifier.requireOwnedValidConsent(
                eq(termsConsentId), eq(100), eq(ConsentModuleCode.PROGRAM), eq(ConsentType.TERMS_OF_SERVICE), any()))
                .thenReturn(buildUserConsentFixture(termsConsentId));
        when(consentVerifier.findCurrentValidConsent(
                eq(100), eq(ConsentModuleCode.PROGRAM), eq(ConsentType.PERSONAL_INFO), any()))
                .thenReturn(Optional.of(buildUserConsentFixture(personalInfoConsentId)));
        when(consentVerifier.requireOwnedValidConsent(
                eq(personalInfoConsentId), eq(100), eq(ConsentModuleCode.PROGRAM), eq(ConsentType.PERSONAL_INFO), any()))
                .thenReturn(buildUserConsentFixture(personalInfoConsentId));
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
                .thenReturn(9L);

        assertThatThrownBy(() -> programApplicationService.confirm(1, 5, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REQUIRED_CONSENT_NOT_AGREED);

        verify(applicationRepository, never()).confirmWaitlisted(any(), any());
    }

    @Test
    void approve_whenWithinCapacity_succeeds() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "WAITLISTED", 100);

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
                .thenReturn(9L);

        ProgramApplicationDecisionResponseDTO response = programApplicationService.approve(1, 5, 200);

        assertThat(response.applicationStatus()).isEqualTo("APPROVED");
        assertThat(response.applicationStatusLabel()).isEqualTo("승인");
        assertThat(response.processedBy()).isEqualTo(200);
        verify(eventPublisher).publishEvent(argThat((Object e) -> e instanceof ApplicationDecidedEvent evt
                && "APPROVED".equals(evt.decisionStatus())
                && evt.studentId().equals(100)
                && evt.applicationId().equals(5)));
    }

    @Test
    void approve_whenCapacityFull_throwsCapacityExceeded() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "WAITLISTED");

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
                .thenReturn(10L);

        assertThatThrownBy(() -> programApplicationService.approve(1, 5, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_CAPACITY_EXCEEDED);
    }

    @Test
    void approve_whenPreviouslyApplied_skipsCapacityCheckEvenIfFull() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED", 100);

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));

        ProgramApplicationDecisionResponseDTO response = programApplicationService.approve(1, 5, 200);

        assertThat(response.applicationStatus()).isEqualTo("APPROVED");
        verify(applicationRepository, never())
                .countByProgram_ProgramIdAndApplicationStatusIn(anyInt(), any());
    }

    @Test
    void approve_whenAlreadyProcessed_throwsAlreadyProcessed() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPROVED");

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> programApplicationService.approve(1, 5, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_ALREADY_PROCESSED);
    }

    @Test
    void reject_succeeds_withReason() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED", 100);

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));

        ProgramApplicationDecisionResponseDTO response =
                programApplicationService.reject(1, 5, "정원 외 사유", 200);

        assertThat(response.applicationStatus()).isEqualTo("REJECTED");
        assertThat(response.applicationStatusLabel()).isEqualTo("반려");
        assertThat(response.decisionReason()).isEqualTo("정원 외 사유");
        verify(eventPublisher).publishEvent(any(WaitlistSlotOpenedEvent.class));
        verify(eventPublisher).publishEvent(argThat((Object e) -> e instanceof ApplicationDecidedEvent evt
                && "REJECTED".equals(evt.decisionStatus())
                && "정원 외 사유".equals(evt.reason())
                && evt.studentId().equals(100)));
    }

    @Test
    void reject_whenPreviouslyWaitlisted_doesNotPublishEvent() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "WAITLISTED", 100);

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));

        programApplicationService.reject(1, 5, "정원 외 사유", 200);

        verify(eventPublisher, never()).publishEvent(any(WaitlistSlotOpenedEvent.class));
    }

    @Test
    void reject_whenProgramNotFound_throwsProgramNotFound() {
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programApplicationService.reject(1, 5, "사유", 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
    }

    @Test
    void reject_whenApplicationNotFound_throwsApplicationNotFound() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programApplicationService.reject(1, 5, "사유", 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    /**
     * programId(999)와 applicationId(5)가 모두 잘못된 요청은, 프로그램 행을 먼저 잠그는 새 순서상
     * 신청 행을 조회하기도 전에 PROGRAM_NOT_FOUND로 끝난다(이전에는 APPLICATION_NOT_FOUND였음 —
     * cancel()과 같은 의도된 API 계약 변경).
     */
    @Test
    void reject_whenProgramIdWrong_throwsProgramNotFound() {
        when(programRepository.findByIdForUpdate(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programApplicationService.reject(999, 5, "사유", 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
    }

    @Test
    void reject_whenApplicationBelongsToOtherProgram_throwsApplicationNotFound() throws Exception {
        ExtracurricularProgram requestedProgram = buildProgramFixture(999, Instant.now(), Instant.now(), 10);
        ExtracurricularProgram actualProgram = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, actualProgram, "APPLIED");

        when(programRepository.findByIdForUpdate(999)).thenReturn(Optional.of(requestedProgram));
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

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));
        when(applicationRepository.updateCancellation(eq(5), eq("일정 변경"), any())).thenReturn(1);
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(eq(1), any()))
                .thenReturn(4L);

        ProgramApplicationCancelResponseDTO response =
                programApplicationService.cancel(1, 5, 100, "일정 변경");

        assertThat(response.applicationStatus()).isEqualTo("CANCELLED");
        assertThat(response.applicationStatusLabel()).isEqualTo("취소");
        assertThat(response.cancellationReason()).isEqualTo("일정 변경");
        assertThat(response.remainingCapacity()).isEqualTo(6);
        assertThat(response.recruitmentEndsAt()).isEqualTo(program.getRecruitmentEndsAt());
        verify(eventPublisher).publishEvent(any(WaitlistSlotOpenedEvent.class));
    }

    @Test
    void cancel_whenOccupiedCountExceedsCapacity_clampsRemainingCapacityToZero() throws Exception {
        // 정원(capacity)이 이미 신청된 인원(occupiedCount)보다 작게 수정된 뒤 취소된 경우,
        // remainingCapacity는 음수가 아니라 0으로 내려가야 한다.
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 3);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED", 100);

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));
        when(applicationRepository.updateCancellation(eq(5), eq("일정 변경"), any())).thenReturn(1);
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(eq(1), any()))
                .thenReturn(5L);

        ProgramApplicationCancelResponseDTO response =
                programApplicationService.cancel(1, 5, 100, "일정 변경");

        assertThat(response.remainingCapacity()).isZero();
    }

    @Test
    void cancel_whenApproved_succeeds() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(3600), now.plusSeconds(3600), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPROVED", 100);

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));
        when(applicationRepository.updateCancellation(eq(5), eq(null), any())).thenReturn(1);

        ProgramApplicationCancelResponseDTO response =
                programApplicationService.cancel(1, 5, 100, null);

        assertThat(response.applicationStatus()).isEqualTo("CANCELLED");
        verify(eventPublisher).publishEvent(any(WaitlistSlotOpenedEvent.class));
    }

    @Test
    void cancel_afterRecruitmentEnd_throwsApplicationNotCancelable() throws Exception {
        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, now.minusSeconds(7200), now.minusSeconds(3600), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED", 100);

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
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

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> programApplicationService.cancel(1, 5, 100, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_ALREADY_CANCELED);
    }

    @Test
    void cancel_whenProgramNotFound_throwsProgramNotFound() {
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programApplicationService.cancel(1, 5, 100, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
    }

    @Test
    void cancel_whenApplicationNotFound_throwsApplicationNotFound() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programApplicationService.cancel(1, 5, 100, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    /**
     * programId(999)와 applicationId(5)가 모두 잘못된 요청은, 프로그램 행을 먼저 잠그는 새 순서상
     * 신청 행을 조회하기도 전에 PROGRAM_NOT_FOUND로 끝난다(이전에는 APPLICATION_NOT_FOUND였음 —
     * 의도된 API 계약 변경, reject()도 동일).
     */
    @Test
    void cancel_whenProgramIdWrong_throwsProgramNotFound() {
        when(programRepository.findByIdForUpdate(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programApplicationService.cancel(999, 5, 100, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
    }

    @Test
    void cancel_whenApplicationBelongsToOtherProgram_throwsApplicationNotFound() throws Exception {
        ExtracurricularProgram requestedProgram = buildProgramFixture(999, Instant.now(), Instant.now(), 10);
        ExtracurricularProgram actualProgram = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, actualProgram, "APPLIED", 100);

        when(programRepository.findByIdForUpdate(999)).thenReturn(Optional.of(requestedProgram));
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

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
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

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(application));
        when(applicationRepository.updateCancellation(eq(5), eq(null), any())).thenReturn(0);

        assertThatThrownBy(() -> programApplicationService.cancel(1, 5, 100, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_CANCELABLE);
    }

    /**
     * notifyAllWaitlistedApplicantsOfOpenSlots는 cancel()의 트랜잭션 커밋 이후(@TransactionalEventListener
     * AFTER_COMMIT)에 실행되는 리스너라, cancel()을 거치지 않고 이벤트를 직접 넘겨 호출한다.
     */
    @Test
    void notifyAllWaitlistedApplicantsOfOpenSlots_whenSlotsOpen_sendsSlotCountToAllWaitlisted() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication first = buildApplicationFixture(7, program, "WAITLISTED", 200);
        ProgramApplication second = buildApplicationFixture(8, program, "WAITLISTED", 201);

        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
                .thenReturn(8L);
        when(applicationRepository.findAllByProgram_ProgramIdAndApplicationStatusOrderByWaitlistOrderAsc(1, "WAITLISTED"))
                .thenReturn(List.of(first, second));

        programApplicationService.notifyAllWaitlistedApplicantsOfOpenSlots(
                new WaitlistSlotOpenedEvent(1, "테스트 프로그램"));

        verify(notificationSender).send(argThat(request ->
                request.recipientUserId().equals(200)
                        && request.content().contains("테스트 프로그램")
                        && request.content().contains("2자리")));
        verify(notificationSender).send(argThat(request ->
                request.recipientUserId().equals(201)
                        && request.content().contains("2자리")));
    }

    @Test
    void notifyAllWaitlistedApplicantsOfOpenSlots_whenNoSlotsAvailable_sendsNothing() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);

        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
                .thenReturn(10L);

        programApplicationService.notifyAllWaitlistedApplicantsOfOpenSlots(
                new WaitlistSlotOpenedEvent(1, "테스트 프로그램"));

        verify(notificationSender, never()).send(any());
        verify(applicationRepository, never())
                .findAllByProgram_ProgramIdAndApplicationStatusOrderByWaitlistOrderAsc(any(), any());
    }

    @Test
    void notifyAllWaitlistedApplicantsOfOpenSlots_whenNoWaitlistedApplicant_sendsNothing() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);

        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
                .thenReturn(8L);
        when(applicationRepository.findAllByProgram_ProgramIdAndApplicationStatusOrderByWaitlistOrderAsc(1, "WAITLISTED"))
                .thenReturn(List.of());

        programApplicationService.notifyAllWaitlistedApplicantsOfOpenSlots(
                new WaitlistSlotOpenedEvent(1, "테스트 프로그램"));

        verify(notificationSender, never()).send(any());
    }

    @Test
    void notifyAllWaitlistedApplicantsOfOpenSlots_whenSendFailsForOne_swallowsExceptionAndNotifiesRest() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication first = buildApplicationFixture(7, program, "WAITLISTED", 200);
        ProgramApplication second = buildApplicationFixture(8, program, "WAITLISTED", 201);

        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
                .thenReturn(9L);
        when(applicationRepository.findAllByProgram_ProgramIdAndApplicationStatusOrderByWaitlistOrderAsc(1, "WAITLISTED"))
                .thenReturn(List.of(first, second));
        doThrow(new RuntimeException("발송 실패"))
                .when(notificationSender).send(argThat(request -> request.recipientUserId().equals(200)));

        assertThatCode(() -> programApplicationService.notifyAllWaitlistedApplicantsOfOpenSlots(
                new WaitlistSlotOpenedEvent(1, "테스트 프로그램")))
                .doesNotThrowAnyException();

        verify(notificationSender).send(argThat(request -> request.recipientUserId().equals(201)));
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
    void listMyApplications_withAttendanceAndMileageData_mapsAggregatesCorrectly() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ReflectionTestUtils.setField(program, "programName", "테스트 프로그램");

        Instant now = Instant.now();
        ProgramApplication first = buildApplicationSummaryFixture(
                1, program, "APPROVED", null, now, null, null, null, null, null, null, null);
        ProgramApplication second = buildApplicationSummaryFixture(
                2, program, "APPROVED", null, now, null, null, null, null, null, null, null);

        Pageable pageable = PageRequest.of(0, 10);
        Page<ProgramApplication> page = new PageImpl<>(List.of(first, second), pageable, 2);
        when(applicationRepository.findAllByStudentId(100, pageable)).thenReturn(page);

        ProgramAttendanceRepository.AttendanceCountProjection attendanceForFirst =
                mock(ProgramAttendanceRepository.AttendanceCountProjection.class);
        when(attendanceForFirst.getApplicationId()).thenReturn(1);
        when(attendanceForFirst.getTotalCount()).thenReturn(4L);
        when(attendanceForFirst.getPresentCount()).thenReturn(3L);

        ProgramAttendanceRepository.AttendanceCountProjection attendanceForSecond =
                mock(ProgramAttendanceRepository.AttendanceCountProjection.class);
        when(attendanceForSecond.getApplicationId()).thenReturn(2);
        when(attendanceForSecond.getTotalCount()).thenReturn(2L);
        when(attendanceForSecond.getPresentCount()).thenReturn(0L);

        when(attendanceRepository.countAttendanceByApplicationIds(List.of(1, 2)))
                .thenReturn(List.of(attendanceForFirst, attendanceForSecond));

        ProgramMileageTransactionRepository.EarnedPointsProjection pointsForFirst =
                mock(ProgramMileageTransactionRepository.EarnedPointsProjection.class);
        when(pointsForFirst.getApplicationId()).thenReturn(1);
        when(pointsForFirst.getPoints()).thenReturn(new BigDecimal("500"));

        when(mileageTransactionRepository.findPostedPointsByApplicationIds(List.of(1, 2)))
                .thenReturn(List.of(pointsForFirst));

        PageResponse<ProgramApplicationSummaryResponseDTO> response =
                programApplicationService.listMyApplications(100, pageable);

        ProgramApplicationSummaryResponseDTO firstDto = response.content().get(0);
        assertThat(firstDto.totalAttendanceCount()).isEqualTo(4);
        assertThat(firstDto.presentAttendanceCount()).isEqualTo(3);
        assertThat(firstDto.attendanceRate()).isEqualTo(75.0);
        assertThat(firstDto.earnedMileagePoints()).isEqualByComparingTo("500");

        ProgramApplicationSummaryResponseDTO secondDto = response.content().get(1);
        assertThat(secondDto.totalAttendanceCount()).isEqualTo(2);
        assertThat(secondDto.presentAttendanceCount()).isEqualTo(0);
        assertThat(secondDto.attendanceRate()).isEqualTo(0.0);
        assertThat(secondDto.earnedMileagePoints()).isNull();

        verify(attendanceRepository).countAttendanceByApplicationIds(List.of(1, 2));
        verify(mileageTransactionRepository).findPostedPointsByApplicationIds(List.of(1, 2));
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
        ProgramApplication succeeding = buildApplicationFixture(5, program, "WAITLISTED", 100);
        ProgramApplication failing = buildApplicationFixture(6, program, "WAITLISTED", 101);

        when(applicationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(succeeding));
        when(applicationRepository.findByIdForUpdate(6)).thenReturn(Optional.of(failing));
        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
        // 첫 번째 건을 승인하는 순간 정원이 다 찼다고 가정 — 두 번째 건은 승인 시도 시 정원초과로 실패해야 한다.
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(1, List.of("APPLIED", "APPROVED")))
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
        ProgramApplication first = buildApplicationFixture(5, program, "APPLIED", 100);
        ProgramApplication second = buildApplicationFixture(6, program, "APPLIED", 101);

        when(programRepository.findByIdForUpdate(1)).thenReturn(Optional.of(program));
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
        when(applicationRepository.findAllByProgramIdAndStatus(1, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(application), pageable, 1));

        PageResponse<ProgramApplicationAdminListItemResponseDTO> response =
                programApplicationService.listByProgram(1, null, null, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).studentName()).isEqualTo("홍길동");
        assertThat(response.content().get(0).studentNo()).isEqualTo("2021000123");
    }

    @Test
    void listByProgram_whenKeywordBlank_searchesWithoutKeyword() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1, Instant.now(), Instant.now(), 10);
        ProgramApplication application = buildApplicationFixture(5, program, "APPLIED", 100);
        ReflectionTestUtils.setField(application.getStudent(), "userName", "홍길동");
        ReflectionTestUtils.setField(application.getStudent(), "universityNo", "2021000123");

        Pageable pageable = PageRequest.of(0, 20);
        when(programRepository.existsById(1)).thenReturn(true);
        when(applicationRepository.findAllByProgramIdAndStatus(1, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(application), pageable, 1));

        PageResponse<ProgramApplicationAdminListItemResponseDTO> response =
                programApplicationService.listByProgram(1, null, "  ", pageable);

        assertThat(response.content()).hasSize(1);
    }

    @Test
    void listByProgram_whenKeywordGiven_passesTrimmedKeywordToRepository() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(programRepository.existsById(1)).thenReturn(true);
        when(applicationRepository.findAllByProgramIdAndStatus(1, null, "홍길동", pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<ProgramApplicationAdminListItemResponseDTO> response =
                programApplicationService.listByProgram(1, null, "  홍길동  ", pageable);

        assertThat(response.content()).isEmpty();
        verify(applicationRepository).findAllByProgramIdAndStatus(1, null, "홍길동", pageable);
    }

    @Test
    void listByProgram_whenKeywordContainsLikeWildcards_escapesBeforePassingToRepository() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(programRepository.existsById(1)).thenReturn(true);
        when(applicationRepository.findAllByProgramIdAndStatus(1, null, "100!%!_!!off", pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<ProgramApplicationAdminListItemResponseDTO> response =
                programApplicationService.listByProgram(1, null, "100%_!off", pageable);

        assertThat(response.content()).isEmpty();
        verify(applicationRepository).findAllByProgramIdAndStatus(1, null, "100!%!_!!off", pageable);
    }

    @Test
    void listByProgram_whenProgramNotFound_throwsProgramNotFound() {
        when(programRepository.existsById(1)).thenReturn(false);

        assertThatThrownBy(() -> programApplicationService.listByProgram(1, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
    }

    // apply() 맨 앞의 동의 게이트를 통과시키고, FK 증빙으로 쓰일 UserConsent를 반환하도록 목을 세팅한다.
    // findCurrentValidConsent(잠금 없음)로 후보 ID를 찾은 뒤 requireOwnedValidConsent(락+재검증)로
    // 같은 ID를 다시 조회하므로, 둘 다 같은 userConsentId를 반환하도록 목을 세팅해야 한다. apply()는
    // TERMS_OF_SERVICE도 같은 방식으로 잠금 재검증하므로, 별도 userConsentId로 함께 세팅해 둔다.
    private void mockValidProgramConsent(Integer studentId, Integer userConsentId) throws Exception {
        Integer termsConsentId = userConsentId - 1;
        when(consentVerifier.hasAgreedAllRequired(eq(studentId), eq(ConsentModuleCode.PROGRAM), any()))
                .thenReturn(true);
        when(consentVerifier.findCurrentValidConsent(
                eq(studentId), eq(ConsentModuleCode.PROGRAM), eq(ConsentType.TERMS_OF_SERVICE), any()))
                .thenReturn(Optional.of(buildUserConsentFixture(termsConsentId)));
        when(consentVerifier.requireOwnedValidConsent(
                eq(termsConsentId), eq(studentId), eq(ConsentModuleCode.PROGRAM), eq(ConsentType.TERMS_OF_SERVICE), any()))
                .thenReturn(buildUserConsentFixture(termsConsentId));
        when(consentVerifier.findCurrentValidConsent(
                eq(studentId), eq(ConsentModuleCode.PROGRAM), eq(ConsentType.PERSONAL_INFO), any()))
                .thenReturn(Optional.of(buildUserConsentFixture(userConsentId)));
        when(consentVerifier.requireOwnedValidConsent(
                eq(userConsentId), eq(studentId), eq(ConsentModuleCode.PROGRAM), eq(ConsentType.PERSONAL_INFO), any()))
                .thenReturn(buildUserConsentFixture(userConsentId));
    }

    // UserConsent도 같은 이유(protected 기본 생성자, setter/빌더 없음)로 리플렉션 픽스처를 사용한다.
    private UserConsent buildUserConsentFixture(Integer userConsentId) throws Exception {
        Constructor<UserConsent> constructor = UserConsent.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        UserConsent consent = constructor.newInstance();
        ReflectionTestUtils.setField(consent, "userConsentId", userConsentId);
        return consent;
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
