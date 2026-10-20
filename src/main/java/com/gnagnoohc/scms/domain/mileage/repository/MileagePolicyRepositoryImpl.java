package com.gnagnoohc.scms.domain.mileage.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;

/**
 * MileagePolicy 엔티티는 protected 기본 생성자만 있고 빌더/setter가 없어서 save()로 저장할 수 없다.
 * 예전에는 Hibernate native @Query의 INSERT ... RETURNING으로 우회했지만, 그 조합이 생성 ID를
 * 항상 신뢰성 있게 반환하는지 코드만으로 보장할 수 없다는 지적에 따라, 생성 키 컬럼을 명시한
 * JDBC 생성키 조회 메커니즘을 사용하는 JdbcTemplate 방식으로 전환한다
 * (JdbcUpsertHelper와 동일하게 이 프로젝트에서 이미 쓰이는 패턴).
 */
@RequiredArgsConstructor
public class MileagePolicyRepositoryImpl implements MileagePolicyRepositoryCustom {

    private static final String[] GENERATED_KEY_COLUMNS = {"mileage_policy_id"};

    private static final String INSERT_SQL = """
        INSERT INTO mileage_policy (
            activity_type_id, semester_code, version_no,
            points, maximum_points, valid_from, valid_to, duplicate_rule,
            policy_status, created_by, created_at
        ) VALUES (
            ?, ?, ?,
            ?, ?, ?, ?, CAST(? AS jsonb),
            ?, ?, ?
        )
        """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Integer insertPolicy(Integer activityTypeId, String semesterCode,
                                 Integer versionNo, BigDecimal points, BigDecimal maximumPoints,
                                 LocalDate validFrom, LocalDate validTo, String duplicateRule,
                                 String policyStatus, Integer createdBy, Instant now) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_SQL, GENERATED_KEY_COLUMNS);
            ps.setInt(1, activityTypeId);
            ps.setString(2, semesterCode);
            ps.setInt(3, versionNo);
            ps.setBigDecimal(4, points);
            if (maximumPoints != null) {
                ps.setBigDecimal(5, maximumPoints);
            } else {
                ps.setNull(5, Types.NUMERIC);
            }
            ps.setDate(6, Date.valueOf(validFrom));
            if (validTo != null) {
                ps.setDate(7, Date.valueOf(validTo));
            } else {
                ps.setNull(7, Types.DATE);
            }
            if (duplicateRule != null) {
                ps.setString(8, duplicateRule);
            } else {
                ps.setNull(8, Types.VARCHAR);
            }
            ps.setString(9, policyStatus);
            ps.setInt(10, createdBy);
            ps.setTimestamp(11, Timestamp.from(now));
            return ps;
        }, keyHolder);

        return keyHolder.getKey().intValue();
    }
}
