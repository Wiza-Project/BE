package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.program.dto.request.ProgramSessionRegisterRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.request.ProgramSessionUpdateRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramSessionResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramSession;
import com.gnagnoohc.scms.domain.program.entity.SessionLocationType;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramSessionRepository;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProgramSessionService {

    /**
     * 로그인한 사용자가 비교과운영부서 소속인지 검증(isOperatingDepartment)할 때 기준이 되는 CommonCode 값.
     * ProgramService와 동일한 기준(CommonCodeSeeder 기준 비교과운영부서=D200)이다.
     */
    private static final String DEPARTMENT_GROUP = "DEPARTMENT";
    private static final String DEFAULT_DEPARTMENT_CODE = "D200"; // 비교과운영부서

    private final ExtracurricularProgramRepository programRepository;
    private final ProgramSessionRepository sessionRepository;
    private final CommonCodeRepository commonCodeRepository;

    /**
     * 운영부서가 프로그램의 회차(교육 일정)를 등록한다. 매개변수 3개의 의미:
     *   programId : 회차를 등록할 프로그램의 PK (URL 경로에서 옴)
     *   request   : 등록할 회차 내용 (요청 바디에서 옴)
     *   staffId   : 지금 로그인해서 이 요청을 보낸 운영부서 담당자의 id (인증 정보에서 옴)
     *   departmentCodeId : 로그인한 사용자가 소속된 부서의 CommonCode PK (인증 정보에서 옴)
     *                    → updateSession()과 동일하게, 비교과운영부서(D200) 소속 + 프로그램 등록자 본인인지 검증한다.
     */
    public ProgramSessionResponseDTO registerSession(Integer programId, ProgramSessionRegisterRequestDTO request,
                                                       Integer staffId, Integer departmentCodeId) {
        ExtracurricularProgram program = programRepository.findById(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        if (!isOperatingDepartment(departmentCodeId)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_FORBIDDEN);
        }
        if (!program.getManagerUser().getUserId().equals(staffId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (!request.startsAt().isBefore(request.endsAt())) {
            throw new BusinessException(ErrorCode.PROGRAM_INVALID_PERIOD);
        }

        /**
         * 회차 번호는 1부터 빈 번호 없이 연속되어야 한다(ProgramService.register()의 일괄 등록과 동일한
         * 정책). 그래야 SAME_AS_PREVIOUS가 참조하는 sessionNo - 1이 항상 존재를 보장받는다. 새 회차는
         * 항상 "현재 회차 수 + 1" 번호로만 추가할 수 있다(중간에 끼워넣거나 건너뛸 수 없음, append만 허용).
         */
        long existingCount = sessionRepository.countByProgram_ProgramId(programId);
        if (!request.sessionNo().equals((int) existingCount + 1)) {
            throw new BusinessException(ErrorCode.PROGRAM_SESSION_NO_NOT_CONTIGUOUS);
        }

        String location = resolveLocation(programId, request.sessionNo(), request.locationType(), request.location(), null);

        Instant now = Instant.now();
        Integer sessionId;
        try {
            sessionId = sessionRepository.insertSession(
                    programId, request.sessionNo(), request.sessionName(),
                    request.startsAt(), request.endsAt(), location, staffId, now);
        } catch (DataIntegrityViolationException e) {
            // uq_program_session_program_no 유니크 제약 위반 = 이미 존재하는 회차 번호.
            throw new BusinessException(ErrorCode.DUPLICATE_SESSION_NO);
        }

        return new ProgramSessionResponseDTO(
                sessionId, programId, request.sessionNo(), request.sessionName(),
                request.startsAt(), request.endsAt(), location);
    }

    /**
     * 운영부서가 프로그램의 회차 목록을 조회한다.
     *   currentUserId    : 지금 로그인해서 이 요청을 보낸 사용자의 id (인증 정보에서 옴)
     *   departmentCodeId : 로그인한 사용자가 소속된 부서의 CommonCode PK (인증 정보에서 옴)
     *                    → updateSession()과 동일하게, 비교과운영부서(D200) 소속 + 프로그램 등록자 본인인지 검증한다.
     */
    public List<ProgramSessionResponseDTO> listSessions(Integer programId, Integer currentUserId, Integer departmentCodeId) {
        ExtracurricularProgram program = programRepository.findById(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        if (!isOperatingDepartment(departmentCodeId)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_FORBIDDEN);
        }
        if (!program.getManagerUser().getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return sessionRepository.findByProgram_ProgramIdOrderBySessionNoAsc(programId)
                .stream()
                .map(ProgramSessionResponseDTO::from)
                .toList();
    }

    /**
     * 운영부서가 이미 등록된 회차의 내용(장소 포함)을 수정한다.
     * ProgramSession 엔티티에는 updated_by 컬럼이 없어 등록과 달리 담당자 id는 받지 않는다.
     *
     *   currentUserId    : 지금 로그인해서 이 요청을 보낸 사용자의 id (인증 정보에서 옴, 클라이언트가 위조 불가)
     *   departmentCodeId : 로그인한 사용자가 소속된 부서의 CommonCode PK (인증 정보에서 옴)
     *                    → ProgramService.update()와 동일하게, 비교과운영부서(D200) 소속 + 프로그램 등록자 본인인지 검증한다.
     */
    public ProgramSessionResponseDTO updateSession(Integer programId, Integer sessionId,
                                                    ProgramSessionUpdateRequestDTO request,
                                                    Integer currentUserId, Integer departmentCodeId) {
        ProgramSession session = sessionRepository.findByProgramSessionIdAndProgram_ProgramId(sessionId, programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_SESSION_NOT_FOUND));

        if (!isOperatingDepartment(departmentCodeId)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_FORBIDDEN);
        }

        if (!session.getProgram().getManagerUser().getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (!request.startsAt().isBefore(request.endsAt())) {
            throw new BusinessException(ErrorCode.PROGRAM_INVALID_PERIOD);
        }

        // 회차 번호는 1..N 연속만 허용되는 정책이라, 다른 회차를 함께 재배치하지 않는 단일 수정에서는
        // sessionNo 변경 자체를 금지한다 (변경을 허용하면 반드시 gap 또는 중복이 발생한다).
        if (!request.sessionNo().equals(session.getSessionNo())) {
            throw new BusinessException(ErrorCode.PROGRAM_SESSION_NO_NOT_CONTIGUOUS);
        }

        String location = resolveLocation(programId, request.sessionNo(), request.locationType(), request.location(), sessionId);

        int updatedRows;
        try {
            updatedRows = sessionRepository.updateSession(
                    sessionId, programId, request.sessionNo(), request.sessionName(),
                    request.startsAt(), request.endsAt(), location);
        } catch (DataIntegrityViolationException e) {
            // uq_program_session_program_no 유니크 제약 위반 = 다른 회차가 이미 쓰고 있는 회차 번호.
            throw new BusinessException(ErrorCode.DUPLICATE_SESSION_NO);
        }

        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.PROGRAM_SESSION_NOT_FOUND);
        }

        return new ProgramSessionResponseDTO(
                sessionId, programId, request.sessionNo(), request.sessionName(),
                request.startsAt(), request.endsAt(), location);
    }

    /**
     * 회차 장소 입력 방식(locationType)에 따라 실제로 저장할 location 값을 확정한다.
     *   DIRECT_INPUT      : location이 비어있으면 안 된다.
     *   SAME_AS_PREVIOUS  : 직전 회차(sessionNo - 1)를 DB에서 찾아 그 location을 그대로 복사한다.
     *                       직전 회차가 없거나(예: 1회차) 직전 회차의 location도 비어있으면 복사할 값이 없으므로 거부한다.
     *
     * excludeSessionId : updateSession()에서 회차 번호를 바꾸는 경우, 이 시점엔 아직 UPDATE가 실행되기 전이라
     *                    DB에는 수정 대상 회차 자신이 옛 sessionNo를 가진 채로 남아있다. 그냥 sessionNo로만
     *                    "직전 회차"를 찾으면 자기 자신이 조회될 수 있어, 그 회차의 PK를 넘겨 제외한다.
     *                    registerSession()은 신규 회차라 자기 자신이 존재할 수 없으므로 null을 넘긴다.
     */
    private String resolveLocation(Integer programId, Integer sessionNo, SessionLocationType locationType,
                                    String location, Integer excludeSessionId) {
        if (locationType == SessionLocationType.DIRECT_INPUT) {
            if (!StringUtils.hasText(location)) {
                throw new BusinessException(ErrorCode.SESSION_LOCATION_REQUIRED);
            }
            return location;
        }

        Optional<ProgramSession> previousSession = excludeSessionId == null
                ? sessionRepository.findByProgram_ProgramIdAndSessionNo(programId, sessionNo - 1)
                : sessionRepository.findByProgram_ProgramIdAndSessionNoAndProgramSessionIdNot(
                        programId, sessionNo - 1, excludeSessionId);
        return previousSession
                .map(ProgramSession::getLocation)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new BusinessException(ErrorCode.PREVIOUS_SESSION_LOCATION_NOT_FOUND));
    }

    /**
     * 로그인한 사용자의 부서 codeId가 비교과운영부서(D200)의 codeId와 같은지 검사한다.
     * departmentCodeId가 null이면(부서 미배정) 당연히 비교과운영부서가 아니므로 false.
     */
    private boolean isOperatingDepartment(Integer departmentCodeId) {
        if (departmentCodeId == null) {
            return false;
        }
        return commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc(DEPARTMENT_GROUP)
                .stream()
                .filter(commonCode -> commonCode.getCode().equals(DEFAULT_DEPARTMENT_CODE))
                .anyMatch(commonCode -> commonCode.getCodeId().equals(departmentCodeId));
    }
}
