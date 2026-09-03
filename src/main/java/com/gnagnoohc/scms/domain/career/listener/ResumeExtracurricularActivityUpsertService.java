package com.gnagnoohc.scms.domain.career.listener;

import com.gnagnoohc.scms.domain.career.entity.ResumeExtracurricularActivity;
import com.gnagnoohc.scms.domain.career.repository.ResumeExtracurricularActivityRepository;
import com.gnagnoohc.scms.domain.program.event.ExtracurricularActivityCompletedEvent;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ResumeExtracurricularActivityEventListener}가 위임하는 실제 저장 트랜잭션.
 *
 * <p>별도 빈으로 분리한 이유는 {@code ResumeCompetencySnapshotUpsertService}와 같다 — AFTER_COMMIT
 * 시점엔 활성 트랜잭션이 없으므로 새 트랜잭션이 필요하고, 자기호출로는 프록시가 우회돼 트랜잭션이
 * 열리지 않는다.</p>
 *
 * <p>다만 재시도 루프는 없다: {@code application_id}는 신청 건당 1행(insert-only)이라, 같은 이벤트가
 * 두 번 들어와도 두 번째가 담은 데이터는 첫 번째와 완전히 같다(같은 신청 건이 두 번 이수 확정되는
 * 케이스가 없음). 그래서 선확인으로 대부분 걸러내고, 그 사이 경합이 나도 그냥 건너뛰면 된다 — 되돌아가
 * 다시 반영할 데이터가 없다({@code ProgramMileageAccrualService.accrueProgramCompletion}과 동일한
 * "existsBy 선확인 → skip" 패턴).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeExtracurricularActivityUpsertService {

    private final ResumeExtracurricularActivityRepository resumeExtracurricularActivityRepository;
    private final AppUserRepository appUserRepository;

    /** @return 새로 저장했으면 true, 이미 반영된 신청 건이라 건너뛰었으면 false. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean save(ExtracurricularActivityCompletedEvent event) {
        if (resumeExtracurricularActivityRepository.existsByApplicationId(event.applicationId())) {
            log.info("이력서 비교과 이력 저장 건너뜀(이미 반영된 신청 건) — applicationId={}", event.applicationId());
            return false;
        }

        ResumeExtracurricularActivity activity = ResumeExtracurricularActivity.from(
                appUserRepository.getReferenceById(event.studentId()), event);
        resumeExtracurricularActivityRepository.save(activity);
        return true;
    }
}
