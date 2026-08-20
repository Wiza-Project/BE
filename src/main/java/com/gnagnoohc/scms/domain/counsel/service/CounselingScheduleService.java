package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingScheduleRequest;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingScheduleAvailabilityResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingScheduleResponse;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSchedule;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingType;
import com.gnagnoohc.scms.domain.counsel.repository.CounselUserRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingReservationRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingScheduleRepository;
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

    private final CounselUserRepository counselUserRepository;
    private final CounselingTypeRepository counselingTypeRepository;
    private final CounselingScheduleRepository counselingScheduleRepository;
    private final CounselingReservationRepository counselingReservationRepository;

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
     * 활성 상담사가 미래의 겹치지 않는 OPEN 일정을 등록한다.
     * 사용자 행 잠금 뒤 시간 조건을 다시 검사해 잠금을 기다리는 동안 과거가 된 요청도 저장하지 않는다.
     */
    public CounselingScheduleResponse create(
            CounselingScheduleRequest request,
            Integer counselorId
    ) {
        validateRequest(request, Instant.now());
        CounselingType counselingType = getActiveCounselingType(request.counselingTypeId());

        // 같은 상담사의 빈 일정 구간을 동시에 선점하지 못하도록 사용자 행부터 잠근다.
        AppUser counselor = getActiveCounselorForUpdate(counselorId);
        validateRequest(request, Instant.now());
        ensureNoOverlap(counselorId, request.startsAt(), request.endsAt());

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
        CounselingType counselingType = getActiveCounselingType(request.counselingTypeId());

        // 모든 변경 경로가 사용자 행 다음 일정 행을 잠가 잠금 순서를 통일한다.
        getActiveCounselorForUpdate(counselorId);
        CounselingSchedule schedule = getScheduleForUpdate(scheduleId);
        ensureOwnerAndOpen(schedule, counselorId);
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
        getActiveCounselorForUpdate(counselorId);
        CounselingSchedule schedule = getScheduleForUpdate(scheduleId);
        ensureOwnerAndOpen(schedule, counselorId);

        schedule.close();
        return CounselingScheduleResponse.from(schedule);
    }

    private AppUser getActiveCounselorForUpdate(Integer counselorId) {
        AppUser counselor = counselUserRepository.findByIdForUpdate(counselorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        boolean active = "ACTIVE".equals(counselor.getAccountStatus());
        boolean counselorRole = counselUserRepository.hasCounselorRole(counselorId);
        if (!active || !counselorRole) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return counselor;
    }

    private CounselingType getActiveCounselingType(Integer counselingTypeId) {
        return counselingTypeRepository
                .findByCounselingTypeIdAndActiveTrue(counselingTypeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
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
