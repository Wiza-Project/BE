package com.gnagnoohc.scms.domain.competency.support;

import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.competency.service.AssessmentResultReadyEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

/**
 * 이력서 핵심역량 연동 기능 도입 이전의 완료 진단 결과를 취창업 읽기 모델에 채우기 위한 1회성 백필 러너.
 *
 * <p>{@code submittedAt IS NOT NULL}인 기존 attempt 전체를 id 슬라이스로 순회하며
 * {@link AssessmentResultReadyEventPublisher#publish}를 {@code requestId = null}로 호출한다.
 * 취창업이 {@code studentId + attemptId}로 멱등 적재하므로 여러 번 실행해도 안전하다.</p>
 *
 * <p>매 기동마다 전량 재발행하지 않도록 전용 프로필({@code competency-resume-backfill})로 가드한다 —
 * 운영자가 이 프로필을 켜고 한 번 기동할 때만 동작한다. Outbox·정기 재발행 배치는 만들지 않는다.</p>
 */
@Slf4j
@Component
@Profile("competency-resume-backfill")
@RequiredArgsConstructor
public class AssessmentResultBackfillRunner implements CommandLineRunner {

    private static final int PAGE_SIZE = 200;

    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final AssessmentResultReadyEventPublisher assessmentResultReadyEventPublisher;

    @Override
    public void run(String... args) {
        int page = 0;
        long published = 0;
        long failed = 0;
        Slice<Integer> slice;
        do {
            slice = assessmentAttemptRepository.findSubmittedAttemptIds(PageRequest.of(page, PAGE_SIZE));
            for (Integer attemptId : slice.getContent()) {
                try {
                    // publish는 @Transactional이라 attemptId마다 독립 트랜잭션에서 발행된다 —
                    // 한 건 실패가 다른 건 발행을 막지 않도록 건별로 예외를 삼킨다.
                    assessmentResultReadyEventPublisher.publish(attemptId, null);
                    published++;
                } catch (Exception e) {
                    failed++;
                    log.warn("이력서 연동 백필 발행 실패 — attemptId={}, 건너뜀", attemptId, e);
                }
            }
            page++;
        } while (slice.hasNext());

        log.info("이력서 핵심역량 연동 백필 완료 — 발행 {}건, 실패 {}건", published, failed);
    }
}
