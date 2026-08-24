package com.gnagnoohc.scms.domain.mileage.repository;

import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MileageActivityTypeRepository extends JpaRepository<MileageActivityType, Integer> {

    List<MileageActivityType> findAllByActiveTrueOrderByActivityNameAsc();

    /**
     * 활동유형 row에 비관적 락을 걸어 조회한다(ExtracurricularProgramRepository.findByIdForUpdate와 동일 패턴).
     * 같은 활동유형에 대한 마일리지 정책 등록이 동시에 들어와도, 버전 채번(findNextVersionNo)과
     * 실제 INSERT 사이에 다른 등록이 끼어들어 같은 버전 번호를 계산하는 경쟁 조건을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM MileageActivityType a WHERE a.activityTypeId = :activityTypeId")
    Optional<MileageActivityType> findByIdForUpdate(@Param("activityTypeId") Integer activityTypeId);
}
