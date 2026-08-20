package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 일정 수정 가능 여부를 판단할 때 필요한 예약 이력 조회를 담당한다.
 */
public interface CounselingReservationRepository extends JpaRepository<CounselingReservation, Integer> {

    /**
     * 취소·반려된 예약도 과거 이력이므로 상태와 관계없이 한 건이라도 있으면 일정 전체 수정을 막는다.
     */
    boolean existsByCounselingScheduleCounselingScheduleId(Integer scheduleId);
}
