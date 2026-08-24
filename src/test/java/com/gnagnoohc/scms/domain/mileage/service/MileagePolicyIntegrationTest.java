package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileagePolicyRegisterRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileagePolicyUpdateRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileagePolicyResponseDTO;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.repository.MileagePolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * insertPolicy()의 생성 ID 반환, 정책 등록/수정의 비관적 락 기반 동시성 제어, clearValidTo 동작을
 * 실제 DB(H2, MODE=PostgreSQL)를 통해 검증한다. MileagePolicyServiceTest(Mockito 단위 테스트)는
 * 리포지토리를 모킹하므로 이 세 가지를 검증할 수 없어 별도로 둔다.
 */
@SpringBootTest
class MileagePolicyIntegrationTest {

    private static final Integer STAFF_ID = 1;

    @Autowired
    private MileagePolicyService mileagePolicyService;
    @Autowired
    private MileagePolicyRepository policyRepository;
    @Autowired
    private CompetencyRepository competencyRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Integer activityTypeId;

    @BeforeEach
    void setUp() {
        Competency competency = competencyRepository.save(
                Competency.createTop("C-" + (System.nanoTime() % 1_000_000_000L), "테스트 역량", null, null, 1, STAFF_ID));
        activityTypeId = insertActivityType(competency.getCompetencyId());
    }

    @Test
    void register_returnsGeneratedIdAndPersistsRow() {
        MileagePolicyRegisterRequestDTO request = registerRequest(2026, new BigDecimal("10"), LocalDate.of(2026, 3, 1), null);

        MileagePolicyResponseDTO response = mileagePolicyService.register(request, STAFF_ID);

        assertThat(response.mileagePolicyId()).isNotNull();
        MileagePolicy saved = policyRepository.findById(response.mileagePolicyId()).orElseThrow();
        assertThat(saved.getPoints()).isEqualByComparingTo("10");
        assertThat(saved.getValidFrom()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(saved.getVersionNo()).isEqualTo(1);
    }

    @Test
    void concurrentRegister_assignsDistinctVersionNumbers() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> registerTask = () -> {
                startLatch.await();
                MileagePolicyRegisterRequestDTO request = registerRequest(
                        2027, new BigDecimal("10"), LocalDate.of(2027, 3, 1), null);
                return mileagePolicyService.register(request, STAFF_ID).versionNo();
            };

            Future<Integer> first = executor.submit(registerTask);
            Future<Integer> second = executor.submit(registerTask);
            startLatch.countDown();

            List<Integer> versionNumbers = List.of(first.get(), second.get());
            assertThat(versionNumbers).containsExactlyInAnyOrder(1, 2);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void concurrentUpdate_doesNotLoseEitherChange() throws Exception {
        MileagePolicyResponseDTO registered = mileagePolicyService.register(
                registerRequest(2028, new BigDecimal("10"), LocalDate.of(2028, 3, 1), null), STAFF_ID);
        Integer policyId = registered.mileagePolicyId();

        MileagePolicyUpdateRequestDTO pointsUpdate = new MileagePolicyUpdateRequestDTO(
                new BigDecimal("20"), null, null, null, false, null, null);
        MileagePolicyUpdateRequestDTO statusUpdate = new MileagePolicyUpdateRequestDTO(
                null, null, null, null, false, null, "INACTIVE");

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> updatePoints = () -> {
                startLatch.await();
                mileagePolicyService.update(policyId, pointsUpdate);
                return null;
            };
            Callable<Void> updateStatus = () -> {
                startLatch.await();
                mileagePolicyService.update(policyId, statusUpdate);
                return null;
            };

            Future<Void> first = executor.submit(updatePoints);
            Future<Void> second = executor.submit(updateStatus);
            startLatch.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdown();
        }

        MileagePolicy result = policyRepository.findById(policyId).orElseThrow();
        assertThat(result.getPoints()).isEqualByComparingTo("20");
        assertThat(result.getPolicyStatus()).isEqualTo("INACTIVE");
    }

    @Test
    void update_whenClearValidToTrue_clearsValidTo() {
        MileagePolicyResponseDTO registered = mileagePolicyService.register(
                registerRequest(2029, new BigDecimal("10"), LocalDate.of(2029, 3, 1), LocalDate.of(2029, 8, 31)),
                STAFF_ID);

        mileagePolicyService.update(registered.mileagePolicyId(),
                new MileagePolicyUpdateRequestDTO(null, null, null, null, true, null, null));

        MileagePolicy result = policyRepository.findById(registered.mileagePolicyId()).orElseThrow();
        assertThat(result.getValidTo()).isNull();
    }

    private MileagePolicyRegisterRequestDTO registerRequest(Integer academicYear, BigDecimal points,
                                                              LocalDate validFrom, LocalDate validTo) {
        return new MileagePolicyRegisterRequestDTO(
                activityTypeId, academicYear, null, points, null, validFrom, validTo, null);
    }

    private Integer insertActivityType(Integer competencyId) {
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO mileage_activity_type (
                    competency_id, activity_code, category_code, activity_name,
                    earning_route, is_active, created_by, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, competencyId);
            ps.setString(2, "ACT-TEST-" + System.nanoTime());
            ps.setString(3, "CATEGORY");
            ps.setString(4, "테스트 활동");
            ps.setString(5, "ONLINE");
            ps.setBoolean(6, true);
            ps.setInt(7, STAFF_ID);
            ps.setTimestamp(8, Timestamp.from(now));
            ps.setTimestamp(9, Timestamp.from(now));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }
}
