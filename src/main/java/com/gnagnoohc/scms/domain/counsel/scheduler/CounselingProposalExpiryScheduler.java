package com.gnagnoohc.scms.domain.counsel.scheduler;

import com.gnagnoohc.scms.domain.counsel.repository.CounselingProposalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 응답 기한이 지난 저장 상태 PENDING 제안을 EXPIRED로 정리한다.
 * API가 보여주는 유효 상태는 CounselingProposal.effectiveResponseStatus()가 즉시 계산하므로,
 * 이 스케줄러는 그 계산과 별개로 저장 상태 자체를 뒤늦게 맞추는 정리 작업일 뿐이다. 같은 now로
 * 조건부 UPDATE 한 번만 실행하므로 여러 서버가 동시에 돌려도 같은 결과를 내는 멱등 연산이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CounselingProposalExpiryScheduler {

    private final CounselingProposalRepository counselingProposalRepository;

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void expirePastDeadlineProposals() {
        Instant now = Instant.now();
        int expired = counselingProposalRepository.expirePastDeadline(now);
        if (expired > 0) {
            log.info("응답 기한이 지난 상담 제안을 EXPIRED로 정리했습니다. 건수={}", expired);
        }
    }
}
