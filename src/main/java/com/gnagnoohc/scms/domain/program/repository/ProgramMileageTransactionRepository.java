package com.gnagnoohc.scms.domain.program.repository;

import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 신청 내역 목록 화면의 적립 마일리지 표시를 위해, program 도메인 안에서 MileageTransaction 엔티티를
 * 직접 참조해 신청 건별 확정 적립 점수를 조회한다. mileage 도메인 소유 레포지토리(MileageTransactionRepository)는
 * 건드리지 않는다.
 */
public interface ProgramMileageTransactionRepository extends JpaRepository<MileageTransaction, Integer> {

    /**
     * 신청 건 여러 개의 확정(POSTED) 적립 마일리지를 한 번에 조회한다(N+1 방지).
     * sourceProgramApplication은 유니크 FK라 신청 건당 최대 1건이지만, 페이지 전체를 한 번의 IN 쿼리로 가져온다.
     */
    @Query("""
        SELECT t.sourceProgramApplication.applicationId AS applicationId,
               t.points AS points
        FROM MileageTransaction t
        WHERE t.sourceProgramApplication.applicationId IN :applicationIds
          AND t.transactionStatus = 'POSTED'
        """)
    List<EarnedPointsProjection> findPostedPointsByApplicationIds(@Param("applicationIds") List<Integer> applicationIds);

    /** 신청 건별 확정 적립 마일리지 조회 전용 결과다. */
    interface EarnedPointsProjection {
        /** 신청 건 PK */
        Integer getApplicationId();
        /** 해당 신청 건에 대해 확정(POSTED)된 적립 마일리지 점수 */
        BigDecimal getPoints();
    }
}
