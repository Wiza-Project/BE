package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.program.dto.response.ProgramMileagePolicyPreviewResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramStaffDetailResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramStaffListItemResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramDetailResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramListItemResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.request.ProgramRegisterRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.request.ProgramSessionRegisterRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramRegisterResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.request.ProgramUpdateRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramSessionResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramUpdateResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.domain.program.entity.ProgramStatus;
import com.gnagnoohc.scms.domain.program.entity.SessionLocationType;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramFileUploadResponseDTO;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramSessionRepository;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.repository.MileagePolicyRepository;
import com.gnagnoohc.scms.domain.mileage.service.ExtracurricularMileagePolicyDefinition;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.common.entity.FileGroup;
import com.gnagnoohc.scms.global.common.entity.StoredFile;
import com.gnagnoohc.scms.global.common.helper.FileUploadValidator;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.common.repository.FileGroupRepository;
import com.gnagnoohc.scms.global.common.service.FileGroupService;
import com.gnagnoohc.scms.global.common.service.FileStorageService;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.DbConstraintViolationMatcher;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProgramService {

    private static final BigDecimal DEFAULT_COMPLETION_RATE = new BigDecimal("80");
    /**
     * 등록 직후의 상태. 값 자체는 기존과 동일한 DRAFT이지만, 이제 "모집중"을 의미하는 상수로 취급한다
     * (ProgramStatus 참고 — 모집 마감/운영 종료가 지나면 스케줄러가 OPERATING/CLOSED로 자동 전환한다).
     */
    private static final ProgramStatus INITIAL_STATUS = ProgramStatus.DRAFT;
    /**
     * 로그인한 사용자가 비교과운영부서 소속인지 검증(isOperatingDepartment)할 때 기준이 되는 CommonCode 값.
     * 코드값은 CommonCodeSeeder 기준(비교과운영부서=D200).
     */
    private static final String DEPARTMENT_GROUP = "DEPARTMENT";
    private static final String DEFAULT_DEPARTMENT_CODE = "D200"; // 비교과운영부서
    /**
     * findActiveExtracurricularPolicy()에서 programTypeCodeId로 조회한 CommonCode가 실제로
     * 프로그램 유형 그룹인지 검증할 때 쓰는 값. MileagePolicyRepository.findActiveExtracurricularPoliciesByProgramTypeOn()의
     * JPQL이 검사하는 codeGroup 리터럴과 동일해야 한다.
     */
    private static final String PROGRAM_TYPE_GROUP = "PROGRAM_TYPE";
    // 운영계획서는 문서 1개(PDF)만 받는다 — FileUploadValidator의 기본 허용 확장자(이미지+PDF)보다 좁게 검증한다.
    private static final Set<String> OPERATION_PLAN_EXTENSIONS = Set.of("pdf");

    private final ExtracurricularProgramRepository programRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final MileagePolicyRepository mileagePolicyRepository;
    private final ProgramSessionRepository programSessionRepository;
    private final ProgramApplicationRepository applicationRepository;
    private final FileGroupService fileGroupService;
    private final FileGroupRepository fileGroupRepository;
    private final FileStorageService fileStorageService;
    private final FileUploadValidator fileUploadValidator;

    /**
     * ── "등록(Create)" 기능 ──────────────────────────────────────────────
     *
     * 비교과프로그램을 새로 등록하는 메서드. 매개변수 2개의 의미:
     *   request       : 등록할 내용이 담긴 요청 DTO (요청 바디에서 옴)
     *   managerUserId : 지금 로그인해서 이 요청을 보낸 사용자의 id (인증 정보에서 옴, 클라이언트가 위조 불가)
     *                    → 이 값이 그대로 등록 담당자(managerUser)가 된다.
     *   departmentCodeId : 로그인한 사용자가 소속된 부서의 CommonCode PK (인증 정보에서 옴, 클라이언트가 위조 불가)
     *                    → 비교과운영부서(D200) 소속인지 검증하는 데만 쓰인다.
     */
    public ProgramRegisterResponseDTO register(ProgramRegisterRequestDTO request, Integer managerUserId,
                                                Integer departmentCodeId) {
        /**
         * (0) 부서 권한 확인 -----------------------------------------------------------
         * user_type=STAFF 여부는 SecurityConfig의 URL 매칭(hasRole("STAFF"))에서 이미 걸러졌으므로,
         * 여기서는 그중에서도 소속 부서가 비교과운영부서(D200)인지만 추가로 검증한다.
         */
        if (!isOperatingDepartment(departmentCodeId)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_FORBIDDEN);
        }

        /**
         * (a) 기간 논리 검증 ----------------------------------------------------------
         * @NotNull 등 형식 검증만으로는 "시작 > 종료"처럼 값들 사이의 논리적 오류를 잡을 수 없어 여기서 별도 검증한다.
         * 검증에 실패하면 validatePeriod 내부에서 예외를 던지므로, 통과했을 때만 아래 코드가 실행된다.
         */
        validatePeriod(request.recruitmentStartsAt(), request.recruitmentEndsAt(),
                request.operationStartsAt(), request.operationEndsAt());

        /**
         * (a-1-1) 회차 최소 1개 검증 ----------------------------------------------------
         * 프론트의 "회차관리" 탭에서 최소 1회차를 입력하지 않으면 등록 자체를 막는다. 프론트에서
         * 버튼/화면으로 막더라도 우회 가능하므로 백엔드가 반드시 재검증한다(다른 검증들과 동일한 이유).
         * DB에 아무것도 쓰기 전(첨부파일 검증/INSERT 이전)에 가장 먼저 확인해, 회차 없이 등록을 시도했을 때
         * 불필요한 부수효과 없이 곧바로 막는다.
         */
        if (request.sessions() == null || request.sessions().isEmpty()) {
            throw new BusinessException(ErrorCode.PROGRAM_SESSION_REQUIRED);
        }

        /**
         * (a-2) 첨부파일 검증 ----------------------------------------------------------
         * 클라이언트가 보낸 fileGroupId를 검증 없이 그대로 저장하면, 업로더 본인이 아닌 그룹이나
         * 이미 다른 프로그램에 연결된 그룹을 임의로 지정해 연결할 수 있다. 아래에서 소유권/단일PDF/재사용을 검증한다.
         */
        validateFileGroupForLinking(request.fileGroupId(), managerUserId, null);

        /**
         * (b) 기본값 보정 -------------------------------------------------------------
         * 요청에 completionRate 값이 없으면(null) 엔티티 기본값(80)과 동일하게 채운다.
         */
        BigDecimal completionRate = request.completionRate() != null
                ? request.completionRate() : DEFAULT_COMPLETION_RATE;
        // operatingUnitCodeId/programTypeCodeId는 이제 프론트가 드롭다운으로 선택해서 보내는 필수값이라 그대로 사용한다.
        Integer operatingUnitCodeId = request.operatingUnitCodeId();
        Integer programTypeCodeId = request.programTypeCodeId();
        /**
         * "지금 이 순간"의 시각을 한 번만 만들어서, 아래 INSERT 쿼리(created_at/updated_at)와
         * 응답 DTO(createdAt)에 똑같은 값으로 사용한다.
         */
        Instant now = Instant.now();

        /**
         * (c) 실제 DB 반영 -------------------------------------------------------------
         * programId에는 native INSERT 쿼리가 RETURNING으로 돌려주는, DB가 새로 채번한 PK 값이 담긴다.
         */
        Integer programId;
        try {
            programId = programRepository.insertProgram(
                    request.fileGroupId(),
                    operatingUnitCodeId,
                    programTypeCodeId,
                    request.competencyId(),
                    resolveMileagePolicyId(programTypeCodeId),
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
            /**
             * 존재하지 않는 operatingUnitCodeId/programTypeCodeId/competencyId/mileagePolicyId를 넣으면
             * DB가 FK(외래키) 제약 위반으로 이 예외를 던진다. 그대로 흘리면 500(서버 오류)이 나가버리므로,
             * 위반된 컬럼을 구분해 어떤 참조 값이 없는지에 맞는 404 에러로 바꿔서 응답한다.
             */
            throw resolveForeignKeyViolation(e);
        }

        /**
         * (d) 회차 일괄 생성 -----------------------------------------------------------
         * 위 (a-1-1)에서 이미 1개 이상임을 확인했으므로, 요청에 담긴 회차를 그대로 순회하며 생성한다.
         * ProgramSessionService.registerSession()과 동일한 리포지토리 메서드를 재사용한다.
         * createdBy는 등록 담당자(managerUserId), 생성 시각은 프로그램 생성에 쓴 now를 그대로 재사용한다.
         *
         * locationType=SAME_AS_PREVIOUS인 회차는 아직 DB에 없는(같은 요청 안의) 직전 회차 장소를
         * 참조해야 하므로, sessionNo 오름차순으로 정렬한 뒤 지금까지 확정된 location을
         * resolvedLocations에 누적해가며 순서대로 처리한다.
         */
        List<ProgramSessionRegisterRequestDTO> sortedSessions = request.sessions().stream()
                .sorted(Comparator.comparing(ProgramSessionRegisterRequestDTO::sessionNo))
                .toList();
        /**
         * 회차 번호는 DB 유니크 제약(중복 방지)만 있을 뿐 연속성을 강제하는 제약이 없어서, 이 검증이
         * 없으면 예를 들어 1회차만 두고 3회차를 SAME_AS_PREVIOUS로 보내는 요청에서 sessionNo=2가
         * 없다는 이유로 위 SAME_AS_PREVIOUS 처리가 (실제로는 정책 위반인데) 엉뚱한 에러코드
         * (PREVIOUS_SESSION_LOCATION_NOT_FOUND)로 거절해버린다. 회차 번호는 1부터 빈 번호 없이
         * 연속되어야 한다는 정책을 여기서 명시적으로 검증해, 이후 SAME_AS_PREVIOUS 처리가 항상
         * sessionNo - 1을 안전하게 참조할 수 있도록 보장한다(요청 내 중복 번호도 함께 걸러진다).
         */
        for (int i = 0; i < sortedSessions.size(); i++) {
            if (!sortedSessions.get(i).sessionNo().equals(i + 1)) {
                throw new BusinessException(ErrorCode.PROGRAM_SESSION_NO_NOT_CONTIGUOUS);
            }
        }
        Map<Integer, String> resolvedLocations = new HashMap<>();
        for (ProgramSessionRegisterRequestDTO session : sortedSessions) {
            String location;
            if (session.locationType() == SessionLocationType.DIRECT_INPUT) {
                if (!StringUtils.hasText(session.location())) {
                    throw new BusinessException(ErrorCode.SESSION_LOCATION_REQUIRED);
                }
                location = session.location();
            } else {
                location = resolvedLocations.get(session.sessionNo() - 1);
                if (!StringUtils.hasText(location)) {
                    throw new BusinessException(ErrorCode.PREVIOUS_SESSION_LOCATION_NOT_FOUND);
                }
            }

            try {
                programSessionRepository.insertSession(
                        programId, session.sessionNo(), session.sessionName(),
                        session.startsAt(), session.endsAt(), location,
                        managerUserId, now);
            } catch (DataIntegrityViolationException e) {
                // uq_program_session_program_no 위반 = 요청 안에서 같은 회차번호가 중복된 경우.
                throw new BusinessException(ErrorCode.DUPLICATE_SESSION_NO);
            }
            resolvedLocations.put(session.sessionNo(), location);
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

    /**
     * ── "운영계획서 업로드" 기능 ──────────────────────────────────────────────
     *
     * 등록/수정 폼에서 첨부할 운영계획서(PDF)를 미리 업로드해 fileGroupId를 발급받는 메서드.
     * 어떤 프로그램에 귀속될지 아직 정해지지 않은 시점(등록 전에도 호출 가능)의 업로드이므로
     * programId를 받지 않는다 — 여기서 반환한 fileGroupId를 register()/update() 요청의
     * fileGroupId 필드에 그대로 실어 보내면 된다.
     * register()/update()와 마찬가지로 비교과운영부서(D200) 소속만 업로드할 수 있다.
     */
    public ProgramFileUploadResponseDTO uploadOperationPlan(MultipartFile file, Integer uploaderId,
                                                              Integer departmentCodeId) {
        if (!isOperatingDepartment(departmentCodeId)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_FORBIDDEN);
        }
        fileUploadValidator.validate(file, OPERATION_PLAN_EXTENSIONS);
        FileGroup fileGroup = fileGroupService.createGroup();
        fileStorageService.store(file, fileGroup, uploaderId);
        return new ProgramFileUploadResponseDTO(fileGroup.getFileGroupId(), file.getOriginalFilename());
    }

    /**
     * register()/update()가 fileGroupId를 실제로 프로그램에 연결하기 전에 검증하는 메서드.
     * fileGroupId를 검증 없이 그대로 저장하면, 요청자가 (1) 본인이 업로드하지 않은 그룹이나
     * (2) 이미 다른 프로그램에 연결된 그룹을 임의로 지정해 연결할 수 있다. 아래 세 가지를 확인한다:
     *   - 그룹이 실제로 존재하는지
     *   - 그룹에 속한 파일이 PDF 1개뿐인지(우회 경로로 임의의 fileGroupId를 넣는 경우까지 대비한 방어)
     *   - 그 파일을 올린 사람(StoredFile.createdBy)이 지금 요청자 본인인지
     *   - 같은 부서 소속의 다른 담당자가 등록한 프로그램이라도, 이 그룹이 program 도메인 내 다른 프로그램에
     *     이미 연결돼 있지 않은지 (excludeProgramId는 update()에서 "자기 자신과의 기존 연결"을 재사용으로
     *     치지 않기 위한 예외)
     * FileGroup은 program 외에 다른 도메인(게시글 첨부 등)도 함께 쓰는 공용 테이블이라, 그 도메인들에서의
     * 재사용까지는 이 검증으로 막지 못한다.
     */
    private void validateFileGroupForLinking(Integer fileGroupId, Integer uploaderId, Integer excludeProgramId) {
        if (fileGroupId == null) {
            return;
        }

        FileGroup fileGroup = fileGroupRepository.findById(fileGroupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_FILE_GROUP_NOT_FOUND));

        List<StoredFile> files = fileGroupService.getFiles(fileGroup);
        String originalFileName = files.size() == 1 ? files.get(0).getOriginalFileName() : null;
        if (files.size() != 1 || originalFileName == null
                || !originalFileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "운영계획서는 PDF 파일 1개만 첨부할 수 있습니다.");
        }
        if (!files.get(0).getCreatedBy().equals(uploaderId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        boolean alreadyLinked = excludeProgramId == null
                ? programRepository.existsByFileGroup_FileGroupId(fileGroupId)
                : programRepository.existsByFileGroup_FileGroupIdAndProgramIdNot(fileGroupId, excludeProgramId);
        if (alreadyLinked) {
            throw new BusinessException(ErrorCode.PROGRAM_FILE_GROUP_ALREADY_LINKED);
        }
    }

    /**
     * ── "수정(Update)" 기능 ──────────────────────────────────────────────
     *
     * 프로그램 하나를 수정하는 메서드. 매개변수 3개의 의미:
     *   programId        : 수정할 프로그램의 PK (URL 경로에서 옴)
     *   request          : 수정할 내용이 담긴 요청 DTO (요청 바디에서 옴)
     *   currentUserId    : 지금 로그인해서 이 요청을 보낸 사용자의 id (인증 정보에서 옴, 클라이언트가 위조 불가)
     *   departmentCodeId : 로그인한 사용자가 소속된 부서의 CommonCode PK (인증 정보에서 옴, 클라이언트가 위조 불가)
     *                    → 비교과운영부서(D200) 소속인지 검증하는 데만 쓰인다. 등록 이후 다른 부서로 옮겼다면
     *                      본인이 등록한 프로그램이라도 더 이상 수정할 수 없어야 하므로, 소유자 확인과 별개로 매번 다시 검사한다.
     */
    public ProgramUpdateResponseDTO update(Integer programId, ProgramUpdateRequestDTO request, Integer currentUserId,
                                            Integer departmentCodeId) {

        /**
         * (a) 존재 확인 ------------------------------------------------------------
         * findById는 DB에서 값을 "읽기"만 하는 조회이므로, 엔티티에 setter/빌더가 없어도 문제없이 쓸 수 있다.
         * 여기서 읽어온 program 엔티티는 실제 수정에 쓰는 게 아니라, 아래 (b)(c) 검증(소유자 확인, 상태 확인)에만 사용한다.
         * 만약 programId에 해당하는 프로그램이 DB에 없다면 Optional이 비어있으므로, orElseThrow가 예외를 던진다.
         */
        ExtracurricularProgram program = programRepository.findById(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        /**
         * (a-1) 부서 권한 확인 -----------------------------------------------------------
         * register()의 (0)단계와 동일한 이유로, 지금도 비교과운영부서(D200) 소속인지 검증한다.
         */
        if (!isOperatingDepartment(departmentCodeId)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_FORBIDDEN);
        }

        /**
         * (b) 소유자 확인 ------------------------------------------------------------
         * program.getManagerUser()는 이 프로그램을 등록한 담당자(AppUser) 엔티티다.
         * 그 담당자의 userId와, 지금 요청을 보낸 사용자의 id(currentUserId)가 다르면
         * "본인이 등록한 프로그램이 아니므로" 수정 권한이 없다는 뜻이라 403 Forbidden을 던진다.
         * (프론트엔드에서 수정 버튼을 숨기더라도, 이 검증은 백엔드에서 반드시 다시 해야 한다 — 버튼 숨김은 우회 가능하기 때문.)
         */
        if (!program.getManagerUser().getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        /**
         * (c) 수정 가능한 상태인지 확인 ------------------------------------------------
         * 모집중(현재 시각이 모집 종료 시각 이전)일 때만 수정을 허용한다.
         * 모집이 종료되면(운영중 이후) 이미 신청한 학생들에게 혼란을 주거나 데이터가 꼬일 수 있기 때문에 막는다.
         * "지금 이 순간"의 시각을 한 번만 만들어서, 이 판단과 아래 UPDATE 쿼리·응답 DTO에 똑같이 사용한다.
         *
         * programStatus(ProgramStatus.DRAFT="모집중")를 직접 보지 않고 시각을 비교하는 이유:
         * ProgramStatusScheduler는 최대 1분 주기로만 상태를 갱신하므로, 모집 마감 시각이 지난 직후부터
         * 다음 스케줄 실행 전까지는 DB의 programStatus가 아직 DRAFT로 남아있는 지연 구간이 있다.
         * 그 구간에서 programStatus만 보고 판단하면 이미 모집이 끝났는데도 수정을 허용해버리므로,
         * 스케줄러 지연과 무관하게 항상 정확한 시각 비교를 기준으로 삼는다.
         */
        Instant now = Instant.now();
        if (!now.isBefore(program.getRecruitmentEndsAt())) {
            throw new BusinessException(ErrorCode.PROGRAM_NOT_EDITABLE);
        }

        /**
         * (d) 기간 논리 검증 ----------------------------------------------------------
         * register()에서 쓰던 것과 완전히 같은 검증 로직을 재사용한다(아래 validatePeriod 메서드 참고).
         */
        validatePeriod(request.recruitmentStartsAt(), request.recruitmentEndsAt(),
                request.operationStartsAt(), request.operationEndsAt());

        // 요청에 completionRate 값이 없으면(null) 기본값(80)으로 채워준다. register()와 동일한 규칙.
        BigDecimal completionRate = request.completionRate() != null
                ? request.completionRate() : DEFAULT_COMPLETION_RATE;

        // 운영단위도 이제 프론트가 드롭다운으로 선택해서 보내는 필수값이라 그대로 사용한다.
        Integer operatingUnitCodeId = request.operatingUnitCodeId();

        /**
         * fileGroupId와 clearFileGroup=true를 동시에 보내는 것은 "새 파일을 연결하라"와
         * "첨부파일을 지워라"가 충돌하는 모순된 요청이므로 막는다(MileagePolicyService의
         * clearValidTo 충돌 검사와 동일한 패턴).
         */
        if (request.clearFileGroup() && request.fileGroupId() != null) {
            throw new BusinessException(ErrorCode.PROGRAM_FILE_GROUP_CONFLICT);
        }

        // 첨부파일은 별도 업로드 화면에서만 바뀌므로, 수정 폼이 파일을 다시 첨부하지 않아
        // 요청에 안 담겨오면(null) 기존에 첨부돼 있던 파일을 그대로 유지한다(지우지 않는다).
        // 첨부파일을 삭제하려면 clearFileGroup=true를 명시적으로 보내야 하며, 이때는 null로 갱신해
        // 연결을 해제한다(FileGroup은 여러 도메인이 공유하는 테이블이라 row 자체를 지우지는 않는다).
        Integer fileGroupId = request.clearFileGroup() ? null
                : request.fileGroupId() != null
                        ? request.fileGroupId()
                        : (program.getFileGroup() != null ? program.getFileGroup().getFileGroupId() : null);

        /**
         * (d-1) 첨부파일 검증 ----------------------------------------------------------
         * 요청이 실제로 새 fileGroupId를 지정한 경우(기존과 다른 값)에만 검증한다 — 기존에 이미
         * 이 프로그램에 연결돼 있던 fileGroupId를 그대로 유지하는 경우까지 재검증할 필요는 없다.
         */
        boolean isNewFileGroup = request.fileGroupId() != null
                && (program.getFileGroup() == null
                        || !request.fileGroupId().equals(program.getFileGroup().getFileGroupId()));
        if (isNewFileGroup) {
            validateFileGroupForLinking(fileGroupId, currentUserId, programId);
        }

        /**
         * (e) 실제 DB 반영 -------------------------------------------------------------
         * updatedRows는 이번 UPDATE 문으로 실제 몇 개의 row가 바뀌었는지를 담는다(보통 0 또는 1).
         */
        Optional<MileagePolicy> resolvedMileagePolicy = findActiveExtracurricularPolicy(request.programTypeCodeId());

        int updatedRows;
        try {
            updatedRows = programRepository.updateProgram(
                    programId,
                    fileGroupId,
                    operatingUnitCodeId,
                    request.programTypeCodeId(),
                    request.competencyId(),
                    resolvedMileagePolicy.map(MileagePolicy::getMileagePolicyId).orElse(null),
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
            /**
             * register()와 마찬가지로, 존재하지 않는 참조값(FK)을 넣었을 때 DB가 던지는 예외를
             * 어떤 참조값이 문제였는지에 맞는 404 에러로 바꿔준다. 등록 때 만든 메서드를 그대로 재사용한다.
             */
            throw resolveForeignKeyViolation(e);
        }

        /**
         * updatedRows가 0이라는 것은, (c)에서 모집중임을 확인한 "직후"부터 실제 UPDATE 문이
         * 실행되기 "직전" 사이의 아주 짧은 순간에 모집 종료 시각이 지나버렸다는 뜻이다
         * (native UPDATE 쿼리의 WHERE 절에도 recruitment_ends_at > :now 조건이 걸려있기 때문).
         * 이런 경우도 결국 "지금은 수정할 수 없는 상태"이므로 (c)와 같은 에러를 던진다.
         */
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.PROGRAM_NOT_EDITABLE);
        }

        // 수정에 성공했으니, 방금 반영한 값들을 그대로 담아 응답 DTO를 만들어 돌려준다.
        return new ProgramUpdateResponseDTO(
                programId,
                request.programName(),
                /**
                 * 이 API는 모집중(DRAFT)일 때만 성공하므로 항상 "모집중"이지만, 하드코딩 대신
                 * 방금 읽어온 엔티티의 실제 상태를 라벨로 변환해 내려준다.
                 */
                program.getProgramStatus().getLabel(),
                request.capacity(),
                completionRate,
                resolvedMileagePolicy.map(MileagePolicy::getMileagePolicyId).orElse(null),
                resolvedMileagePolicy.map(MileagePolicy::getPoints).orElse(null),
                resolvedMileagePolicy.map(policy -> policy.getActivityType().getActivityName()).orElse(null),
                request.recruitmentStartsAt(),
                request.recruitmentEndsAt(),
                request.operationStartsAt(),
                request.operationEndsAt(),
                now
        );
    }

    /**
     * ── "목록 조회(List)" 기능 ──────────────────────────────────────────────
     *
     * 학생이 프로그램 목록 페이지에서 탐색할 때 쓰는 조회. 상태/이름 키워드/연계 핵심역량으로 걸러 페이지 단위로 내려준다.
     * 셋 다 없으면 전체 프로그램을 페이지네이션해서 반환한다. 정렬(신규순=createdAt, 마감임박순=recruitmentEndsAt 등)은
     * 이 메서드가 아니라 Pageable의 sort 쿼리 파라미터로 이미 처리된다(ExtracurricularProgramRepositoryImpl.resolveOrderSpecifiers 참고).
     */
    @Transactional(readOnly = true)
    public PageResponse<ProgramListItemResponseDTO> list(ProgramStatus status, String keyword, Integer competencyId,
                                                           Integer studentId, Pageable pageable) {
        Page<ExtracurricularProgram> page = programRepository.search(status, keyword, competencyId, pageable);
        Map<Integer, Long> applicantCounts = countApplicantsByProgram(page.getContent());
        Map<Integer, String> myApplicationStatuses = findMyApplicationStatuses(page.getContent(), studentId);
        return PageResponse.from(page.map(program ->
                ProgramListItemResponseDTO.from(program, applicantCounts.getOrDefault(program.getProgramId(), 0L),
                        myApplicationStatuses.get(program.getProgramId()))));
    }

    /**
     * ── "staff용 목록 조회(List)" 기능 ──────────────────────────────────────
     *
     * list()와 거의 같지만, 로그인한 staff 본인이 담당(managerUser)한 프로그램으로만 범위를 좁힌다.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProgramStaffListItemResponseDTO> listMine(Integer managerUserId, ProgramStatus status,
                                                                    String keyword, Integer competencyId,
                                                                    Pageable pageable) {
        Page<ExtracurricularProgram> page = programRepository.searchByManager(
                managerUserId, status, keyword, competencyId, pageable);
        Map<Integer, Long> applicantCounts = countApplicantsByProgram(page.getContent());
        // listMine()은 이미 managerUserId 조건으로 본인 소유만 조회하므로, 여기서는 모집 마감 시각만 비교하면 된다.
        Instant now = Instant.now();
        return PageResponse.from(page.map(program -> ProgramStaffListItemResponseDTO.from(
                program, applicantCounts.getOrDefault(program.getProgramId(), 0L),
                now.isBefore(program.getRecruitmentEndsAt()))));
    }

    /**
     * ── "학생용 상세 조회(Detail)" 기능 ──────────────────────────────────────
     *
     * 학생이 프로그램 상세 화면에서 볼 기본정보 전체 + 회차 목록 + 신청자 수를 한 번에 조립한다.
     */
    @Transactional(readOnly = true)
    public ProgramDetailResponseDTO getDetail(Integer programId, Integer studentId) {
        ExtracurricularProgram program = programRepository.findDetailById(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        List<ProgramSessionResponseDTO> sessions = programSessionRepository
                .findByProgram_ProgramIdOrderBySessionNoAsc(programId)
                .stream()
                .map(ProgramSessionResponseDTO::from)
                .toList();

        long applicantCount = applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(
                programId, List.of(ApplicationStatus.APPLIED.name(), ApplicationStatus.APPROVED.name()));

        // CANCELLED는 apply()가 재신청 대상으로 취급하는 상태라, "신청 안 한 것"과 동일하게 null로 내려준다
        // (findMyApplicationStatusesByProgramIds와 같은 이유 — 재신청 가능한 프로그램에서 버튼을 숨기면 안 됨).
        String myApplicationStatus = applicationRepository
                .findByProgram_ProgramIdAndStudent_UserId(programId, studentId)
                .map(ProgramApplication::getApplicationStatus)
                .filter(status -> !ApplicationStatus.CANCELLED.name().equals(status))
                .orElse(null);

        String fileName = program.getFileGroup() != null
                ? fileGroupService.getFiles(program.getFileGroup()).stream()
                        .findFirst()
                        .map(StoredFile::getOriginalFileName)
                        .orElse(null)
                : null;

        return ProgramDetailResponseDTO.from(program, applicantCount, sessions, myApplicationStatus, fileName);
    }

    /**
     * ── "운영계획서 다운로드" 기능 ──────────────────────────────────────────────
     *
     * 학생 상세 조회와 동일한 권한(로그인한 학생이면 조회 가능)으로 프로그램에 연결된
     * 운영계획서 원본 파일을 내려받는다. 별도 소유자 검증은 하지 않는다 — 상세 조회가
     * 가능한 프로그램이면 첨부파일도 볼 수 있다는 전제.
     */
    @Transactional(readOnly = true)
    public FileStorageService.LoadedFile downloadOperationPlan(Integer programId) {
        ExtracurricularProgram program = programRepository.findById(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        FileGroup fileGroup = program.getFileGroup();
        if (fileGroup == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        StoredFile storedFile = fileGroupService.getFiles(fileGroup).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        return fileStorageService.load(storedFile.getStoredFileId());
    }

    /**
     * ── "staff용 상세 조회(Detail)" 기능 ──────────────────────────────────────
     *
     * getDetail()과 거의 같지만, 등록자 본인만 조회 가능하도록 소유자 검증을 추가하고
     * 수정/삭제 가능 여부(isEditable/isDeletable)를 함께 계산해 내려준다.
     */
    @Transactional(readOnly = true)
    public ProgramStaffDetailResponseDTO getMyDetail(Integer programId, Integer currentUserId) {
        ExtracurricularProgram program = programRepository.findDetailById(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        if (!program.getManagerUser().getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<ProgramSessionResponseDTO> sessions = programSessionRepository
                .findByProgram_ProgramIdOrderBySessionNoAsc(programId)
                .stream()
                .map(ProgramSessionResponseDTO::from)
                .toList();

        long applicantCount = applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(
                programId, List.of(ApplicationStatus.APPLIED.name(), ApplicationStatus.APPROVED.name()));

        boolean editable = Instant.now().isBefore(program.getRecruitmentEndsAt());

        return ProgramStaffDetailResponseDTO.from(program, applicantCount, sessions, editable);
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

    // 목록 페이지 한 번에 해당하는 프로그램들에 대한 로그인 학생 본인의 신청 상태를 한 번의 쿼리로 배치 조회한다(N+1 방지).
    private Map<Integer, String> findMyApplicationStatuses(List<ExtracurricularProgram> programs, Integer studentId) {
        if (programs.isEmpty()) {
            return Map.of();
        }
        List<Integer> programIds = programs.stream().map(ExtracurricularProgram::getProgramId).toList();
        return applicationRepository.findMyApplicationStatusesByProgramIds(studentId, programIds).stream()
                .collect(Collectors.toMap(
                        ProgramApplicationRepository.MyApplicationStatusProjection::getProgramId,
                        ProgramApplicationRepository.MyApplicationStatusProjection::getStatus));
    }

    /**
     * ── "삭제(Delete)" 기능 ──────────────────────────────────────────────
     *
     * 프로그램 하나를 삭제하는 메서드. 매개변수 2개의 의미:
     *   programId        : 삭제할 프로그램의 PK (URL 경로에서 옴)
     *   currentUserId    : 지금 로그인해서 이 요청을 보낸 사용자의 id (인증 정보에서 옴, 클라이언트가 위조 불가)
     *   departmentCodeId : 로그인한 사용자가 소속된 부서의 CommonCode PK (인증 정보에서 옴, 클라이언트가 위조 불가)
     *                    → update()와 동일한 이유로, 비교과운영부서(D200) 소속인지 매번 다시 검증한다.
     */
    public void delete(Integer programId, Integer currentUserId, Integer departmentCodeId) {

        // (a) 존재 확인 ------------------------------------------------------------
        ExtracurricularProgram program = programRepository.findById(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        /**
         * (a-1) 부서 권한 확인 -----------------------------------------------------------
         * update()의 (a-1)단계와 동일한 이유로, 지금도 비교과운영부서(D200) 소속인지 검증한다.
         */
        if (!isOperatingDepartment(departmentCodeId)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_FORBIDDEN);
        }

        /**
         * (b) 소유자 확인 ------------------------------------------------------------
         * update()와 동일한 이유로, 등록자 본인이 아니면 403 Forbidden.
         */
        if (!program.getManagerUser().getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        /**
         * (c) 삭제 가능한 상태인지 확인 ------------------------------------------------
         * update()의 (c)단계와 완전히 같은 조건: 모집중(현재 시각이 모집 종료 시각 이전)일 때만 허용한다.
         */
        Instant now = Instant.now();
        if (!now.isBefore(program.getRecruitmentEndsAt())) {
            throw new BusinessException(ErrorCode.PROGRAM_NOT_DELETABLE);
        }

        /**
         * (d) 실제 DB 반영 -------------------------------------------------------------
         * deletedRows가 0이라는 것은, (c)에서 모집중임을 확인한 "직후"부터 실제 DELETE 문이 실행되기
         * "직전" 사이의 아주 짧은 순간에 모집 종료 시각이 지나버렸다는 뜻이다(native DELETE 쿼리의
         * WHERE 절에도 recruitment_ends_at > :now 조건이 걸려있기 때문). 이런 경우도 결국
         * "지금은 삭제할 수 없는 상태"이므로 (c)와 같은 에러를 던진다.
         */
        int deletedRows = programRepository.deleteProgram(programId, now);
        if (deletedRows == 0) {
            throw new BusinessException(ErrorCode.PROGRAM_NOT_DELETABLE);
        }
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

    /**
     * 프로그램 유형(programTypeCodeId)에 대응하는 비교과 마일리지 정책을 자동으로 찾아 그 id를 반환한다.
     * WP-261: 등록/수정 시점에 마일리지 정책을 수동으로 고르지 않고, 프로그램 유형 선택만으로 자동 매핑되게 한다.
     * 여기서 못 찾아도(시드 데이터 미존재, 유효기간 밖 등) 등록 자체를 막지 않고 null로 둔다 — 이수 완료 후
     * 마일리지 적립 시점에 ProgramMileageAccrualService가 같은 조건으로 다시 동적 조회하기 때문에, 이 값은
     * 최신 상태를 미리 채워두는 최적화일 뿐 필수 로직이 아니다.
     */
    private Integer resolveMileagePolicyId(Integer programTypeCodeId) {
        return findActiveExtracurricularPolicy(programTypeCodeId)
                .map(MileagePolicy::getMileagePolicyId)
                .orElse(null);
    }

    private Optional<MileagePolicy> findActiveExtracurricularPolicy(Integer programTypeCodeId) {
        if (programTypeCodeId == null) {
            return Optional.empty();
        }
        return commonCodeRepository.findById(programTypeCodeId)
                .filter(programTypeCode -> PROGRAM_TYPE_GROUP.equals(programTypeCode.getCodeGroup()))
                .flatMap(programTypeCode -> mileagePolicyRepository
                        .findActiveExtracurricularPoliciesByProgramTypeOn(
                                programTypeCode.getCode(),
                                ExtracurricularMileagePolicyDefinition.CATEGORY_CODE,
                                ExtracurricularMileagePolicyDefinition.EARNING_ROUTE,
                                LocalDate.now())
                        .stream()
                        .findFirst());
    }

    /**
     * WP-261 자동 매핑 로직(resolveMileagePolicyId)과 동일한 조건으로 조회하되, 등록/수정을 실행하지 않고도
     * 프론트 폼에서 programTypeCodeId 선택 시점에 매핑 결과를 미리 확인할 수 있게 한다.
     */
    @Transactional(readOnly = true)
    public ProgramMileagePolicyPreviewResponseDTO previewMileagePolicy(Integer programTypeCodeId) {
        if (programTypeCodeId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "programTypeCodeId는 필수입니다.");
        }
        MileagePolicy policy = findActiveExtracurricularPolicy(programTypeCodeId).orElse(null);
        return ProgramMileagePolicyPreviewResponseDTO.from(programTypeCodeId, policy);
    }

    /**
     * FK 제약 위반 메시지에는 PostgreSQL이 항상 위반된 컬럼명을 "Key (컬럼명)=(값) is not present..." 형식으로 담아준다.
     * 제약 조건 이름(마이그레이션에서 어떻게 명명했는지)에 기대지 않고 컬럼명 문자열만으로 어떤 참조 값이 없는지 구분한다.
     * (DbConstraintViolationMatcher가 원인 예외 메시지에서 토큰 포함 여부를 안전하게 검사해준다.)
     */
    private BusinessException resolveForeignKeyViolation(DataIntegrityViolationException e) {
        /**
         * file_group_id는 유니크 제약(uq_extracurricular_program_file_group_id) 위반일 수도 있다
         * (동시에 두 요청이 같은 fileGroupId를 서로 다른 프로그램에 연결하려 한 경쟁 조건 케이스).
         * validateFileGroupForLinking()의 애플리케이션 계층 검사가 이미 흔한 경우는 막아주지만,
         * 레이스 상황에 대한 최종 방어선은 이 DB 제약이다. file_group_id는 컬럼명 자체도 FK 제약
         * 위반 메시지에 등장할 수 있어(아래 컬럼명 매칭 방식과 달리) 컬럼명이 아닌 제약조건 이름으로 구분한다.
         */
        if (DbConstraintViolationMatcher.contains(e, "uq_extracurricular_program_file_group_id")) {
            return new BusinessException(ErrorCode.PROGRAM_FILE_GROUP_ALREADY_LINKED);
        }
        // 메시지 안에 "operating_unit_code_id"라는 컬럼명이 들어있다면, 존재하지 않는 운영 단위 코드를 참조한 것.
        if (DbConstraintViolationMatcher.contains(e, "operating_unit_code_id")) {
            return new BusinessException(ErrorCode.OPERATING_UNIT_NOT_FOUND);
        }
        // "program_type_code_id"가 들어있다면, 존재하지 않는 프로그램 유형(분류) 코드를 참조한 것.
        if (DbConstraintViolationMatcher.contains(e, "program_type_code_id")) {
            return new BusinessException(ErrorCode.PROGRAM_CATEGORY_NOT_FOUND);
        }
        // "competency_id"가 들어있다면, 존재하지 않는 핵심역량을 참조한 것.
        if (DbConstraintViolationMatcher.contains(e, "competency_id")) {
            return new BusinessException(ErrorCode.COMPETENCY_NOT_FOUND);
        }
        // "mileage_policy_id"가 들어있다면, 존재하지 않는 마일리지 정책을 참조한 것.
        if (DbConstraintViolationMatcher.contains(e, "mileage_policy_id")) {
            return new BusinessException(ErrorCode.MILEAGE_POLICY_NOT_FOUND);
        }
        // 위 4개 컬럼 중 어디에도 해당하지 않는 예상치 못한 FK 위반이라면, 일반적인 "입력값 오류"로 처리한다.
        return new BusinessException(ErrorCode.INVALID_INPUT, "요청 값이 올바르지 않습니다.");
    }

    /**
     * 세 가지 논리적 제약을 검사한다: 모집 시작<종료, 운영 시작<종료, 모집 종료<=운영 시작(모집이 끝나야 운영이 시작됨).
     * register()와 update() 양쪽에서 공통으로 쓸 수 있도록, DTO 타입 전체가 아니라 Instant 값 4개만 파라미터로 받는다.
     * (두 DTO는 필드 구성은 같지만 서로 다른 record 타입이라 공통 인터페이스가 없기 때문에, 원시 값을 뽑아서 넘기는 방식을 택했다.)
     */
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
