package com.gnagnoohc.scms.domain.program.repository;

import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * MileageTransaction은 domain/mileage 소유 엔티티지만, 신청 내역 화면에서 "이 신청 건으로 적립된
 * 마일리지"를 보여주려면 program 도메인에서 이 값을 조회할 방법이 필요하다. domain/mileage 폴더는
 * 다른 담당자 소유라 손대지 않고, CompetencyOptionRepository와 같은 방식으로 domain/program 폴더
 * 안에서 엔티티를 직접 import해 조회 전용 Repository를 둔다.
 */
public interface ProgramMileageTransactionRepository extends JpaRepository<MileageTransaction, Integer> {

    /**
     * sourceProgramApplication은 신청 건당 최대 1건(unique FK)이므로, applicationId 목록을 넘기면
     * 각 신청 건에 대해 최대 하나씩의 적립 내역이 돌아온다.
     */
    List<MileageTransaction> findBySourceProgramApplication_ApplicationIdIn(List<Integer> applicationIds);
}
