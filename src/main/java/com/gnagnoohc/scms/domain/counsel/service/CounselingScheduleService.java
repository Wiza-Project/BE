package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingScheduleRequest;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingScheduleAvailabilityResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingScheduleResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselorScheduleResponse;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSchedule;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingType;
import com.gnagnoohc.scms.domain.counsel.repository.CounselUserRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingReservationRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingScheduleRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingSessionRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingTypeRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 상담 일정 등록·전체 수정·마감의 권한, 불변식과 트랜잭션 경계를 담당한다.
 * 검사부터 상태 변경까지 같은 트랜잭션에서 처리하므로 중간에 예외가 발생하면 변경 내용이 함께 롤백된다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CounselingScheduleService {

    // 상담사가 개별 일정을 열 수 있는 신청 경로. CENTER 유형은 센터 접수 방식이라 일정 대상이 아니다..
    private static final String APPLICATION_ROUTE_DIRECT = "DIRECT";

    private final CounselUserRepository counselUserRepository;
    private final CounselingTypeRepository counselingTypeRepository;
    private final CounselingScheduleRepository counselingScheduleRepository;
    private final CounselingReservationRepository counselingReservationRepository;
    private final CounselingSessionRepository counselingSessionRepository;
    private final CounselManagementAccessPolicy counselManagementAccessPolicy;

    /**
     * 활성 학생만 예약 가능한 일정을 조회할 수 있으며 조회 중에는 일정 행을 잠그지 않는다.
     */
    @Transactional(readOnly = true)
    public List<CounselingScheduleAvailabilityResponse> getAvailableSchedules(
            Integer counselingTypeId,
            Integer studentId
    ) {
        if (!counselUserRepository.isActiveStudent(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        getActiveCounselingType(counselingTypeId);

        return counselingScheduleRepository.findAvailableSchedules(
                counselingTypeId,
                Instant.now()
        );
    }

    /**
     * 상담사는 과거와 마감된 일정을 포함해 본인 소유 일정만 최신 시작 시각 순으로 조회한다.
     * 목록의 예약 이력 표시는 화면 안내용이며, 수정 시에는 별도 트랜잭션에서 다시 검증한다.
     * ST200+ST300 사용자는 CS200 일정만 봐야 하므로, 조회 조건 자체에 careerOnly를 넘겨
     * 다른 유형의 일정 행을 애초에 읽지 않는다(조회 후 메모리에서 걸러내지 않는다).
     */
    @Transactional(readOnly = true)
    public List<CounselorScheduleResponse> getCounselorSchedules(Integer counselorId) {
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselorId);
        boolean careerOnly = scope == CounselManagementAccessPolicy.Scope.CAREER_ONLY;
        return counselingScheduleRepository.findCounselorSchedules(counselorId, careerOnly);
    }

    /**
     * 활성 상담사가 미래의 겹치지 않는 OPEN 일정을 등록한다.
     * 사용자 행 잠금 뒤 시간 조건을 다시 검사해 잠금을 기다리는 동안 과거가 된 요청도 저장하지 않는다.
     */
    public CounselingScheduleResponse create(
            CounselingScheduleRequest request,
            Integer counselorId
    ) {
        validateRequest(request, Instant.now());
        CounselingType counselingType = getSchedulableCounselingType(request.counselingTypeId());

        // 같은 상담사의 빈 일정 구간을 동시에 선점하지 못하도록 사용자 행부터 잠근다.
        AppUser counselor = getActiveCounselorForUpdate(counselorId);
        // 역할 범위 검증은 잠금 뒤 얻은 scope로 최종 확정한다. URL 진입 이후 역할이 바뀌었더라도
        // 이 시점의 DB 값을 기준으로 판정하며, 부작용(겹침 조회·저장)보다 먼저 끝내야 검증 실패 시
        // 어떤 행도 바뀌지 않는다.
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselor);
        ensureTypeAllowed(scope, counselingType);
        validateRequest(request, Instant.now());
        ensureNoOverlap(counselorId, request.startsAt(), request.endsAt());
        ensureNoSessionOverlap(counselorId, request.startsAt(), request.endsAt());

        CounselingSchedule schedule = CounselingSchedule.create(
                counselingType,
                counselor,
                request.startsAt(),
                request.endsAt(),
                request.capacity(),
                request.bookingDeadline(),
                request.location()
        );
        return CounselingScheduleResponse.from(counselingScheduleRepository.save(schedule));
    }

    /**
     * 사용자 행 다음 대상 일정 행 순서로 잠근 뒤 본인 소유·OPEN·예약 이력 없음 조건을 검사한다.
     * 같은 잠금 순서를 지켜 동시 수정과 마감 사이의 교착 가능성을 줄인다.
     */
    public CounselingScheduleResponse update(
            Integer scheduleId,
            CounselingScheduleRequest request,
            Integer counselorId
    ) {
        validateRequest(request, Instant.now());
        CounselingType counselingType = getSchedulableCounselingType(request.counselingTypeId());

        // 모든 변경 경로가 사용자 행 다음 일정 행을 잠가 잠금 순서를 통일한다.
        AppUser counselor = getActiveCounselorForUpdate(counselorId);
        CounselingSchedule schedule = getScheduleForUpdate(scheduleId);
        ensureOwnerAndOpen(schedule, counselorId);
        // 소유권 검사 통과 뒤, 실제로 상태를 바꾸기 전에 기존 유형과 신규 요청 유형을 모두 검사한다.
        // CAREER_ONLY 사용자는 본인 소유 일정이라도 비CS200 일정은 건드릴 수 없어야 하므로,
        // "그 일정이 원래 CS200이었는지"와 "새로 옮기려는 유형이 CS200인지"를 둘 다 확인해야 한다.
        // 신규 유형만 검사하면 CAREER_ONLY가 CS200으로 위장한 새 값을 보내 비CS200 기존 일정을
        // 우회 수정할 길이 남는다. 여기서 걸리면 어떤 행도 아직 바뀌지 않았다.
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselor);
        ensureTypeAllowed(scope, schedule.getCounselingType());
        ensureTypeAllowed(scope, counselingType);
        validateRequest(request, Instant.now());

        if (counselingReservationRepository
                .existsByCounselingScheduleCounselingScheduleId(scheduleId)) {
            throw new BusinessException(
                    ErrorCode.SCHEDULE_NOT_AVAILABLE,
                    "예약 이력이 있는 일정은 수정할 수 없습니다."
            );
        }
        if (counselingScheduleRepository.existsOverlappingScheduleExcluding(
                counselorId,
                scheduleId,
                request.startsAt(),
                request.endsAt()
        )) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_AVAILABLE);
        }
        ensureNoSessionOverlap(counselorId, request.startsAt(), request.endsAt());

        schedule.update(
                counselingType,
                request.startsAt(),
                request.endsAt(),
                request.capacity(),
                request.bookingDeadline(),
                request.location()
        );
        return CounselingScheduleResponse.from(schedule);
    }

    /**
     * 본인의 OPEN 일정만 CLOSED로 전환한다.
     * 일정 행 잠금이 동시에 들어온 수정이나 예약 처리가 이전 상태를 보고 진행하는 것을 막는다.
     */
    public CounselingScheduleResponse close(Integer scheduleId, Integer counselorId) {
        AppUser counselor = getActiveCounselorForUpdate(counselorId);
        CounselingSchedule schedule = getScheduleForUpdate(scheduleId);
        ensureOwnerAndOpen(schedule, counselorId);
        // 마감은 새 유형을 받지 않으므로 일정에 이미 연결된 유형을 기준으로 범위를 검사한다.
        // 이미 만들어진 다른 유형 일정도 그 유형이 나중에 CAREER_ONLY 범위 밖이 됐다는 이유만으로
        // 마감이 막히면 안 되지만, 마감은 상태를 바꾸는 조작이므로 여전히 현재 범위 안의 자원인지 확인한다.
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselor);
        ensureTypeAllowed(scope, schedule.getCounselingType());

        schedule.close();
        return CounselingScheduleResponse.from(schedule);
    }

    /**
     * 사용자 행만 잠근다. 활성·STAFF·ST200 여부와 역할 범위 판정은 호출부가
     * {@link CounselManagementAccessPolicy#requireScope(AppUser)}로 한 번에 확인한다.
     */
    private AppUser getActiveCounselorForUpdate(Integer counselorId) {
        return counselUserRepository.findByIdForUpdate(counselorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private void ensureTypeAllowed(CounselManagementAccessPolicy.Scope scope, CounselingType counselingType) {
        if (!counselManagementAccessPolicy.allows(
                scope, counselingType.getTypeCode(), counselingType.getApplicationRoute()
        )) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private CounselingType getActiveCounselingType(Integer counselingTypeId) {
        return counselingTypeRepository
                .findByCounselingTypeIdAndActiveTrue(counselingTypeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 일정 등록·수정 대상은 활성이면서 신청 경로가 DIRECT인 유형만 허용한다.
     * CENTER 유형은 상담사가 여는 개별 일정 대상이 아니므로, 유형 ID를 직접 보낸 요청이라도 서버에서 막는다.
     * (활성만 검사하는 {@link #getActiveCounselingType}은 학생의 예약 가능 일정 조회에서 계속 재사용한다.)
     */
    private CounselingType getSchedulableCounselingType(Integer counselingTypeId) {
        CounselingType counselingType = getActiveCounselingType(counselingTypeId);
        if (!APPLICATION_ROUTE_DIRECT.equals(counselingType.getApplicationRoute())) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "일정은 DIRECT(일정 지정) 유형에만 등록할 수 있습니다."
            );
        }
        return counselingType;
    }

    private CounselingSchedule getScheduleForUpdate(Integer scheduleId) {
        return counselingScheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void ensureOwnerAndOpen(CounselingSchedule schedule, Integer counselorId) {
        if (!schedule.isOwnedBy(counselorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!schedule.isOpen()) {
            throw new BusinessException(
                    ErrorCode.SCHEDULE_NOT_AVAILABLE,
                    "열린 일정만 변경할 수 있습니다."
            );
        }
    }

    private void ensureNoOverlap(Integer counselorId, Instant startsAt, Instant endsAt) {
        if (counselingScheduleRepository
                .existsOverlappingSchedule(counselorId, startsAt, endsAt)) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_AVAILABLE);
        }
    }

    /**
     * 상담 회기 관리 설계(3.2): 같은 상담사의 CANCELED가 아닌 상담 회기와도 반열린 구간으로
     * 겹칠 수 없다. 일정 등록·수정 모두 사용자 행을 먼저 잠근 뒤 호출되므로 회기 생성과 직렬화된다.
     */
    private void ensureNoSessionOverlap(Integer counselorId, Instant startsAt, Instant endsAt) {
        if (counselingSessionRepository
                .existsOverlappingSessionForCounselor(counselorId, startsAt, endsAt)) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_AVAILABLE);
        }
    }

    private void validateRequest(CounselingScheduleRequest request, Instant now) {
        boolean validCapacity = request.capacity() != null && request.capacity() >= 1;
        boolean startsInFuture = request.startsAt().isAfter(now);
        boolean validRange = request.startsAt().isBefore(request.endsAt());
        boolean validDeadline = request.bookingDeadline() == null
                || request.bookingDeadline().isAfter(now)
                && !request.bookingDeadline().isAfter(request.startsAt());
        if (!validCapacity || !startsInFuture || !validRange || !validDeadline) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "일정과 예약 마감 시각이 올바르지 않습니다."
            );
        }
    }
}
