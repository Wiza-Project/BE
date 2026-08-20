package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.program.dto.response.CompetencyOptionResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramAdminListItemResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramDetailResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramListItemResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.request.ProgramRegisterRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramRegisterResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.request.ProgramUpdateRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramSessionResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramUpdateResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramStatus;
import com.gnagnoohc.scms.domain.program.repository.CompetencyOptionRepository;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramSessionRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProgramService {

    private static final BigDecimal DEFAULT_COMPLETION_RATE = new BigDecimal("80");
    // 등록 직후의 상태. 값 자체는 기존과 동일한 DRAFT이지만, 이제 "모집중"을 의미하는 상수로 취급한다
    // (ProgramStatus 참고 — 모집 마감/운영 종료가 지나면 스케줄러가 OPERATING/CLOSED로 자동 전환한다).
    private static final ProgramStatus INITIAL_STATUS = ProgramStatus.DRAFT;
    // 요청에 부서/프로그램 유형 코드가 없을 때 채워 넣을 기본값(CommonCode의 code_group/code).
    // 코드값은 CommonCodeSeeder 기준(접두어+100단위 형식으로 리네임됨: 학습=PT100, 비교과운영부서=D200).
    private static final String DEPARTMENT_GROUP = "DEPARTMENT";
    private static final String DEFAULT_DEPARTMENT_CODE = "D200"; // 비교과운영부서
    private static final String PROGRAM_TYPE_GROUP = "PROGRAM_TYPE";
    private static final String DEFAULT_PROGRAM_TYPE_CODE = "PT100"; // 학습

    private final ExtracurricularProgramRepository programRepository;
    private final CompetencyOptionRepository competencyOptionRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final ProgramSessionRepository programSessionRepository;
    private final ProgramApplicationRepository applicationRepository;

    // ── "등록(Create)" 기능 ──────────────────────────────────────────────
    //
    // 비교과프로그램을 새로 등록하는 메서드. 매개변수 2개의 의미:
    //   request       : 등록할 내용이 담긴 요청 DTO (요청 바디에서 옴)
    //   managerUserId : 지금 로그인해서 이 요청을 보낸 사용자의 id (인증 정보에서 옴, 클라이언트가 위조 불가)
    //                    → 이 값이 그대로 등록 담당자(managerUser)가 된다.
    //   departmentCodeId : 로그인한 사용자가 소속된 부서의 CommonCode PK (인증 정보에서 옴, 클라이언트가 위조 불가)
    //                    → 비교과운영부서(D200) 소속인지 검증하는 데만 쓰인다.
    public ProgramRegisterResponseDTO register(ProgramRegisterRequestDTO request, Integer managerUserId,
                                                Integer departmentCodeId) {
        // (0) 부서 권한 확인 -----------------------------------------------------------
        // user_type=STAFF 여부는 SecurityConfig의 URL 매칭(hasRole("STAFF"))에서 이미 걸러졌으므로,
        // 여기서는 그중에서도 소속 부서가 비교과운영부서(D200)인지만 추가로 검증한다.
        if (!isOperatingDepartment(departmentCodeId)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_FORBIDDEN);
        }

        // (a) 기간 논리 검증 ----------------------------------------------------------
        // @NotNull 등 형식 검증만으로는 "시작 > 종료"처럼 값들 사이의 논리적 오류를 잡을 수 없어 여기서 별도 검증한다.
        // 검증에 실패하면 validatePeriod 내부에서 예외를 던지므로, 통과했을 때만 아래 코드가 실행된다.
        validatePeriod(request.recruitmentStartsAt(), request.recruitmentEndsAt(),
                request.operationStartsAt(), request.operationEndsAt());

        // (b) 기본값 보정 -------------------------------------------------------------
        // 요청에 completionRate 값이 없으면(null) 엔티티 기본값(80)과 동일하게 채운다.
        BigDecimal completionRate = request.completionRate() != null
                ? request.completionRate() : DEFAULT_COMPLETION_RATE;
        // 요청에 operatingUnitCodeId/programTypeCodeId가 없으면(null) 각각 "비교과운영부서"/"학습" 코드로 채운다.
        Integer operatingUnitCodeId = resolveCodeId(
                request.operatingUnitCodeId(), DEPARTMENT_GROUP, DEFAULT_DEPARTMENT_CODE,
                ErrorCode.OPERATING_UNIT_NOT_FOUND);
        Integer programTypeCodeId = resolveCodeId(
                request.programTypeCodeId(), PROGRAM_TYPE_GROUP, DEFAULT_PROGRAM_TYPE_CODE,
                ErrorCode.PROGRAM_CATEGORY_NOT_FOUND);
        // "지금 이 순간"의 시각을 한 번만 만들어서, 아래 INSERT 쿼리(created_at/updated_at)와
        // 응답 DTO(createdAt)에 똑같은 값으로 사용한다.
        Instant now = Instant.now();

        // (c) 실제 DB 반영 -------------------------------------------------------------
        // programId에는 native INSERT 쿼리가 RETURNING으로 돌려주는, DB가 새로 채번한 PK 값이 담긴다.
        Integer programId;
        try {
            programId = programRepository.insertProgram(
                    request.fileGroupId(),
                    operatingUnitCodeId,
                    programTypeCodeId,
                    request.competencyId(),
                    request.mileagePolicyId(),
                    // managerUserId는 요청 DTO가 아니라 이 메서드의 매개변수(로그인한 사용자)로 채운다.
                    managerUserId,
                    request.programName(),
                    request.description(),
                    request.recruitmentStartsAt(),
                    request.recruitmentEndsAt(),
                    request.operationStartsAt(),
                    request.operationEndsAt(),
                    request.capacity(),
                    completionRate,
                    // 새로 등록되는 프로그램은 항상 "모집중"(DRAFT) 상태로 시작한다. 클라이언트가 정할 수 없다.
                    INITIAL_STATUS.name(),
                    now
            );
        } catch (DataIntegrityViolationException e) {
            // 존재하지 않는 operatingUnitCodeId/programTypeCodeId/competencyId/mileagePolicyId를 넣으면
            // DB가 FK(외래키) 제약 위반으로 이 예외를 던진다. 그대로 흘리면 500(서버 오류)이 나가버리므로,
            // 위반된 컬럼을 구분해 어떤 참조 값이 없는지에 맞는 404 에러로 바꿔서 응답한다.
            throw resolveForeignKeyViolation(e);
        }

        // 등록에 성공했으니, 방금 저장한 값들을 그대로 담아 응답 DTO를 만들어 돌려준다.
        return new ProgramRegisterResponseDTO(
                // DB가 새로 만들어준 PK.
                programId,
                request.programName(),
                // 응답에는 한글 라벨("모집중")을 담아, 클라이언트가 등록 직후 상태를 바로 알 수 있게 한다.
                INITIAL_STATUS.getLabel(),
                request.capacity(),
                completionRate,
                request.recruitmentStartsAt(),
                request.recruitmentEndsAt(),
                request.operationStartsAt(),
                request.operationEndsAt(),
                now
        );
    }

    // ── "수정(Update)" 기능 ──────────────────────────────────────────────
    //
    // 프로그램 하나를 수정하는 메서드. 매개변수 3개의 의미:
    //   programId     : 수정할 프로그램의 PK (URL 경로에서 옴)
    //   request       : 수정할 내용이 담긴 요청 DTO (요청 바디에서 옴)
    //   currentUserId : 지금 로그인해서 이 요청을 보낸 사용자의 id (인증 정보에서 옴, 클라이언트가 위조 불가)
    public ProgramUpdateResponseDTO update(Integer programId, ProgramUpdateRequestDTO request, Integer currentUserId) {

        // (a) 존재 확인 ------------------------------------------------------------
        // findById는 DB에서 값을 "읽기"만 하는 조회이므로, 엔티티에 setter/빌더가 없어도 문제없이 쓸 수 있다.
        // 여기서 읽어온 program 엔티티는 실제 수정에 쓰는 게 아니라, 아래 (b)(c) 검증(소유자 확인, 상태 확인)에만 사용한다.
        // 만약 programId에 해당하는 프로그램이 DB에 없다면 Optional이 비어있으므로, orElseThrow가 예외를 던진다.
        ExtracurricularProgram program = programRepository.findById(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        // (b) 소유자 확인 ------------------------------------------------------------
        // program.getManagerUser()는 이 프로그램을 등록한 담당자(AppUser) 엔티티다.
        // 그 담당자의 userId와, 지금 요청을 보낸 사용자의 id(currentUserId)가 다르면
        // "본인이 등록한 프로그램이 아니므로" 수정 권한이 없다는 뜻이라 403 Forbidden을 던진다.
        // (프론트엔드에서 수정 버튼을 숨기더라도, 이 검증은 백엔드에서 반드시 다시 해야 한다 — 버튼 숨김은 우회 가능하기 때문.)
        if (!program.getManagerUser().getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // (c) 수정 가능한 상태인지 확인 ------------------------------------------------
        // 모집중(현재 시각이 모집 종료 시각 이전)일 때만 수정을 허용한다.
        // 모집이 종료되면(운영중 이후) 이미 신청한 학생들에게 혼란을 주거나 데이터가 꼬일 수 있기 때문에 막는다.
        // "지금 이 순간"의 시각을 한 번만 만들어서, 이 판단과 아래 UPDATE 쿼리·응답 DTO에 똑같이 사용한다.
        //
        // programStatus(ProgramStatus.DRAFT="모집중")를 직접 보지 않고 시각을 비교하는 이유:
        // ProgramStatusScheduler는 최대 1분 주기로만 상태를 갱신하므로, 모집 마감 시각이 지난 직후부터
        // 다음 스케줄 실행 전까지는 DB의 programStatus가 아직 DRAFT로 남아있는 지연 구간이 있다.
        // 그 구간에서 programStatus만 보고 판단하면 이미 모집이 끝났는데도 수정을 허용해버리므로,
        // 스케줄러 지연과 무관하게 항상 정확한 시각 비교를 기준으로 삼는다.
        Instant now = Instant.now();
        if (!now.isBefore(program.getRecruitmentEndsAt())) {
            throw new BusinessException(ErrorCode.PROGRAM_NOT_EDITABLE);
        }

        // (d) 기간 논리 검증 ----------------------------------------------------------
        // register()에서 쓰던 것과 완전히 같은 검증 로직을 재사용한다(아래 validatePeriod 메서드 참고).
        validatePeriod(request.recruitmentStartsAt(), request.recruitmentEndsAt(),
                request.operationStartsAt(), request.operationEndsAt());

        // 요청에 completionRate 값이 없으면(null) 기본값(80)으로 채워준다. register()와 동일한 규칙.
        BigDecimal completionRate = request.completionRate() != null
                ? request.completionRate() : DEFAULT_COMPLETION_RATE;

        // (e) 실제 DB 반영 -------------------------------------------------------------
        // updatedRows는 이번 UPDATE 문으로 실제 몇 개의 row가 바뀌었는지를 담는다(보통 0 또는 1).
        int updatedRows;
        try {
            updatedRows = programRepository.updateProgram(
                    programId,
                    request.fileGroupId(),
                    request.operatingUnitCodeId(),
                    request.programTypeCodeId(),
                    request.competencyId(),
                    request.mileagePolicyId(),
                    request.programName(),
                    request.description(),
                    request.recruitmentStartsAt(),
                    request.recruitmentEndsAt(),
                    request.operationStartsAt(),
                    request.operationEndsAt(),
                    request.capacity(),
                    completionRate,
                    // updatedBy는 요청 바디가 아니라 currentUserId(로그인한 사용자)로 서버가 직접 채운다.
                    currentUserId,
                    now
            );
        } catch (DataIntegrityViolationException e) {
            // register()와 마찬가지로, 존재하지 않는 참조값(FK)을 넣었을 때 DB가 던지는 예외를
            // 어떤 참조값이 문제였는지에 맞는 404 에러로 바꿔준다. 등록 때 만든 메서드를 그대로 재사용한다.
            throw resolveForeignKeyViolation(e);
        }

        // updatedRows가 0이라는 것은, (c)에서 모집중임을 확인한 "직후"부터 실제 UPDATE 문이
        // 실행되기 "직전" 사이의 아주 짧은 순간에 모집 종료 시각이 지나버렸다는 뜻이다
        // (native UPDATE 쿼리의 WHERE 절에도 recruitment_ends_at > :now 조건이 걸려있기 때문).
        // 이런 경우도 결국 "지금은 수정할 수 없는 상태"이므로 (c)와 같은 에러를 던진다.
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.PROGRAM_NOT_EDITABLE);
        }

        // 수정에 성공했으니, 방금 반영한 값들을 그대로 담아 응답 DTO를 만들어 돌려준다.
        return new ProgramUpdateResponseDTO(
                programId,
                request.programName(),
                // 이 API는 모집중(DRAFT)일 때만 성공하므로 항상 "모집중"이지만, 하드코딩 대신
                // 방금 읽어온 엔티티의 실제 상태를 라벨로 변환해 내려준다.
                program.getProgramStatus().getLabel(),
                request.capacity(),
                completionRate,
                request.recruitmentStartsAt(),
                request.recruitmentEndsAt(),
                request.operationStartsAt(),
                request.operationEndsAt(),
                now
        );
    }

    // ── "목록 조회(List)" 기능 ──────────────────────────────────────────────
    //
    // 학생이 프로그램 목록 페이지에서 탐색할 때 쓰는 조회. 상태/이름 키워드로 걸러 페이지 단위로 내려준다.
    // status/keyword가 둘 다 없으면 전체 프로그램을 페이지네이션해서 반환한다.
    @Transactional(readOnly = true)
    public PageResponse<ProgramListItemResponseDTO> list(ProgramStatus status, String keyword, Pageable pageable) {
        Page<ExtracurricularProgram> page = programRepository.search(status, keyword, pageable);
        Map<Integer, Long> applicantCounts = countApplicantsByProgram(page.getContent());
        return PageResponse.from(page.map(program ->
                ProgramListItemResponseDTO.from(program, applicantCounts.getOrDefault(program.getProgramId(), 0L))));
    }

    // ── "staff용 목록 조회(List)" 기능 ──────────────────────────────────────
    //
    // list()와 거의 같지만, 로그인한 staff 본인이 담당(managerUser)한 프로그램으로만 범위를 좁힌다.
    @Transactional(readOnly = true)
    public PageResponse<ProgramAdminListItemResponseDTO> listMine(Integer managerUserId, ProgramStatus status,
                                                                    String keyword, Pageable pageable) {
        Page<ExtracurricularProgram> page = programRepository.searchByManager(managerUserId, status, keyword, pageable);
        Map<Integer, Long> applicantCounts = countApplicantsByProgram(page.getContent());
        return PageResponse.from(page.map(program -> ProgramAdminListItemResponseDTO.from(
                program, applicantCounts.getOrDefault(program.getProgramId(), 0L))));
    }

    // ── "학생용 상세 조회(Detail)" 기능 ──────────────────────────────────────
    //
    // 학생이 프로그램 상세 화면에서 볼 기본정보 전체 + 회차 목록 + 신청자 수를 한 번에 조립한다.
    @Transactional(readOnly = true)
    public ProgramDetailResponseDTO getDetail(Integer programId) {
        ExtracurricularProgram program = programRepository.findDetailById(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        List<ProgramSessionResponseDTO> sessions = programSessionRepository
                .findByProgram_ProgramIdOrderBySessionNoAsc(programId)
                .stream()
                .map(ProgramSessionResponseDTO::from)
                .toList();

        long applicantCount = applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(
                programId, List.of(ApplicationStatus.APPLIED.name(), ApplicationStatus.APPROVED.name()));

        return ProgramDetailResponseDTO.from(program, applicantCount, sessions);
    }

    // 목록 페이지 한 번에 해당하는 프로그램들의 신청자 수를 한 번의 쿼리로 배치 조회한다(N+1 방지).
    private Map<Integer, Long> countApplicantsByProgram(List<ExtracurricularProgram> programs) {
        if (programs.isEmpty()) {
            return Map.of();
        }
        List<Integer> programIds = programs.stream().map(ExtracurricularProgram::getProgramId).toList();
        return applicationRepository.countActiveApplicantsByProgramIds(programIds).stream()
                .collect(Collectors.toMap(
                        ProgramApplicationRepository.ProgramApplicantCount::getProgramId,
                        ProgramApplicationRepository.ProgramApplicantCount::getCount));
    }

    // 프로그램 등록 폼의 핵심역량 드롭다운용 옵션 목록. 최상위(하위 역량 없음) + 활성 상태만 노출한다.
    public List<CompetencyOptionResponseDTO> getCompetencyOptions() {
        return competencyOptionRepository.findByParentCompetencyIsNullAndActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(CompetencyOptionResponseDTO::from)
                .toList();
    }

    // ── "삭제(Delete)" 기능 ──────────────────────────────────────────────
    //
    // 프로그램 하나를 삭제하는 메서드. 매개변수 2개의 의미:
    //   programId     : 삭제할 프로그램의 PK (URL 경로에서 옴)
    //   currentUserId : 지금 로그인해서 이 요청을 보낸 사용자의 id (인증 정보에서 옴, 클라이언트가 위조 불가)
    public void delete(Integer programId, Integer currentUserId) {

        // (a) 존재 확인 ------------------------------------------------------------
        ExtracurricularProgram program = programRepository.findById(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        // (b) 소유자 확인 ------------------------------------------------------------
        // update()와 동일한 이유로, 등록자 본인이 아니면 403 Forbidden.
        if (!program.getManagerUser().getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // (c) 삭제 가능한 상태인지 확인 ------------------------------------------------
        // update()의 (c)단계와 완전히 같은 조건: 모집중(현재 시각이 모집 종료 시각 이전)일 때만 허용한다.
        Instant now = Instant.now();
        if (!now.isBefore(program.getRecruitmentEndsAt())) {
            throw new BusinessException(ErrorCode.PROGRAM_NOT_DELETABLE);
        }

        // (d) 실제 DB 반영 -------------------------------------------------------------
        // deletedRows가 0이라는 것은, (c)에서 모집중임을 확인한 "직후"부터 실제 DELETE 문이 실행되기
        // "직전" 사이의 아주 짧은 순간에 모집 종료 시각이 지나버렸다는 뜻이다(native DELETE 쿼리의
        // WHERE 절에도 recruitment_ends_at > :now 조건이 걸려있기 때문). 이런 경우도 결국
        // "지금은 삭제할 수 없는 상태"이므로 (c)와 같은 에러를 던진다.
        int deletedRows = programRepository.deleteProgram(programId, now);
        if (deletedRows == 0) {
            throw new BusinessException(ErrorCode.PROGRAM_NOT_DELETABLE);
        }
    }

    // 로그인한 사용자의 부서 codeId가 비교과운영부서(D200)의 codeId와 같은지 검사한다.
    // departmentCodeId가 null이면(부서 미배정) 당연히 비교과운영부서가 아니므로 false.
    private boolean isOperatingDepartment(Integer departmentCodeId) {
        if (departmentCodeId == null) {
            return false;
        }
        return commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc(DEPARTMENT_GROUP)
                .stream()
                .filter(commonCode -> commonCode.getCode().equals(DEFAULT_DEPARTMENT_CODE))
                .anyMatch(commonCode -> commonCode.getCodeId().equals(departmentCodeId));
    }

    // 요청값이 있으면 그대로 쓰고, 없으면(null) 주어진 그룹에서 defaultCode와 일치하는 CommonCode를 찾아 그 codeId를 대신 쓴다.
    // (CommonCodeRepository에는 group+code 단건 조회 메서드가 없어, 기존에 있는 그룹 전체 조회 메서드로 가져온 뒤 code로 걸러낸다.)
    private Integer resolveCodeId(Integer requestedCodeId, String codeGroup, String defaultCode,
                                   ErrorCode notFoundError) {
        if (requestedCodeId != null) {
            return requestedCodeId;
        }
        return commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc(codeGroup)
                .stream()
                .filter(commonCode -> commonCode.getCode().equals(defaultCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(notFoundError))
                .getCodeId();
    }

    // FK 제약 위반 메시지에는 PostgreSQL이 항상 위반된 컬럼명을 "Key (컬럼명)=(값) is not present..." 형식으로 담아준다.
    // 제약 조건 이름(마이그레이션에서 어떻게 명명했는지)에 기대지 않고 컬럼명 문자열만으로 어떤 참조 값이 없는지 구분한다.
    private BusinessException resolveForeignKeyViolation(DataIntegrityViolationException e) {
        // e.getMostSpecificCause()는 스프링이 감싸놓은 예외 껍데기를 벗기고, 실제로 DB 드라이버가 던진
        // 가장 안쪽의 원인 예외를 꺼내온다. 그 예외의 메시지 안에 "어떤 컬럼이 문제였는지"가 문자열로 들어있다.
        // (참고: 이 메시지는 로그로만 쓰이고, 서버가 실제로 클라이언트에게 응답하는 건 아래 고정된 ErrorCode 메시지뿐이다.)
        String detail = e.getMostSpecificCause().getMessage();

        // 메시지 안에 "operating_unit_code_id"라는 컬럼명이 들어있다면, 존재하지 않는 운영 단위 코드를 참조한 것.
        if (detail.contains("operating_unit_code_id")) {
            return new BusinessException(ErrorCode.OPERATING_UNIT_NOT_FOUND);
        }
        // "program_type_code_id"가 들어있다면, 존재하지 않는 프로그램 유형(분류) 코드를 참조한 것.
        if (detail.contains("program_type_code_id")) {
            return new BusinessException(ErrorCode.PROGRAM_CATEGORY_NOT_FOUND);
        }
        // "competency_id"가 들어있다면, 존재하지 않는 핵심역량을 참조한 것.
        if (detail.contains("competency_id")) {
            return new BusinessException(ErrorCode.COMPETENCY_NOT_FOUND);
        }
        // "mileage_policy_id"가 들어있다면, 존재하지 않는 마일리지 정책을 참조한 것.
        if (detail.contains("mileage_policy_id")) {
            return new BusinessException(ErrorCode.MILEAGE_POLICY_NOT_FOUND);
        }
        // 위 4개 컬럼 중 어디에도 해당하지 않는 예상치 못한 FK 위반이라면, 일반적인 "입력값 오류"로 처리한다.
        return new BusinessException(ErrorCode.INVALID_INPUT, "요청 값이 올바르지 않습니다.");
    }

    // 세 가지 논리적 제약을 검사한다: 모집 시작<종료, 운영 시작<종료, 모집 종료<=운영 시작(모집이 끝나야 운영이 시작됨).
    // register()와 update() 양쪽에서 공통으로 쓸 수 있도록, DTO 타입 전체가 아니라 Instant 값 4개만 파라미터로 받는다.
    // (두 DTO는 필드 구성은 같지만 서로 다른 record 타입이라 공통 인터페이스가 없기 때문에, 원시 값을 뽑아서 넘기는 방식을 택했다.)
    private void validatePeriod(Instant recruitmentStartsAt, Instant recruitmentEndsAt,
                                 Instant operationStartsAt, Instant operationEndsAt) {
        // 모집 시작 시각이 모집 종료 시각보다 앞서야 한다(true여야 정상).
        boolean recruitmentValid = recruitmentStartsAt.isBefore(recruitmentEndsAt);
        // 운영 시작 시각이 운영 종료 시각보다 앞서야 한다(true여야 정상).
        boolean operationValid = operationStartsAt.isBefore(operationEndsAt);
        // 모집이 끝난 뒤에(또는 같은 시각에) 운영이 시작되어야 한다 — 모집 종료가 운영 시작보다 늦으면 안 됨.
        boolean recruitmentBeforeOperation = !recruitmentEndsAt.isAfter(operationStartsAt);

        // 셋 중 하나라도 어긋나면 400 에러(PROGRAM_INVALID_PERIOD)를 던져서 요청을 거절한다.
        if (!recruitmentValid || !operationValid || !recruitmentBeforeOperation) {
            throw new BusinessException(ErrorCode.PROGRAM_INVALID_PERIOD);
        }
    }
}
