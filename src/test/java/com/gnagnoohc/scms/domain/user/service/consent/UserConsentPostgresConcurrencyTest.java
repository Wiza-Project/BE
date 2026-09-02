package com.gnagnoohc.scms.domain.user.service.consent;

import com.gnagnoohc.scms.domain.counsel.dto.request.CounselingReservationRequest;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingReservationResponse;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSchedule;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingType;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingReservationRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingScheduleRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingTypeRepository;
import com.gnagnoohc.scms.domain.counsel.service.CounselingReservationService;
import com.gnagnoohc.scms.domain.user.dto.consent.UserConsentHistoryResponse;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.ConsentPolicy;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.domain.user.entity.UserRole;
import com.gnagnoohc.scms.domain.user.entity.UserRoleId;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.domain.user.repository.ConsentPolicyRepository;
import com.gnagnoohc.scms.domain.user.repository.UserConsentRepository;
import com.gnagnoohc.scms.domain.user.repository.UserRoleRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 활성 동의 유일성(부분 유니크 인덱스)과 동의 검증·철회 행 잠금(findByIdForUpdate)이 실제
 * PostgreSQL에서 의도한 대로 동작하는지 검증한다.
 * StudentJobRelationConcurrencyTest와 동일하게 @ActiveProfiles("test") 없이 기본 프로필(local)로
 * 실행해 application-local.yml의 실제 Docker PostgreSQL(scms_postgres_dev)에 붙는다.
 * H2는 부분 유니크 인덱스·PESSIMISTIC_WRITE 행 잠금의 실제 동작을 보장하지 않으므로 CI(H2)에서는
 * 제외하고 로컬에서만 수동 실행해 검증한다.
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Disabled("PostgreSQL 전용 동시성 테스트 — 로컬 Docker PostgreSQL에서 검증 완료, CI H2 환경 제외")
class UserConsentPostgresConcurrencyTest {

    @Autowired
    private UserConsentService userConsentService;
    @Autowired
    private UserConsentRepository userConsentRepository;
    @Autowired
    private ConsentVerifier consentVerifier;
    @Autowired
    private CounselingReservationService counselingReservationService;
    @Autowired
    private CounselingReservationRepository counselingReservationRepository;
    @Autowired
    private CounselingTypeRepository counselingTypeRepository;
    @Autowired
    private CounselingScheduleRepository counselingScheduleRepository;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private ConsentPolicyRepository consentPolicyRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private AppUser student;
    private AppUser counselor;
    private CounselingType counselingType;
    private CounselingSchedule schedule;
    private ConsentPolicy policy;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            student = appUserRepository.save(appUser("동시성상담학생", "STUDENT"));
            counselor = appUserRepository.save(appUser("동시성상담사", "STAFF"));
            UserRole role = BeanUtils.instantiateClass(UserRole.class);
            UserRoleId roleId = BeanUtils.instantiateClass(UserRoleId.class);
            ReflectionTestUtils.setField(roleId, "userId", counselor.getUserId());
            ReflectionTestUtils.setField(roleId, "roleCode", "ST200");
            ReflectionTestUtils.setField(role, "id", roleId);
            ReflectionTestUtils.setField(role, "user", counselor);
            ReflectionTestUtils.setField(role, "grantedAt", Instant.now());
            userRoleRepository.save(role);

            counselingType = BeanUtils.instantiateClass(CounselingType.class);
            ReflectionTestUtils.setField(counselingType, "typeCode", "CT-" + System.nanoTime());
            ReflectionTestUtils.setField(counselingType, "typeName", "동시성테스트상담");
            ReflectionTestUtils.setField(counselingType, "applicationRoute", "DIRECT");
            ReflectionTestUtils.setField(counselingType, "counselingMethod", "ONLINE");
            ReflectionTestUtils.setField(counselingType, "active", true);
            ReflectionTestUtils.setField(counselingType, "createdBy", 1);
            counselingType = counselingTypeRepository.save(counselingType);

