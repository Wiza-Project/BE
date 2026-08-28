package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.program.dto.request.ProgramSessionRegisterRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.request.ProgramSessionUpdateRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramSessionResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramSession;
import com.gnagnoohc.scms.domain.program.entity.SessionLocationType;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramSessionRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramSessionServiceTest {

    @Mock
    ExtracurricularProgramRepository programRepository;

    @Mock
    ProgramSessionRepository sessionRepository;

    @Mock
    CommonCodeRepository commonCodeRepository;

    @InjectMocks
    ProgramSessionService programSessionService;

    @Test
    void registerSession_whenProgramExists_returnsRegisteredSession() {
        Instant startsAt = Instant.now().plusSeconds(3600);
        Instant endsAt = startsAt.plusSeconds(3600);
        ProgramSessionRegisterRequestDTO request =
                new ProgramSessionRegisterRequestDTO(1, "1주차", startsAt, endsAt, SessionLocationType.DIRECT_INPUT, "본관 101호");

        when(programRepository.existsById(1)).thenReturn(true);
        when(sessionRepository.insertSession(
                eq(1), eq(1), eq("1주차"), eq(startsAt), eq(endsAt), eq("본관 101호"), eq(200), any()))
                .thenReturn(10);

        ProgramSessionResponseDTO response = programSessionService.registerSession(1, request, 200);

        assertThat(response.programSessionId()).isEqualTo(10);
        assertThat(response.programId()).isEqualTo(1);
        assertThat(response.sessionNo()).isEqualTo(1);
    }

    @Test
    void registerSession_whenProgramNotFound_throwsProgramNotFound() {
        ProgramSessionRegisterRequestDTO request =
                new ProgramSessionRegisterRequestDTO(1, "1주차", Instant.now(), Instant.now().plusSeconds(3600),
                        SessionLocationType.DIRECT_INPUT, null);

        when(programRepository.existsById(1)).thenReturn(false);

        assertThatThrownBy(() -> programSessionService.registerSession(1, request, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
    }

    @Test
    void registerSession_whenSessionNoDuplicate_throwsDuplicateSessionNo() {
        ProgramSessionRegisterRequestDTO request =
                new ProgramSessionRegisterRequestDTO(1, "1주차", Instant.now(), Instant.now().plusSeconds(3600),
                        SessionLocationType.DIRECT_INPUT, "본관 101호");

        when(programRepository.existsById(1)).thenReturn(true);
        when(sessionRepository.insertSession(eq(1), eq(1), any(), any(), any(), any(), eq(200), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> programSessionService.registerSession(1, request, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_SESSION_NO);
    }

    @Test
    void registerSession_whenPeriodInvalid_throwsProgramInvalidPeriod() {
        Instant startsAt = Instant.now().plusSeconds(3600);
        Instant endsAt = startsAt.minusSeconds(1);
        ProgramSessionRegisterRequestDTO request =
                new ProgramSessionRegisterRequestDTO(1, "1주차", startsAt, endsAt, SessionLocationType.DIRECT_INPUT, null);

        when(programRepository.existsById(1)).thenReturn(true);

        assertThatThrownBy(() -> programSessionService.registerSession(1, request, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_INVALID_PERIOD);
    }

    @Test
    void registerSession_whenDirectInputAndLocationBlank_throwsSessionLocationRequired() {
        ProgramSessionRegisterRequestDTO request = new ProgramSessionRegisterRequestDTO(
                2, "2주차", Instant.now(), Instant.now().plusSeconds(3600), SessionLocationType.DIRECT_INPUT, "  ");

        when(programRepository.existsById(1)).thenReturn(true);

        assertThatThrownBy(() -> programSessionService.registerSession(1, request, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SESSION_LOCATION_REQUIRED);
    }

    @Test
    void registerSession_whenSameAsPreviousAndPreviousExists_copiesPreviousLocation() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1);
        ProgramSession previousSession = buildSessionFixture(10, program, 1);
        ReflectionTestUtils.setField(previousSession, "location", "본관 101호");
        Instant startsAt = Instant.now().plusSeconds(3600);
        Instant endsAt = startsAt.plusSeconds(3600);
        ProgramSessionRegisterRequestDTO request = new ProgramSessionRegisterRequestDTO(
                2, "2주차", startsAt, endsAt, SessionLocationType.SAME_AS_PREVIOUS, null);

        when(programRepository.existsById(1)).thenReturn(true);
        when(sessionRepository.findByProgram_ProgramIdAndSessionNo(1, 1))
                .thenReturn(java.util.Optional.of(previousSession));
        when(sessionRepository.insertSession(
                eq(1), eq(2), eq("2주차"), eq(startsAt), eq(endsAt), eq("본관 101호"), eq(200), any()))
                .thenReturn(11);

        ProgramSessionResponseDTO response = programSessionService.registerSession(1, request, 200);

        assertThat(response.location()).isEqualTo("본관 101호");
    }

    @Test
    void registerSession_whenSameAsPreviousAndNoPreviousSession_throwsPreviousSessionLocationNotFound() {
        ProgramSessionRegisterRequestDTO request = new ProgramSessionRegisterRequestDTO(
                1, "1주차", Instant.now(), Instant.now().plusSeconds(3600), SessionLocationType.SAME_AS_PREVIOUS, null);

        when(programRepository.existsById(1)).thenReturn(true);
        when(sessionRepository.findByProgram_ProgramIdAndSessionNo(1, 0))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> programSessionService.registerSession(1, request, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PREVIOUS_SESSION_LOCATION_NOT_FOUND);
    }

    @Test
    void listSessions_whenProgramExists_returnsSessionsOrderedByFixtureOrder() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(1);
        ProgramSession session = buildSessionFixture(10, program, 1);

        when(programRepository.existsById(1)).thenReturn(true);
        when(sessionRepository.findByProgram_ProgramIdOrderBySessionNoAsc(1)).thenReturn(List.of(session));

        List<ProgramSessionResponseDTO> response = programSessionService.listSessions(1);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).programSessionId()).isEqualTo(10);
    }

    @Test
    void listSessions_whenProgramNotFound_throwsProgramNotFound() {
        when(programRepository.existsById(1)).thenReturn(false);

        assertThatThrownBy(() -> programSessionService.listSessions(1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
    }

    @Test
    void updateSession_whenSessionExists_returnsUpdatedSession() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserId()).thenReturn(200);
        ExtracurricularProgram program = buildProgramFixture(1, managerUser);
        ProgramSession session = buildSessionFixture(10, program, 1);
        Instant startsAt = Instant.now().plusSeconds(3600);
        Instant endsAt = startsAt.plusSeconds(3600);
        ProgramSessionUpdateRequestDTO request =
                new ProgramSessionUpdateRequestDTO(1, "1주차(수정)", startsAt, endsAt,
                        SessionLocationType.DIRECT_INPUT, "본관 202호");

        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1))
                .thenReturn(java.util.Optional.of(session));
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));
        when(sessionRepository.updateSession(10, 1, 1, "1주차(수정)", startsAt, endsAt, "본관 202호"))
                .thenReturn(1);

        ProgramSessionResponseDTO response = programSessionService.updateSession(1, 10, request, 200, 11);

        assertThat(response.programSessionId()).isEqualTo(10);
        assertThat(response.sessionName()).isEqualTo("1주차(수정)");
        assertThat(response.location()).isEqualTo("본관 202호");
    }

    @Test
    void updateSession_whenSessionNotFound_throwsProgramSessionNotFound() {
        ProgramSessionUpdateRequestDTO request =
                new ProgramSessionUpdateRequestDTO(1, null, Instant.now(), Instant.now().plusSeconds(3600),
                        SessionLocationType.DIRECT_INPUT, null);

        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> programSessionService.updateSession(1, 10, request, 200, 11))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_SESSION_NOT_FOUND);
    }

    @Test
    void updateSession_whenNotOperatingDepartment_throwsDepartmentForbidden() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        ExtracurricularProgram program = buildProgramFixture(1, managerUser);
        ProgramSession session = buildSessionFixture(10, program, 1);
        ProgramSessionUpdateRequestDTO request =
                new ProgramSessionUpdateRequestDTO(1, null, Instant.now(), Instant.now().plusSeconds(3600),
                        SessionLocationType.DIRECT_INPUT, null);

        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1))
                .thenReturn(java.util.Optional.of(session));
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        assertThatThrownBy(() -> programSessionService.updateSession(1, 10, request, 200, 99))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_FORBIDDEN);
    }

    @Test
    void updateSession_whenNotOwner_throwsForbidden() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserId()).thenReturn(200);
        ExtracurricularProgram program = buildProgramFixture(1, managerUser);
        ProgramSession session = buildSessionFixture(10, program, 1);
        ProgramSessionUpdateRequestDTO request =
                new ProgramSessionUpdateRequestDTO(1, null, Instant.now(), Instant.now().plusSeconds(3600),
                        SessionLocationType.DIRECT_INPUT, null);

        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1))
                .thenReturn(java.util.Optional.of(session));
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        assertThatThrownBy(() -> programSessionService.updateSession(1, 10, request, 999, 11))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void updateSession_whenPeriodInvalid_throwsProgramInvalidPeriod() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserId()).thenReturn(200);
        ExtracurricularProgram program = buildProgramFixture(1, managerUser);
        ProgramSession session = buildSessionFixture(10, program, 1);
        Instant startsAt = Instant.now().plusSeconds(3600);
        Instant endsAt = startsAt.minusSeconds(1);
        ProgramSessionUpdateRequestDTO request =
                new ProgramSessionUpdateRequestDTO(1, null, startsAt, endsAt, SessionLocationType.DIRECT_INPUT, null);

        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1))
                .thenReturn(java.util.Optional.of(session));
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        assertThatThrownBy(() -> programSessionService.updateSession(1, 10, request, 200, 11))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_INVALID_PERIOD);
    }

    // resolveLocation()이 sessionNo 변경 시 자기 자신을 "직전 회차"로 잘못 조회하지 않는지 검증한다
    // (1회차 -> 2회차로 번호를 바꾸는 경우, sessionNo=1 조회에서 수정 대상 자신이 걸리면 안 된다).
    @Test
    void updateSession_whenSessionNoChangedToNext_excludesSelfFromPreviousSessionLookup() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserId()).thenReturn(200);
        ExtracurricularProgram program = buildProgramFixture(1, managerUser);
        ProgramSession session = buildSessionFixture(10, program, 1);
        ProgramSession realPreviousSession = buildSessionFixture(9, program, 1);
        ReflectionTestUtils.setField(realPreviousSession, "location", "본관 101호");
        Instant startsAt = Instant.now().plusSeconds(3600);
        Instant endsAt = startsAt.plusSeconds(3600);
        ProgramSessionUpdateRequestDTO request = new ProgramSessionUpdateRequestDTO(
                2, "2주차", startsAt, endsAt, SessionLocationType.SAME_AS_PREVIOUS, null);

        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1))
                .thenReturn(java.util.Optional.of(session));
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));
        when(sessionRepository.findByProgram_ProgramIdAndSessionNoAndProgramSessionIdNot(1, 1, 10))
                .thenReturn(java.util.Optional.of(realPreviousSession));
        when(sessionRepository.updateSession(10, 1, 2, "2주차", startsAt, endsAt, "본관 101호"))
                .thenReturn(1);

        ProgramSessionResponseDTO response = programSessionService.updateSession(1, 10, request, 200, 11);

        assertThat(response.location()).isEqualTo("본관 101호");
    }

    // 자기 자신을 제외하고 나면 진짜 직전 회차가 없는 경우(예: 1회차 하나뿐인 프로그램에서 2회차로 번호만 바꾸는 경우)
    // 자기 자신의 옛 장소를 잘못 복사하지 않고 PREVIOUS_SESSION_LOCATION_NOT_FOUND로 거부해야 한다.
    @Test
    void updateSession_whenSessionNoChangedAndNoOtherPreviousSession_throwsPreviousSessionLocationNotFound() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserId()).thenReturn(200);
        ExtracurricularProgram program = buildProgramFixture(1, managerUser);
        ProgramSession session = buildSessionFixture(10, program, 1);
        Instant startsAt = Instant.now().plusSeconds(3600);
        Instant endsAt = startsAt.plusSeconds(3600);
        ProgramSessionUpdateRequestDTO request = new ProgramSessionUpdateRequestDTO(
                2, "2주차", startsAt, endsAt, SessionLocationType.SAME_AS_PREVIOUS, null);

        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1))
                .thenReturn(java.util.Optional.of(session));
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));
        when(sessionRepository.findByProgram_ProgramIdAndSessionNoAndProgramSessionIdNot(1, 1, 10))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> programSessionService.updateSession(1, 10, request, 200, 11))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PREVIOUS_SESSION_LOCATION_NOT_FOUND);
    }

    @Test
    void updateSession_whenSessionNoDuplicate_throwsDuplicateSessionNo() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserId()).thenReturn(200);
        ExtracurricularProgram program = buildProgramFixture(1, managerUser);
        ProgramSession session = buildSessionFixture(10, program, 1);
        ProgramSessionUpdateRequestDTO request =
                new ProgramSessionUpdateRequestDTO(2, null, Instant.now(), Instant.now().plusSeconds(3600),
                        SessionLocationType.DIRECT_INPUT, "본관 101호");

        when(sessionRepository.findByProgramSessionIdAndProgram_ProgramId(10, 1))
                .thenReturn(java.util.Optional.of(session));
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));
        when(sessionRepository.updateSession(eq(10), eq(1), eq(2), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> programSessionService.updateSession(1, 10, request, 200, 11))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_SESSION_NO);
    }

    /**
     * ExtracurricularProgram/ProgramSession은 protected 기본 생성자만 있고 setter/빌더가 없어(네이티브 SQL로만
     * 값을 채우는 구조), 테스트 픽스처는 리플렉션으로 생성한다 (ProgramApplicationServiceTest 참고).
     */
    private ExtracurricularProgram buildProgramFixture(Integer programId) throws Exception {
        return buildProgramFixture(programId, null);
    }

    private ExtracurricularProgram buildProgramFixture(Integer programId, AppUser managerUser) throws Exception {
        Constructor<ExtracurricularProgram> constructor = ExtracurricularProgram.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ExtracurricularProgram program = constructor.newInstance();
        ReflectionTestUtils.setField(program, "programId", programId);
        ReflectionTestUtils.setField(program, "managerUser", managerUser);
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

    /**
     * CommonCode도 ExtracurricularProgram과 동일하게 protected 기본 생성자만 있고 setter가 없어,
     * 위 buildProgramFixture와 같은 방식으로 리플렉션을 통해 픽스처를 만든다(ProgramServiceTest 참고).
     */
    private CommonCode buildCommonCodeFixture(Integer codeId, String codeGroup, String code) throws Exception {
        Constructor<CommonCode> constructor = CommonCode.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        CommonCode commonCode = constructor.newInstance();
        ReflectionTestUtils.setField(commonCode, "codeId", codeId);
        ReflectionTestUtils.setField(commonCode, "codeGroup", codeGroup);
        ReflectionTestUtils.setField(commonCode, "code", code);
        return commonCode;
    }
}
