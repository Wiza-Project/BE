package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 예약당 활성 배정(ended_at IS NULL)을 조회한다.
 * 이 조회 자체에는 별도 잠금을 걸지 않는다. 승인·거절·취소는 모두 먼저 CounselingReservation
 * 행을 PESSIMISTIC_WRITE로 잠근 뒤에만 진행되므로, 그 예약에 딸린 활성 배정 조회도 이미
 * 예약 잠금을 통해 간접적으로 직렬화된다(같은 예약의 배정을 두 트랜잭션이 동시에 바꿀 수 없음).
 */
public interface CounselingAssignmentRepository extends JpaRepository<CounselingAssignment, Integer> {
    Optional<CounselingAssignment> findByCounselingReservationCounselingReservationIdAndEndedAtIsNull(
            Integer reservationId
    );
}