            schedule = counselingScheduleRepository.save(CounselingSchedule.create(
                    counselingType, counselor,
                    Instant.now().plus(3, ChronoUnit.DAYS),
                    Instant.now().plus(3, ChronoUnit.DAYS).plusSeconds(1800),
                    5, null, "온라인"
            ));

            policy = BeanUtils.instantiateClass(ConsentPolicy.class);
            ReflectionTestUtils.setField(policy, "consentType", ConsentType.PERSONAL_INFO.name());
            ReflectionTestUtils.setField(policy, "moduleCode", ConsentModuleCode.COUNSELING.name());
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

    /**
     * 이 클래스는 로컬 공유 Docker Postgres(scms_postgres_dev)를 대상으로 실행되므로,
     * StudentJobRelationConcurrencyTest처럼 전체 테이블을 deleteAllInBatch()로 비우면 안 된다 —
     * H2 인메모리와 달리 이 DB에는 다른 실제 개발 데이터가 함께 들어있다. 이 테스트가 setUp()에서
     * 직접 만든 행(student/counselor/type/schedule/policy와 그 자식 행)만 ID로 지정해 지운다.
     */
    @AfterEach
    void tearDown() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            // deleteAllInBatch()는 벌크 delete라 방금 조회한 엔티티가 영속성 컨텍스트에 "관리 중"으로
            // 남는다 — 이후 flush에서 이미 지워진 그 행을 다시 들여다보다 연쇄적으로 엉뚱한 detached
            // 참조(counselingSchedule 등)를 transient로 오인하는 예외로 이어진다. 엔티티 단위 delete로
            // 대신 처리해 매 삭제가 영속성 컨텍스트에서도 즉시 정리되게 한다.
            counselingReservationRepository.deleteAll(
                    counselingReservationRepository.findAllByStudentUserId(student.getUserId(), Pageable.unpaged()));
            counselingScheduleRepository.delete(schedule);
            counselingTypeRepository.delete(counselingType);
            userConsentRepository.deleteAll(
                    userConsentRepository.findByUser_UserIdOrderByConsentedAtDesc(student.getUserId()));
            consentPolicyRepository.delete(policy);
            userRoleRepository.deleteAll(userRoleRepository.findByUser_UserId(counselor.getUserId()));
            appUserRepository.delete(student);
            appUserRepository.delete(counselor);
            return null;
        });
    }

    @Test
    @DisplayName("같은 사용자·정책에 동시에 agree()를 2번 호출해도 활성(미철회) 동의는 정확히 1건만 남는다")
    void concurrentAgree_sameUserSamePolicy_onlyOneActiveConsentSurvives() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        Runnable task = () -> {
            ready.countDown();
            try {
                start.await();
                userConsentService.agree(student.getUserId(), policy.getConsentPolicyId());
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.CONSENT_SAVE_CONFLICT) {
                    conflictCount.incrementAndGet();
                } else {
                    throw e;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Future<?> f1 = executor.submit(task);
        Future<?> f2 = executor.submit(task);
        ready.await();
        start.countDown();
        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        long activeCount = userConsentService.getMyHistory(student.getUserId()).stream()
                .filter(h -> h.consentPolicyId().equals(policy.getConsentPolicyId()) && h.withdrawnAt() == null)
                .count();
        assertThat(activeCount).isEqualTo(1);
        assertThat(successCount.get() + conflictCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("철회가 동의 행 잠금을 먼저 잡으면, 뒤이은 예약 생성은 실패하고 예약이 남지 않는다")
    void withdrawWinsLock_reservationCreateFails() throws Exception {
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

        // 예약 생성 스레드(실제 서비스 경로): 같은 동의 행 잠금을 기다리느라 아직 끝나지 않아야 한다.
        Future<CounselingReservationResponse> reservationFuture = executor.submit(() ->
                counselingReservationService.create(reservationRequest(consentId), student.getUserId()));

        assertThatThrownBy(() -> reservationFuture.get(300, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

        releaseWithdraw.countDown();
        withdrawFuture.get(5, TimeUnit.SECONDS);

        try {
            reservationFuture.get(5, TimeUnit.SECONDS);
            throw new AssertionError("철회된 동의로 예약 생성이 성공해서는 안 된다");
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) e.getCause()).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        }

        executor.shutdown();
        assertThat(counselingReservationRepository.count()).isZero();
    }

    @Test
    @DisplayName("예약 생성이 동의 행 잠금을 먼저 잡으면, 예약은 검증 당시 동의를 참조해 커밋되고 철회는 그 뒤에 완료된다")
    void reservationWinsLock_thenWithdrawSucceeds_reservationKeepsConsentReference() throws Exception {
        Integer consentId = agree();

        CountDownLatch reservationHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseReservation = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // 예약 생성 스레드: create()가 하는 것과 동일하게 consentVerifier로 같은 행을 잠그고 검증한 뒤,
        // 신호를 보내고 대기하다가 예약을 저장·커밋한다(잠금 유지 구간을 테스트에서 직접 제어하기 위함).
        // student/schedule/counselingType은 setUp()의 다른 세션(스레드)에서 만든 detached 참조라
        // 이 스레드의 세션에서 그대로 쓰면 Hibernate가 transient로 오인한다 — 이 세션에서 다시 조회한다.
        Future<Integer> reservationFuture = executor.submit(() -> {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            return tx.execute(status -> {
                UserConsent consent = consentVerifier.requireOwnedValidConsent(
                        consentId, student.getUserId(),
                        ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, Instant.now());
                reservationHoldsLock.countDown();
                await(releaseReservation);
                AppUser sessionStudent = appUserRepository.findById(student.getUserId()).orElseThrow();
                CounselingSchedule sessionSchedule =
                        counselingScheduleRepository.findById(schedule.getCounselingScheduleId()).orElseThrow();
                CounselingType sessionType =
                        counselingTypeRepository.findById(counselingType.getCounselingTypeId()).orElseThrow();
                CounselingReservation reservation = CounselingReservation.create(
                        sessionType, sessionSchedule, sessionStudent, consent, "동시성 테스트 신청");
                counselingReservationRepository.save(reservation);
                entityManager.flush();
                return reservation.getCounselingReservationId();
            });
        });

        reservationHoldsLock.await(5, TimeUnit.SECONDS);

        // 철회 스레드(실제 서비스 경로): 같은 행 잠금을 기다리느라 아직 끝나지 않아야 한다.
        Future<Void> withdrawFuture = executor.submit(() -> {
            userConsentService.withdraw(student.getUserId(), consentId);
            return null;
        });

        assertThatThrownBy(() -> withdrawFuture.get(300, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

        releaseReservation.countDown();
        Integer reservationId = reservationFuture.get(5, TimeUnit.SECONDS);
        withdrawFuture.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        CounselingReservation saved = counselingReservationRepository.findById(reservationId).orElseThrow();
        assertThat(saved.getUserConsent().getUserConsentId()).isEqualTo(consentId);

        UserConsentHistoryResponse history = userConsentService.getMyHistory(student.getUserId()).stream()
                .filter(h -> h.userConsentId().equals(consentId))
                .findFirst().orElseThrow();
        assertThat(history.withdrawnAt()).isNotNull();
    }

    private Integer agree() {
        return userConsentService.agree(student.getUserId(), policy.getConsentPolicyId()).userConsentId();
    }

    private CounselingReservationRequest reservationRequest(Integer consentId) {
        return new CounselingReservationRequest(
                counselingType.getCounselingTypeId(), schedule.getCounselingScheduleId(), consentId, "동시성 테스트 신청");
    }

    private AppUser appUser(String label, String userType) {
        AppUser user = BeanUtils.instantiateClass(AppUser.class);
        ReflectionTestUtils.setField(user, "universityNo", "PG" + System.nanoTime());
        ReflectionTestUtils.setField(user, "userName", label);
        ReflectionTestUtils.setField(user, "userType", userType);
        ReflectionTestUtils.setField(user, "passwordHash", "$2a$10$dummyhashedpasswordforconcurrencytest");
        ReflectionTestUtils.setField(user, "email", "pgtest" + System.nanoTime() + "@univ.ac.kr");
        ReflectionTestUtils.setField(user, "accountStatus", "ACTIVE");
        return user;
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
