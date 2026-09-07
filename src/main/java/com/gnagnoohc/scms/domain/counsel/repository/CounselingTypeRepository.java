package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 상담 유형 엔티티의 영속성 조회를 담당한다.
 */
public interface CounselingTypeRepository extends JpaRepository<CounselingType, Integer> {

    /**
     * 새 일정은 활성 유형으로만 만들되, 비활성화된 유형을 참조하는 과거 일정 자체는 보존한다.
     */
    Optional<CounselingType> findByCounselingTypeIdAndActiveTrue(Integer counselingTypeId);

    /**
     * 학생 신청 화면과 상담사 일정 등록 화면 모두 활성이면서 신청 경로가 지정된(현재 DIRECT) 유형만 코드 순서로 제공한다.
     * CENTER 온라인 접수 유형은 현재 MVP 범위 밖(별도 정책 확정 전까지 거절)이라 상담사가 일정을 여는 대상도,
     * 학생이 신청할 수 있는 대상도 아니므로 이 조회에서 제외된다.
     */
    List<CounselingType> findAllByActiveTrueAndApplicationRouteOrderByTypeCodeAsc(String applicationRoute);

    /**
     * 스트레스 결과 기반 상담 제안 수락 전용 조회다. 제안한 상담사의 일정으로 제한하지 않고
     * 활성 CS300 + DIRECT 유형 자체를 코드로 바로 찾는다(설계 2장 확정 정책).
     */
    Optional<CounselingType> findByTypeCodeAndApplicationRouteAndActiveTrue(
            String typeCode,
            String applicationRoute
    );
}
