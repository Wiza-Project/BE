package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.CounselorPendingReservationResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselorReservationDecisionResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselorReservationDetailResponse;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingAssignment;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSchedule;
import com.gnagnoohc.scms.domain.counsel.repository.CounselUserRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingAssignmentRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingReservationRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 상담사가 자신의 일정에 걸린 DIRECT 예약(REQUESTED)을 승인·반려하고, 승인 시 최초 활성 배정을 만든다.
 * 예약 존재 여부는 "대상 없음 / 다른 상담사 일정 / 일정 없는 CENTER 예약"을 구분하지 않고 모두
 * S003(RESERVATION_NOT_FOUND)으로 묶어, 다른 상담사가 담당하는 예약이 있다는 사실 자체를 노출하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CounselorReservationService {

    private final CounselUserRepository counselUserRepository;
    private final CounselingReservationRepository counselingReservationRepository;
    private final CounselingAssignmentRepository counselingAssignmentRepository;

    public PageResponse<CounselorPendingReservationResponse> getPending(
            Integer counselorId,
            int page,
            int size
    ) {
        ensureActiveCounselor(counselorId);
        return PageResponse.from(counselingReservationRepository
                .findPendingByCounselor(counselorId, PageRequest.of(page, size))
                .map(CounselorPendingReservationResponse::from));
    }

    public CounselorReservationDetailResponse getReservation(Integer reservationId, Integer counselorId) {
        ensureActiveCounselor(counselorId);
        CounselingReservation reservation = counselingReservationRepository
                .findByIdAndCounselor(reservationId, counselorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        return CounselorReservationDetailResponse.from(reservation);
    }

    /**
     * 예약 행을 잠근 뒤 담당 상담사 본인인지 확인하고 승인 상태로 바꾼 다음, 같은 트랜잭션에서
     * 일정의 담당 상담사(=승인 처리자 본인)를 최초 활성 배정으로 등록한다.
     * approve()/reject()가 REQUESTED가 아니면 예외를 던지므로, 승인 재시도(같은 요청 중복 전송)로
     * 배정이 두 번 생성되지 않는다 — 첫 승인이 커밋된 뒤 재시도가 오면 이미 APPROVED라 S005로 막힌다.
     */
    @Transactional
    public CounselorReservationDecisionResponse approve(Integer reservationId, Integer counselorId) {
        ensureActiveCounselor(counselorId);
        Instant now = Instant.now();
        CounselingReservation reservation = getOwnedReservationForUpdate(reservationId, counselorId);

        // CLOSED는 신규 예약만 막는 상태이므로 여기서 schedule.isOpen()은 검사하지 않는다.
        // 이미 걸린 REQUESTED 예약은 일정이 마감됐어도 승인 가능해야 close()의 의미가 유지된다.
        // 대신 일정이 이미 시작(또는 경과)됐으면 과거 시각에 활성 배정이 생기므로 이것만 막는다.
        // reservation.approve() 등 어떤 필드 변경보다 먼저 던지므로 실패 시 부분 반영이 없다.
        CounselingSchedule schedule = reservation.getCounselingSchedule(); // 소유권 검증으로 이미 non-null 확정
        if (!schedule.getStartsAt().isAfter(now)) {
            throw new BusinessException(
                    ErrorCode.SCHEDULE_NOT_AVAILABLE,
                    "상담 시작 시각이 지나 승인할 수 없습니다."
            );
        }
        reservation.approve(counselorId, now);

        // 최초 배정 대상은 이 일정의 담당 상담사이며, 위 소유권 검사로 요청자 본인과 동일함이 이미
        // 확인됐고 요청자는 ensureActiveCounselor로 활성 ST200 상담사임도 확인됐다.
        // 즉 배정 대상의 활성·역할 검증을 별도로 다시 하지 않아도 이미 충족된 상태다.
        CounselingAssignment assignment = CounselingAssignment.create(
                reservation,
                schedule.getCounselor(),
                counselorId,
                null,
                now
        );
        counselingAssignmentRepository.save(assignment);
        return CounselorReservationDecisionResponse.from(reservation, assignment);
    }

    @Transactional
    public CounselorPendingReservationResponse reject(
            Integer reservationId,
            Integer counselorId,
            String decisionReason
    ) {
        ensureActiveCounselor(counselorId);
        Instant now = Instant.now();
        CounselingReservation reservation = getOwnedReservationForUpdate(reservationId, counselorId);
        reservation.reject(decisionReason, counselorId, now);
        return CounselorPendingReservationResponse.from(reservation);
    }

    /**
     * 잠금 조회(studentId 조건 없이 예약 행만 잠금) 후, 이 예약이 참조하는 일정의 담당 상담사가
     * 요청자 본인인지 여기서 검증한다. 일정이 없거나(CENTER) 다른 상담사의 일정이면 모두
     * RESERVATION_NOT_FOUND로 통일해, 존재 여부나 담당자 정보를 노출하지 않는다.
     */
    private CounselingReservation getOwnedReservationForUpdate(Integer reservationId, Integer counselorId) {
        CounselingReservation reservation = counselingReservationRepository
                .findByIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        CounselingSchedule schedule = reservation.getCounselingSchedule();
        if (schedule == null || !schedule.getCounselor().getUserId().equals(counselorId)) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        return reservation;
    }

    // URL 인가를 통과한 뒤에도 계정 상태나 ST200 역할이 변경될 수 있으므로,
    // 상태 변경 직전에 활성 상담사 여부를 다시 확인한다.
    private void ensureActiveCounselor(Integer counselorId) {
        if (!counselUserRepository.isActiveCounselor(counselorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
