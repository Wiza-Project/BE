package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.ConsentPolicy;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.domain.user.repository.ConsentPolicyRepository;
import com.gnagnoohc.scms.domain.user.repository.UserConsentRepository;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentModuleCode;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentType;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentVerifier;
import com.gnagnoohc.scms.domain.user.service.consent.UserConsentService;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ProgramApplicationService.apply()가 동의 검증 시 UserConsentService.withdraw()와 같은 행 잠금
 * (UserConsentRepository.findByIdForUpdate)으로 직렬화되는지 실제 PostgreSQL에서 검증한다.
 * UserConsentPostgresConcurrencyTest의 withdrawWinsLock/reservationWinsLock 두 테스트와 동일한
 * 구조를, CounselingReservationService 대신 ProgramApplicationService.apply()를 대상으로 재현한다.
 * H2는 PESSIMISTIC_WRITE 행 잠금의 실제 대기 동작을 보장하지 않으므로 CI(H2)에서는 제외하고
 * 로컬 Docker PostgreSQL(scms_postgres_dev)에서만 수동 실행해 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("PostgreSQL 전용 동시성 테스트 — 로컬 Docker PostgreSQL에서 검증 완료, CI H2 환경 제외")
class ProgramApplicationConsentConcurrencyTest {

    @Autowired
    private ProgramApplicationService programApplicationService;
    @Autowired
    private ExtracurricularProgramRepository programRepository;
    @Autowired
    private ProgramApplicationRepository applicationRepository;
    @Autowired
    private UserConsentService userConsentService;
    @Autowired
    private UserConsentRepository userConsentRepository;
    @Autowired
    private ConsentVerifier consentVerifier;
    @Autowired
    private ConsentPolicyRepository consentPolicyRepository;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private CommonCodeRepository commonCodeRepository;
    @Autowired
    private CompetencyRepository competencyRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private AppUser student;
    private ExtracurricularProgram program;
    private ConsentPolicy policy;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            student = saveStudent("동시성신청학생", "PA-" + System.nanoTime());

            String suffix = "PA-" + System.nanoTime();
            CommonCode operatingUnitCode = saveCommonCode("DEPARTMENT", "D-" + suffix);
            CommonCode programTypeCode = saveCommonCode("PROGRAM_TYPE", "PT-" + suffix);
            Competency competency = competencyRepository.save(
                    Competency.createTop("CP-" + suffix, "동시성테스트역량", "Concurrency Test Competency", null, 1, 1));
            AppUser manager = saveStudent("동시성담당자", "MGR-" + suffix);

            program = buildProgram(operatingUnitCode, programTypeCode, competency, manager);

