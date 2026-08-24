package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingReservationDetailResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingReservationRequest;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingReservationResponse;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSchedule;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingType;
import com.gnagnoohc.scms.domain.counsel.repository.CounselUserRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingConsentRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingReservationRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingScheduleRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingTypeRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 학생의 DIRECT(온라인 신청) 직접 예약을 생성하고 본인 예약만 조회한다.
 * CENTER(센터 접수)형 신청은 체크리스트 12번(상담센터 접수 처리) 구현 전까지 거절한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CounselingReservationService {

    private static final String DIRECT_ROUTE = "DIRECT";
    private static final String CENTER_ROUTE = "CENTER";

    private final CounselUserRepository counselUserRepository;
    private final CounselingTypeRepository counselingTypeRepository;
    private final CounselingScheduleRepository counselingScheduleRepository;
    private final CounselingReservationRepository counselingReservationRepository;
    private final CounselingConsentRepository counselingConsentRepository;

    /**
     * 학생 행을 먼저 잠가 같은 학생의 서로 다른 일정 예약도 순서대로 검증한다.
     * 직접 예약은 이어서 일정 행을 잠가 정원과 마감 조건을 최종 확인한다.
     */
    @Transactional
    public CounselingReservationResponse create(
            CounselingReservationRequest request,
            Integer studentId
    ) {
        Instant now = Instant.now();
        AppUser student = getActiveStudentForUpdate(studentId);
        CounselingType counselingType = getActiveCounselingType(request.counselingTypeId());
        UserConsent userConsent = getValidConsent(request.consentId(), studentId, now);
        CounselingSchedule counselingSchedule = getScheduleForReservation(
                request.scheduleId(),
                counselingType,
                studentId,
                now
        );

        CounselingReservation reservation = CounselingReservation.create(
                counselingType,
                counselingSchedule,
                student,
                userConsent,
                request.requestContent()
        );
        return CounselingReservationResponse.from(counselingReservationRepository.save(reservation));
    }

    public PageResponse<CounselingReservationResponse> getReservations(
            Integer studentId,
            int page,
            int size
    ) {
        ensureActiveStudent(studentId);
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return PageResponse.from(counselingReservationRepository
                .findAllByStudentUserId(studentId, pageRequest)
                .map(CounselingReservationResponse::from));
    }

    public CounselingReservationDetailResponse getReservation(
            Integer reservationId,
            Integer studentId
    ) {
        ensureActiveStudent(studentId);
        CounselingReservation reservation = counselingReservationRepository
                .findByCounselingReservationIdAndStudentUserId(reservationId, studentId)
                // 다른 학생의 예약인지와 없는 예약인지를 구분하지 않아 예약 존재를 노출하지 않는다.
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        return CounselingReservationDetailResponse.from(reservation);
    }

    private AppUser getActiveStudentForUpdate(Integer studentId) {
        AppUser student = counselUserRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        if (!isActiveStudent(student)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return student;
    }

    private void ensureActiveStudent(Integer studentId) {
        if (!counselUserRepository.isActiveStudent(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean isActiveStudent(AppUser student) {
        return "STUDENT".equals(student.getUserType())
                && "ACTIVE".equals(student.getAccountStatus());
    }

    private CounselingType getActiveCounselingType(Integer counselingTypeId) {
        return counselingTypeRepository
                .findByCounselingTypeIdAndActiveTrue(counselingTypeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private UserConsent getValidConsent(Integer consentId, Integer studentId, Instant now) {
        if (consentId == null) {
            return null;
        }
        return counselingConsentRepository.findValidByIdAndStudentId(consentId, studentId, now)
                // 다른 학생의 동의나 철회 이력의 존재는 공개하지 않는다.
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private CounselingSchedule getScheduleForReservation(
            Integer scheduleId,
            CounselingType counselingType,
            Integer studentId,
            Instant now
    ) {
        if (DIRECT_ROUTE.equals(counselingType.getApplicationRoute())) {
            return getAvailableDirectSchedule(scheduleId, counselingType, studentId, now);
        }
        // CENTER(센터 접수)는 체크리스트 12번(상담센터 접수 처리) 구현 전까지 학생 신청 경로에서 제외한다.
        // 프론트 필터만으로는 유형 ID를 직접 보낸 요청을 막지 못하므로 서버가 최종 방어선으로 거절한다.
        // 상담사 일정 등록의 CENTER 거절(CounselingScheduleService)과 같은 400 C001(INVALID_INPUT)로 통일한다.
        // enum·시드는 유지하므로 12번 착수 시 REQUESTED 예약 생성 로직을 복원한다.
        if (CENTER_ROUTE.equals(counselingType.getApplicationRoute())) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "센터 접수(CENTER) 상담은 아직 신청할 수 없습니다."
            );
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }

    private CounselingSchedule getAvailableDirectSchedule(
            Integer scheduleId,
            CounselingType counselingType,
            Integer studentId,
            Instant now
    ) {
        if (scheduleId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        CounselingSchedule schedule = counselingScheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_AVAILABLE));
        boolean matchingType = schedule.getCounselingType().getCounselingTypeId()
                .equals(counselingType.getCounselingTypeId());
        boolean beforeDeadline = schedule.getBookingDeadline() == null
                || schedule.getBookingDeadline().isAfter(now);
        boolean capacityAvailable = counselingReservationRepository
                .countOccupiedReservations(scheduleId) < schedule.getCapacity();
        boolean activeCounselor = "ACTIVE".equals(schedule.getCounselor().getAccountStatus())
                && counselUserRepository.hasCounselorRole(schedule.getCounselor().getUserId());
        if (!matchingType
                || !schedule.isOpen()
                || !schedule.getStartsAt().isAfter(now)
                || !beforeDeadline
                || !capacityAvailable
                || !activeCounselor
                || counselingReservationRepository.existsOverlappingActiveReservation(
                        studentId,
                        schedule.getStartsAt(),
                        schedule.getEndsAt()
                )) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_AVAILABLE);
        }
        return schedule;
    }
}
