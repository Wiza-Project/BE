package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.program.dto.request.ProgramAttendanceRecordRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramAttendanceResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramMyAttendanceResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.domain.program.entity.ProgramAttendance;
import com.gnagnoohc.scms.domain.program.entity.ProgramSession;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramAttendanceRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramSessionRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramAttendanceServiceTest {

    @Mock
    ProgramSessionRepository sessionRepository;

    @Mock
    ProgramApplicationRepository applicationRepository;

    @Mock
    ProgramAttendanceRepository attendanceRepository;

    @InjectMocks
    ProgramAttendanceService programAttendanceService;

    @Test
    void recordAttendance_whenApplicationApproved_upsertsAndReturnsAttendance() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1);
        ProgramSession session = buildSessionFixture(10, program, 1);
        ProgramApplication application = buildApplicationFixture(5, program, "APPROVED");
        ProgramAttendance attendance = buildAttendanceFixture(99, application, session, "PRESENT");
        ProgramAttendanceRecordRequestDTO request = new ProgramAttendanceRecordRequestDTO("PRESENT", 90, null);

        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1)).thenReturn(Optional.of(session));
        when(applicationRepository.findById(5)).thenReturn(Optional.of(application));
        when(attendanceRepository.findByApplication_ApplicationIdAndProgramSession_ProgramSessionId(5, 10))
                .thenReturn(Optional.of(attendance));

        ProgramAttendanceResponseDTO response = programAttendanceService.recordAttendance(1, 10, 5, request, 200);

        assertThat(response.attendanceId()).isEqualTo(99);
        assertThat(response.attendanceStatus()).isEqualTo("PRESENT");
    }

    @Test
    void recordAttendance_whenSessionNotFound_throwsProgramSessionNotFound() {
        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1)).thenReturn(Optional.empty());

        ProgramAttendanceRecordRequestDTO request = new ProgramAttendanceRecordRequestDTO("PRESENT", null, null);

        assertThatThrownBy(() -> programAttendanceService.recordAttendance(1, 10, 5, request, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_SESSION_NOT_FOUND);
    }

    @Test
    void recordAttendance_whenApplicationNotFound_throwsApplicationNotFound() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1);
        ProgramSession session = buildSessionFixture(10, program, 1);

        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1)).thenReturn(Optional.of(session));
        when(applicationRepository.findById(5)).thenReturn(Optional.empty());

        ProgramAttendanceRecordRequestDTO request = new ProgramAttendanceRecordRequestDTO("PRESENT", null, null);

        assertThatThrownBy(() -> programAttendanceService.recordAttendance(1, 10, 5, request, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    void recordAttendance_whenApplicationBelongsToOtherProgram_throwsApplicationNotFound() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1);
        ExtracurricularProgram otherProgram = buildProgramFixture(999);
        ProgramSession session = buildSessionFixture(10, program, 1);
        ProgramApplication application = buildApplicationFixture(5, otherProgram, "APPROVED");

        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1)).thenReturn(Optional.of(session));
        when(applicationRepository.findById(5)).thenReturn(Optional.of(application));

        ProgramAttendanceRecordRequestDTO request = new ProgramAttendanceRecordRequestDTO("PRESENT", null, null);

        assertThatThrownBy(() -> programAttendanceService.recordAttendance(1, 10, 5, request, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    void recordAttendance_whenApplicationNotApproved_throwsApplicationNotApproved() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1);
        ProgramSession session = buildSessionFixture(10, program, 1);
        ProgramApplication application = buildApplicationFixture(5, program, "WAITLISTED");

        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1)).thenReturn(Optional.of(session));
        when(applicationRepository.findById(5)).thenReturn(Optional.of(application));

        ProgramAttendanceRecordRequestDTO request = new ProgramAttendanceRecordRequestDTO("PRESENT", null, null);

        assertThatThrownBy(() -> programAttendanceService.recordAttendance(1, 10, 5, request, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_APPROVED);
    }

    @Test
    void recordAttendance_whenAttendanceStatusInvalid_throwsInvalidInput() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1);
        ProgramSession session = buildSessionFixture(10, program, 1);
        ProgramApplication application = buildApplicationFixture(5, program, "APPROVED");

        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1)).thenReturn(Optional.of(session));
        when(applicationRepository.findById(5)).thenReturn(Optional.of(application));

        ProgramAttendanceRecordRequestDTO request = new ProgramAttendanceRecordRequestDTO("UNKNOWN", null, null);

        assertThatThrownBy(() -> programAttendanceService.recordAttendance(1, 10, 5, request, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void listAttendance_whenSessionExists_returnsAttendanceList() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1);
        ProgramSession session = buildSessionFixture(10, program, 1);
        ProgramApplication application = buildApplicationFixture(5, program, "APPROVED");
        ProgramAttendance attendance = buildAttendanceFixture(99, application, session, "ABSENT");

        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1)).thenReturn(Optional.of(session));
        when(attendanceRepository.findByProgramSession_ProgramSessionId(10)).thenReturn(List.of(attendance));

        List<ProgramAttendanceResponseDTO> response = programAttendanceService.listAttendance(1, 10);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).attendanceStatus()).isEqualTo("ABSENT");
    }

    @Test
    void listAttendance_whenSessionNotFound_throwsProgramSessionNotFound() {
        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programAttendanceService.listAttendance(1, 10))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_SESSION_NOT_FOUND);
    }

    @Test
    void listMyAttendance_whenSomeSessionsUnrecorded_fillsUnrecordedSessionsWithNull() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1);
        ProgramSession session1 = buildSessionFixture(10, program, 1);
        ProgramSession session2 = buildSessionFixture(11, program, 2);
        ProgramApplication application = buildApplicationFixture(5, program, "APPROVED");
        ProgramAttendance attendance1 = buildAttendanceFixture(99, application, session1, "PRESENT");

        when(applicationRepository.findByProgram_ProgramIdAndStudent_UserId(1, 100)).thenReturn(Optional.of(application));
        when(sessionRepository.findByProgram_ProgramIdOrderBySessionNoAsc(1)).thenReturn(List.of(session1, session2));
        when(attendanceRepository.findByApplication_ApplicationId(5)).thenReturn(List.of(attendance1));

        List<ProgramMyAttendanceResponseDTO> response = programAttendanceService.listMyAttendance(1, 100);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).programSessionId()).isEqualTo(10);
        assertThat(response.get(0).attendanceStatus()).isEqualTo("PRESENT");
        assertThat(response.get(1).programSessionId()).isEqualTo(11);
        assertThat(response.get(1).attendanceStatus()).isNull();
    }

    @Test
    void listMyAttendance_whenApplicationNotFound_throwsApplicationNotFound() {
        when(applicationRepository.findByProgram_ProgramIdAndStudent_UserId(1, 100)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programAttendanceService.listMyAttendance(1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    /**
     * 관련 엔티티들은 모두 protected 기본 생성자만 있고 setter/빌더가 없어(네이티브 SQL로만 값을 채우는 구조),
     * 테스트 픽스처는 리플렉션으로 생성한다 (ProgramApplicationServiceTest 참고).
     */
    private ExtracurricularProgram buildProgramFixture(Integer programId) throws Exception {
        Constructor<ExtracurricularProgram> constructor = ExtracurricularProgram.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ExtracurricularProgram program = constructor.newInstance();
        ReflectionTestUtils.setField(program, "programId", programId);
        return program;
    }

    private ProgramSession buildSessionFixture(Integer programSessionId, ExtracurricularProgram program, Integer sessionNo) throws Exception {
        Constructor<ProgramSession> constructor = ProgramSession.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ProgramSession session = constructor.newInstance();
        ReflectionTestUtils.setField(session, "programSessionId", programSessionId);
        ReflectionTestUtils.setField(session, "program", program);
        ReflectionTestUtils.setField(session, "sessionNo", sessionNo);
        return session;
    }

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

    private ProgramAttendance buildAttendanceFixture(
            Integer attendanceId, ProgramApplication application, ProgramSession session, String attendanceStatus) throws Exception {
        Constructor<ProgramAttendance> constructor = ProgramAttendance.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ProgramAttendance attendance = constructor.newInstance();
        ReflectionTestUtils.setField(attendance, "attendanceId", attendanceId);
        ReflectionTestUtils.setField(attendance, "application", application);
        ReflectionTestUtils.setField(attendance, "programSession", session);
        ReflectionTestUtils.setField(attendance, "attendanceStatus", attendanceStatus);
        return attendance;
    }
}
