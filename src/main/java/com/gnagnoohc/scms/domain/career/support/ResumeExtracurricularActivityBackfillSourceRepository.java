package com.gnagnoohc.scms.domain.career.support;

import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 이력서 비교과 이력 백필 전용 — program 도메인의 완료 신청 건을 career 도메인 쪽에서 직접 조회하기
 * 위한 리포지토리.
 *
 * <p>program 도메인이 소유한 기존 파일({@code ProgramApplicationRepository} 등)은 건드리지 않는다 —
 * career 도메인만 쓰는 조회를 program 도메인 소유 파일에 추가하면 그 파일의 리뷰/소유 경계가
 * 흐려지므로, 이 조회는 career 도메인이 소유하는 별도 리포지토리로 분리했다. {@code ProgramApplication}
 * 엔티티 자체는 여전히 program 도메인 소유지만, JPA 리포지토리 인터페이스는 엔티티가 어느 패키지에
 * 있든 어디서나 선언할 수 있다(영속성 컨텍스트는 엔티티 단위로 동작할 뿐 리포지토리 인터페이스의
 * 위치와는 무관하다). {@link ResumeExtracurricularActivityBackfillRunner}가 상세 조회에 쓰는
 * {@code ProgramApplicationRepository.findWithProgramDetailsByApplicationIdIn}은 이미 이 용도로
 * 존재하던 기존 메서드를 그대로 재사용하는 것이라 이 리포지토리로 옮기지 않았다.</p>
 */
public interface ResumeExtracurricularActivityBackfillSourceRepository extends JpaRepository<ProgramApplication, Integer> {

    /**
     * 이력서 비교과 이력 읽기 모델 도입 이전에 이미 COMPLETED로 판정된 신청 건 id를 applicationId
     * 오름차순 슬라이스로 순회한다. 페이지 단위로 끊어 백필 러너가 전체를 메모리에 한 번에 올리지
     * 않게 한다(competency 도메인의 {@code AssessmentAttemptRepository.findSubmittedAttemptIds}와
     * 같은 이유).
     */
    @Query("""
            select a.applicationId
            from ProgramApplication a
            where a.completionStatus = 'COMPLETED'
            order by a.applicationId asc
            """)
    Slice<Integer> findCompletedApplicationIds(Pageable pageable);
}
