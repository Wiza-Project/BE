package com.gnagnoohc.scms.domain.career.support;

import com.gnagnoohc.scms.domain.career.listener.ResumeExtracurricularActivityUpsertService;
import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.domain.program.event.ExtracurricularActivityCompletedEvent;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 이력서 비교과 이력 읽기 모델 도입 이전에 이미 COMPLETED로 판정된 신청 건을 채우는 1회성 백필 러너.
 *
 * <p>{@code AssessmentResultBackfillRunner}(competency-resume-backfill)와 같은 구조다. program 도메인
 * 소유 파일은 하나도 수정하지 않는다 — COMPLETED 신청 id 조회는 career 도메인이 소유한
 * {@link ResumeExtracurricularActivityBackfillSourceRepository}로, 상세 fetch join은 program 도메인의
 * 기존(무수정) {@code ProgramApplicationRepository.findWithProgramDetailsByApplicationIdIn}으로 각각
 * 처리한다. 이미 계약을 충족한 {@link ExtracurricularActivityCompletedEvent#from}으로 이벤트를 조립한 뒤
 * 실시간 이벤트와 동일한 {@link ResumeExtracurricularActivityUpsertService#save} 경로로 적재한다 —
 * 실제 이벤트를 발행하지 않으므로(스케줄러/리스너를 거치지 않음) 매 기동마다 재실행돼도 별도 부작용이 없고,
 * applicationId 기준 idempotent upsert라 여러 번 실행해도 안전하다.</p>
 *
 * <p>매 기동마다 전량 재처리하지 않도록 전용 프로필({@code resume-extracurricular-backfill})로 가드한다 —
 * 운영자가 이 프로필을 켜고 한 번 기동할 때만 동작한다.</p>
 */
@Slf4j
@Component
@Profile("resume-extracurricular-backfill")
@RequiredArgsConstructor
public class ResumeExtracurricularActivityBackfillRunner implements CommandLineRunner {

    private static final int PAGE_SIZE = 200;

    private final ResumeExtracurricularActivityBackfillSourceRepository resumeExtracurricularActivityBackfillSourceRepository;
    private final ProgramApplicationRepository programApplicationRepository;
    private final ResumeExtracurricularActivityUpsertService resumeExtracurricularActivityUpsertService;

    @Override
    public void run(String... args) {
        int page = 0;
        long saved = 0;
        long skipped = 0;
        long failed = 0;
        Slice<Integer> slice;
        do {
            slice = resumeExtracurricularActivityBackfillSourceRepository.findCompletedApplicationIds(
                    PageRequest.of(page, PAGE_SIZE));
            List<Integer> applicationIds = slice.getContent();
            if (!applicationIds.isEmpty()) {
                for (ProgramApplication application :
                        programApplicationRepository.findWithProgramDetailsByApplicationIdIn(applicationIds)) {
                    try {
                        // save는 신청 건당 REQUIRES_NEW 트랜잭션이라, 한 건 실패가 다른 건 적재를 막지 않도록
                        // 건별로 예외를 삼킨다. 이미 적재된 건(재실행)은 false를 돌려주며 조용히 skip된다.
                        if (resumeExtracurricularActivityUpsertService.save(
                                ExtracurricularActivityCompletedEvent.from(application))) {
                            saved++;
                        } else {
                            skipped++;
                        }
                    } catch (Exception e) {
                        failed++;
                        log.warn("이력서 비교과 이력 백필 저장 실패 — applicationId={}, 건너뜀",
                                application.getApplicationId(), e);
                    }
                }
            }
            page++;
        } while (slice.hasNext());

        log.info("이력서 비교과 이력 백필 완료 — 저장 {}건, 스킵(이미 반영됨) {}건, 실패 {}건", saved, skipped, failed);
    }
}