            policy = BeanUtils.instantiateClass(ConsentPolicy.class);
            ReflectionTestUtils.setField(policy, "consentType", ConsentType.PERSONAL_INFO.name());
            ReflectionTestUtils.setField(policy, "moduleCode", ConsentModuleCode.PROGRAM.name());
            ReflectionTestUtils.setField(policy, "version", "PGTEST-" + System.nanoTime());
            ReflectionTestUtils.setField(policy, "title", "동시성 테스트 정책");
            ReflectionTestUtils.setField(policy, "content", "테스트 본문");
            ReflectionTestUtils.setField(policy, "required", true);
            ReflectionTestUtils.setField(policy, "effectiveFrom", Instant.now().minus(1, ChronoUnit.DAYS));
            ReflectionTestUtils.setField(policy, "active", true);
            ReflectionTestUtils.setField(policy, "createdBy", 1);
            policy = consentPolicyRepository.save(policy);
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            applicationRepository.deleteAll(
                    applicationRepository.findAllByStudentId(student.getUserId(), Pageable.unpaged()).getContent());
            userConsentRepository.deleteAll(
                    userConsentRepository.findByUser_UserIdOrderByConsentedAtDesc(student.getUserId()));
            consentPolicyRepository.delete(policy);
            programRepository.delete(program);
            appUserRepository.delete(student);
            return null;
        });
    }

    @Test
    @DisplayName("철회가 동의 행 잠금을 먼저 잡으면, 뒤이은 신청 접수는 REQUIRED_CONSENT_NOT_AGREED로 실패하고 신청이 남지 않는다")
    void withdrawWinsLock_applyFails() throws Exception {
        Integer consentId = agree();

        CountDownLatch withdrawHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseWithdraw = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // 철회 스레드: 동의 행을 수동으로 잠그고, 잠금을 잡았다는 신호를 보낸 뒤 해제 신호를 받을 때까지 기다린다.
        Future<?> withdrawFuture = executor.submit(() -> {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.execute(status -> {
                UserConsent locked = userConsentRepository.findByIdForUpdate(consentId).orElseThrow();
                withdrawHoldsLock.countDown();
                await(releaseWithdraw);
                locked.withdraw(Instant.now());
                return null;
            });
            return null;
        });

        withdrawHoldsLock.await(5, TimeUnit.SECONDS);

        // 신청 스레드(실제 서비스 경로): 같은 동의 행 잠금을 기다리느라 아직 끝나지 않아야 한다.
        Future<?> applyFuture = executor.submit(() ->
                programApplicationService.apply(program.getProgramId(), student.getUserId()));

        assertThatThrownBy(() -> applyFuture.get(300, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

        releaseWithdraw.countDown();
        withdrawFuture.get(5, TimeUnit.SECONDS);

        try {
            applyFuture.get(5, TimeUnit.SECONDS);
            throw new AssertionError("철회된 동의로 신청 접수가 성공해서는 안 된다");
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) e.getCause()).getErrorCode()).isEqualTo(ErrorCode.REQUIRED_CONSENT_NOT_AGREED);
        }

        executor.shutdown();
        assertThat(applicationRepository.findByProgram_ProgramIdAndStudent_UserId(
                program.getProgramId(), student.getUserId())).isEmpty();
    }

    @Test
    @DisplayName("신청 검증이 동의 행 잠금을 먼저 잡으면, 신청은 검증 당시 동의를 참조해 커밋되고 철회는 그 뒤에 완료된다")
    void applyWinsLock_thenWithdrawSucceeds() throws Exception {
        Integer consentId = agree();

        CountDownLatch applyHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseApply = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // 신청 스레드: apply()가 (0) 단계에서 하는 것과 동일하게 consentVerifier로 같은 행을 잠그고
        // 검증한 뒤, 신호를 보내고 대기하다가 신청을 저장·커밋한다(잠금 유지 구간을 테스트에서 직접 제어하기 위함).
        Future<Integer> applyFuture = executor.submit(() -> {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            return tx.execute(status -> {
                UserConsent consent = consentVerifier.requireOwnedValidConsent(
                        consentId, student.getUserId(), ConsentModuleCode.PROGRAM, ConsentType.PERSONAL_INFO, Instant.now());
                applyHoldsLock.countDown();
                await(releaseApply);
                AppUser sessionStudent = appUserRepository.findById(student.getUserId()).orElseThrow();
                ExtracurricularProgram sessionProgram =
                        programRepository.findById(program.getProgramId()).orElseThrow();
                ProgramApplication application = buildApplication(sessionProgram, sessionStudent, consent);
                applicationRepository.save(application);
                return application.getApplicationId();
            });
        });

        applyHoldsLock.await(5, TimeUnit.SECONDS);

        // 철회 스레드(실제 서비스 경로): 같은 행 잠금을 기다리느라 아직 끝나지 않아야 한다.
        Future<Void> withdrawFuture = executor.submit(() -> {
            userConsentService.withdraw(student.getUserId(), consentId);
            return null;
        });

        assertThatThrownBy(() -> withdrawFuture.get(300, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

        releaseApply.countDown();
        Integer applicationId = applyFuture.get(5, TimeUnit.SECONDS);
        withdrawFuture.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        ProgramApplication saved = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(saved.getUserConsent().getUserConsentId()).isEqualTo(consentId);

        UserConsent afterWithdraw = userConsentRepository.findById(consentId).orElseThrow();
        assertThat(afterWithdraw.getWithdrawnAt()).isNotNull();
    }

    private Integer agree() {
        return userConsentService.agree(student.getUserId(), policy.getConsentPolicyId()).userConsentId();
    }

    private ExtracurricularProgram buildProgram(
            CommonCode operatingUnitCode, CommonCode programTypeCode, Competency competency, AppUser manager) {
        ExtracurricularProgram p = BeanUtils.instantiateClass(ExtracurricularProgram.class);
        Instant now = Instant.now();
        ReflectionTestUtils.setField(p, "operatingUnitCode", operatingUnitCode);
        ReflectionTestUtils.setField(p, "programTypeCode", programTypeCode);
        ReflectionTestUtils.setField(p, "competency", competency);
        ReflectionTestUtils.setField(p, "managerUser", manager);
        ReflectionTestUtils.setField(p, "programName", "동시성 테스트 프로그램");
        ReflectionTestUtils.setField(p, "recruitmentStartsAt", now.minusSeconds(3600));
        ReflectionTestUtils.setField(p, "recruitmentEndsAt", now.plus(3, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(p, "operationStartsAt", now.plus(4, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(p, "operationEndsAt", now.plus(5, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(p, "capacity", 10);
        return programRepository.save(p);
    }

    private ProgramApplication buildApplication(
            ExtracurricularProgram program, AppUser student, UserConsent consent) {
        ProgramApplication application = BeanUtils.instantiateClass(ProgramApplication.class);
        ReflectionTestUtils.setField(application, "program", program);
        ReflectionTestUtils.setField(application, "student", student);
        ReflectionTestUtils.setField(application, "userConsent", consent);
        ReflectionTestUtils.setField(application, "applicationStatus", "APPLIED");
        return application;
    }

    private AppUser saveStudent(String userName, String universityNo) {
        AppUser user = BeanUtils.instantiateClass(AppUser.class);
        ReflectionTestUtils.setField(user, "userName", userName);
        ReflectionTestUtils.setField(user, "universityNo", universityNo);
        ReflectionTestUtils.setField(user, "userType", "STUDENT");
        ReflectionTestUtils.setField(user, "passwordHash", "test-hash");
        return appUserRepository.save(user);
    }

    private CommonCode saveCommonCode(String codeGroup, String code) {
        CommonCode commonCode = BeanUtils.instantiateClass(CommonCode.class);
        ReflectionTestUtils.setField(commonCode, "codeGroup", codeGroup);
        ReflectionTestUtils.setField(commonCode, "code", code);
        ReflectionTestUtils.setField(commonCode, "codeName", code);
        ReflectionTestUtils.setField(commonCode, "createdBy", 1);
        return commonCodeRepository.save(commonCode);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
