package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.relation.JobRelationRequestDTO;
import com.gnagnoohc.scms.domain.career.entity.CompanyAccount;
import com.gnagnoohc.scms.domain.career.entity.JobPosting;
import com.gnagnoohc.scms.domain.career.entity.StudentJobRelation;
import com.gnagnoohc.scms.domain.career.repository.JobPostingRepository;
import com.gnagnoohc.scms.domain.career.repository.StudentJobRelationRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Disabled("PostgreSQL 비관적 락(FOR NO KEY UPDATE) 전용 동시성 테스트 (CI H2 환경 제외, 로컬 Docker PostgreSQL에서 검증 완료)")
class StudentJobRelationConcurrencyTest {

    @Autowired
    private StudentJobRelationService studentJobRelationService;

    @Autowired
    private StudentJobRelationRepository relationRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private AppUser testStudent;
    private JobPosting testPosting;

    @BeforeEach
    void setUp() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            AppUser student = org.springframework.beans.BeanUtils.instantiateClass(AppUser.class);
            ReflectionTestUtils.setField(student, "universityNo", "20269999");
            ReflectionTestUtils.setField(student, "userName", "동시성테스트학생");
            ReflectionTestUtils.setField(student, "userType", "STUDENT");
            ReflectionTestUtils.setField(student, "passwordHash", "$2a$10$dummyhashedpasswordforconcurrencytest");
            ReflectionTestUtils.setField(student, "email", "teststudent@univ.ac.kr");
            ReflectionTestUtils.setField(student, "accountStatus", "ACTIVE");
            testStudent = appUserRepository.save(student);

            CompanyAccount dummyCompany = org.springframework.beans.BeanUtils.instantiateClass(CompanyAccount.class);
            ReflectionTestUtils.setField(dummyCompany, "companyName", "(주)테스트검증기업");
            ReflectionTestUtils.setField(dummyCompany, "businessRegistrationNo", "123-45-67890");
            ReflectionTestUtils.setField(dummyCompany, "loginId", "corp_test_01");
            ReflectionTestUtils.setField(dummyCompany, "passwordHash", "$2a$10$dummyhashedpasswordforconcurrencytest");
            ReflectionTestUtils.setField(dummyCompany, "contactName", "인사담당자");
            ReflectionTestUtils.setField(dummyCompany, "contactEmail", "hr@corp.com");
            ReflectionTestUtils.setField(dummyCompany, "verificationStatus", "VERIFIED");
            ReflectionTestUtils.setField(dummyCompany, "accountStatus", "ACTIVE");

            entityManager.persist(dummyCompany);
            entityManager.flush();

            JobPosting posting = JobPosting.builder()
                    .postingTitle("동시성 테스트 공고")
                    .jobDescription("동시성 테스트용 직무 상세 설명입니다.")
                    .employmentType("REGULAR")
                    .postingType("GENERAL")
                    .recruitmentCount(1)
                    .applicationEndsAt(Instant.now().plus(7, ChronoUnit.DAYS))
                    .build();

            ReflectionTestUtils.setField(posting, "companyAccount", dummyCompany);
            ReflectionTestUtils.setField(posting, "postingStatus", "PUBLISHED");
            testPosting = jobPostingRepository.save(posting);

            return null;
        });
    }

    @AfterEach
    void tearDown() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            relationRepository.deleteAllInBatch();
            jobPostingRepository.deleteAllInBatch();
            appUserRepository.deleteAllInBatch();
            entityManager.createQuery("DELETE FROM CompanyAccount").executeUpdate();
            return null;
        });
    }

    @Test
    @DisplayName("이미 스크랩된 상태에서 동시에 스크랩 토글과 지원을 호출해도 비관적 락으로 인해 데이터 덮어쓰기 없이 정상 반영된다")
    void concurrentToggleScrapAndApplyJob_WithExistingRelation() throws Exception {
        // given: 먼저 스크랩 관계를 생성하고 커밋 완료
        studentJobRelationService.toggleScrap(testStudent.getUserId(), testPosting.getJobPostingId());

        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        JobRelationRequestDTO applyRequest = org.springframework.beans.BeanUtils.instantiateClass(JobRelationRequestDTO.class);
        ReflectionTestUtils.setField(applyRequest, "jobPostingId", testPosting.getJobPostingId());

        // when: 스크랩 토글(해제)과 지원 동시 실행
        Future<?> future1 = executorService.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                studentJobRelationService.toggleScrap(testStudent.getUserId(), testPosting.getJobPostingId());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> future2 = executorService.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                studentJobRelationService.applyJob(testStudent.getUserId(), applyRequest);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        readyLatch.await();
        startLatch.countDown();

        future1.get(5, TimeUnit.SECONDS);
        future2.get(5, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 새 트랜잭션에서 최종 상태 검증
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            StudentJobRelation relation = relationRepository
                    .findByStudent_UserIdAndJobPosting_JobPostingId(testStudent.getUserId(), testPosting.getJobPostingId())
                    .orElseThrow();

            // 1. 지원 완료 상태 검증
            assertThat(relation.getAppliedAt()).isNotNull();
            assertThat(relation.getApplicationStatus()).isEqualTo("APPLIED");

            // 2. 스크랩 해제(null) 상태 보존 검증 (applyJob에 의해 이전 북마크 상태로 덮어씌워지지 않았는지 확인)
            assertThat(relation.getBookmarkedAt()).isNull();
            return null;
        });
    }

    @Test
    @DisplayName("동일 공고에 대해 동시에 2번 지원(applyJob)을 호출하면 비관적 락에 의해 1건만 성공하고 1건은 ALREADY_APPLIED 예외가 발생한다")
    void concurrentDuplicateApplyJob() throws Exception {
        // given
        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyAppliedCount = new AtomicInteger(0);

        JobRelationRequestDTO applyRequest = org.springframework.beans.BeanUtils.instantiateClass(JobRelationRequestDTO.class);
        ReflectionTestUtils.setField(applyRequest, "jobPostingId", testPosting.getJobPostingId());

        // when: 2개 스레드가 동시 지원
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    studentJobRelationService.applyJob(testStudent.getUserId(), applyRequest);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.JOB_POSTING_ALREADY_APPLIED) {
                        alreadyAppliedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("[동시성 테스트] 예외 발생", e);
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // then: 정확히 1건 성공, 1건 중복 방어 성공 검증
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(alreadyAppliedCount.get()).isEqualTo(1);
    }
}