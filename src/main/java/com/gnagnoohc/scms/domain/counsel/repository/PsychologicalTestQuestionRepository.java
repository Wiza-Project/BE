package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.entity.PsychologicalTestQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 검사 유형·버전별 활성 문항을 문항 번호순으로 조회한다.
 * 활성 버전 자체는 DB가 아니라 StressTestService의 상수로 고정되므로, 이 Repository는
 * "어떤 버전이 활성인지"를 판단하지 않고 호출부가 지정한 유형·버전을 그대로 조회만 한다.
 */
public interface PsychologicalTestQuestionRepository extends JpaRepository<PsychologicalTestQuestion, Integer> {

    List<PsychologicalTestQuestion> findByTestTypeAndTestVersionOrderByQuestionNoAsc(
            String testType,
            String testVersion
    );
}
