package com.gnagnoohc.scms.domain.career.scheduler;

import com.gnagnoohc.scms.domain.career.repository.JobPostingRepository;
import com.gnagnoohc.scms.domain.career.service.StudentJobRelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 채용공고 마감 처리 배치 스케줄러
 *
 * <p><strong>[스케줄링 주기 및 벌크 상태 전이 설계 기준]</strong></p>
 * <p>게시 중({@code PUBLISHED})인 공고 중 접수 마감 기한({@code applicationEndsAt})이 경과된 건을
 * 주기적으로 탐색하여 일괄 마감({@code CLOSED}) 상태로 전이</p>
 *
 * <hr>
 * <h3>1. 실행 주기 및 트리거 정책</h3>
 * <ul>
 *   <li><b>Cron 표현식 ({@code 0 0 * * * *}):</b> 매 시 정각(0분 0초)마다 배치 프로세스 트리거 처리</li>
 *   <li><b>기준 시점 ({@code Instant.now()}):</b> UTC 기준 현재 시점보다 이전({@code applicationEndsAt < now})인 공고를 대상으로 필터링</li>
 * </ul>
 *
 * <hr>
 * <h3>2. 트랜잭션 및 DB 부하 최적화</h3>
 * <ul>
 *   <li><b>단일 벌크 UPDATE 수행:</b> 엔티티를 하나씩 조회(Dirty Checking)하여 수정하지 않고, 레포지토리의 벌크 UPDATE JPQL을 1회 호출하여 대량 데이터 처리 속도를 극대화</li>
 *   <li><b>조건부 로깅:</b> 실제 마감 처리된 건수({@code closedCount > 0})가 존재할 때만 완료 로그를 출력하여 불필요한 스케줄러 로그 공해를 최소화</li>
 * </ul>
 *
 * @author YUN
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobPostingScheduler {

    private final JobPostingRepository jobPostingRepository;
    private final StudentJobRelationService studentJobRelationService;

    /**
     * 매 시 정각(0분 0초)에 접수 기간이 지난 게시 공고를 CLOSED로 상태 전이
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void autoCloseExpiredJobPostings() {
        Instant now = Instant.now();
        int closedCount = jobPostingRepository.closeExpiredPostings(now);
        if (closedCount > 0) {
            log.info("[JobPostingScheduler] 마감기한 만료 채용공고 일괄 마감 완료. 처리 건수: {}건", closedCount);
        }
    }

    /**
     * 매일 오전 09:00 정각에 마감 D-3 관심 공고 알림 자동 발송
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void runJobDeadlineNotificationBatch() {
        log.info("[JobPostingScheduler] 마감 D-3 관심 공고 알림 배치 실행 시작");
        studentJobRelationService.sendDeadlineApproachingNotifications();
        log.info("[JobPostingScheduler] 마감 D-3 관심 공고 알림 배치 실행 완료");
    }

}