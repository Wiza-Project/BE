package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.program.dto.ProgramSessionRegisterRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.ProgramSessionResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramSession;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramSessionRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramSessionServiceTest {

    @Mock
    ExtracurricularProgramRepository programRepository;

    @Mock
    ProgramSessionRepository sessionRepository;

    @InjectMocks
    ProgramSessionService programSessionService;

    @Test
    void registerSession_whenProgramExists_returnsRegisteredSession() {
        Instant startsAt = Instant.now().plusSeconds(3600);
        Instant endsAt = startsAt.plusSeconds(3600);
        ProgramSessionRegisterRequestDTO request =
                new ProgramSessionRegisterRequestDTO(1, "1주차", startsAt, endsAt, "본관 101호");

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
                new ProgramSessionRegisterRequestDTO(1, "1주차", Instant.now(), Instant.now().plusSeconds(3600), null);

        when(programRepository.existsById(1)).thenReturn(false);

        assertThatThrownBy(() -> programSessionService.registerSession(1, request, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
    }

    @Test
    void registerSession_whenSessionNoDuplicate_throwsDuplicateSessionNo() {
        ProgramSessionRegisterRequestDTO request =
                new ProgramSessionRegisterRequestDTO(1, "1주차", Instant.now(), Instant.now().plusSeconds(3600), null);

        when(programRepository.existsById(1)).thenReturn(true);
        when(sessionRepository.insertSession(eq(1), eq(1), any(), any(), any(), any(), eq(200), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> programSessionService.registerSession(1, request, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_SESSION_NO);
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

    // ExtracurricularProgram/ProgramSession은 protected 기본 생성자만 있고 setter/빌더가 없어(네이티브 SQL로만
    // 값을 채우는 구조), 테스트 픽스처는 리플렉션으로 생성한다 (ProgramApplicationServiceTest 참고).
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
}
