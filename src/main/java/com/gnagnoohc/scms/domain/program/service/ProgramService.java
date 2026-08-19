package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.program.dto.ProgramRegisterRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.ProgramRegisterResponseDTO;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class ProgramService {

    private static final BigDecimal DEFAULT_COMPLETION_RATE = new BigDecimal("80");
    private static final String INITIAL_STATUS = "DRAFT";

    private final ExtracurricularProgramRepository programRepository;

    // 비교과프로그램 등록 흐름: 기간 검증 → 기본값 보정 → native INSERT 저장 → 응답 DTO 조립.
    // managerUserId는 요청 바디가 아니라 컨트롤러가 인증된 사용자(JWT)에서 뽑아 넘겨준 값이다.
    public ProgramRegisterResponseDTO register(ProgramRegisterRequestDTO request, Integer managerUserId) {
        // @NotNull 등 형식 검증만으로는 "시작 > 종료"처럼 값들 사이의 논리적 오류를 잡을 수 없어 여기서 별도 검증한다.
        validatePeriod(request);

        // 요청에 completionRate가 없으면 엔티티 기본값(80)과 동일하게 채운다.
        BigDecimal completionRate = request.completionRate() != null
                ? request.completionRate() : DEFAULT_COMPLETION_RATE;
        Instant now = Instant.now();

        Integer programId;
        try {
            programId = programRepository.insertProgram(
                    request.fileGroupId(),
                    request.operatingUnitCodeId(),
                    request.programTypeCodeId(),
                    request.competencyId(),
                    request.mileagePolicyId(),
                    managerUserId,
                    request.programName(),
                    request.description(),
                    request.recruitmentStartsAt(),
                    request.recruitmentEndsAt(),
                    request.operationStartsAt(),
                    request.operationEndsAt(),
                    request.capacity(),
                    completionRate,
                    INITIAL_STATUS,
                    now
            );
        } catch (DataIntegrityViolationException e) {
            // 존재하지 않는 operatingUnitCodeId/programTypeCodeId/competencyId/mileagePolicyId를 넣으면
            // DB가 FK 제약 위반으로 이 예외를 던진다. 그대로 흘리면 500이 나가므로 400으로 바꿔서 응답한다.
            throw new BusinessException(ErrorCode.INVALID_INPUT, "존재하지 않는 분류/역량/마일리지 정책입니다.");
        }

        return new ProgramRegisterResponseDTO(
                programId,
                request.programName(),
                INITIAL_STATUS,
                request.capacity(),
                completionRate,
                request.recruitmentStartsAt(),
                request.recruitmentEndsAt(),
                request.operationStartsAt(),
                request.operationEndsAt(),
                now
        );
    }

    // 세 가지 논리적 제약을 검사한다: 모집 시작<종료, 운영 시작<종료, 모집 종료<=운영 시작(모집이 끝나야 운영이 시작됨).
    private void validatePeriod(ProgramRegisterRequestDTO request) {
        boolean recruitmentValid = request.recruitmentStartsAt().isBefore(request.recruitmentEndsAt());
        boolean operationValid = request.operationStartsAt().isBefore(request.operationEndsAt());
        boolean recruitmentBeforeOperation = !request.recruitmentEndsAt().isAfter(request.operationStartsAt());

        if (!recruitmentValid || !operationValid || !recruitmentBeforeOperation) {
            throw new BusinessException(ErrorCode.PROGRAM_INVALID_PERIOD);
        }
    }
}
