package com.gnagnoohc.scms.domain.mileage.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;

/**
 * MileagePolicy 엔티티는 protected 기본 생성자만 있고 빌더/setter가 없어서 save()로 저장할 수 없다.
 * 예전에는 Hibernate native @Query의 INSERT ... RETURNING으로 우회했지만, 그 조합이 생성 ID를
 * 항상 신뢰성 있게 반환하는지 코드만으로 보장할 수 없다는 지적에 따라, JDBC 표준 생성키 조회
 * 메커니즘(Statement.RETURN_GENERATED_KEYS)을 직접 쓰는 JdbcTemplate 방식으로 전환한다
 * (JdbcUpsertHelper와 동일하게 이 프로젝트에서 이미 쓰이는 패턴).
 */
@RequiredArgsConstructor
public class MileagePolicyRepositoryImpl implements MileagePolicyRepositoryCustom {

    private static final String INSERT_SQL = """
        INSERT INTO mileage_policy (
            activity_type_id, academic_year, semester_code, version_no,
            points, maximum_points, valid_from, valid_to, duplicate_rule,
            policy_status, created_by, created_at
        ) VALUES (
            ?, ?, ?, ?,
            ?, ?, ?, ?, CAST(? AS jsonb),
            ?, ?, ?
        )
        """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Integer insertPolicy(Integer activityTypeId, Integer academicYear, String semesterCode,
                                 Integer versionNo, BigDecimal points, BigDecimal maximumPoints,
                                 LocalDate validFrom, LocalDate validTo, String duplicateRule,
                                 String policyStatus, Integer createdBy, Instant now) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, activityTypeId);
            ps.setInt(2, academicYear);
            ps.setString(3, semesterCode);
            ps.setInt(4, versionNo);
            ps.setBigDecimal(5, points);
            if (maximumPoints != null) {
                ps.setBigDecimal(6, maximumPoints);
            } else {
                ps.setNull(6, Types.NUMERIC);
            }
            ps.setDate(7, Date.valueOf(validFrom));
            if (validTo != null) {
                ps.setDate(8, Date.valueOf(validTo));
            } else {
                ps.setNull(8, Types.DATE);
            }
            if (duplicateRule != null) {
                ps.setString(9, duplicateRule);
            } else {
                ps.setNull(9, Types.VARCHAR);
            }
            ps.setString(10, policyStatus);
            ps.setInt(11, createdBy);
            ps.setTimestamp(12, Timestamp.from(now));
            return ps;
        }, keyHolder);

        return keyHolder.getKey().intValue();
    }
}
