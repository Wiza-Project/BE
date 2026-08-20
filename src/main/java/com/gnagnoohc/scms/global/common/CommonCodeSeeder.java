package com.gnagnoohc.scms.global.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * TODO: 개발 편의를 위해 초기 시드데이터 세팅만 이 방식으로 합니다. 이후 삭제예정
 * TODO: sql파일 따로 생성완료
 * 공통코드 초기 시드 데이터.
 *
 * ── 부서(DEPARTMENT) 코드 ────────────────────────────────────────
 * global.common.enums.Department 라는 고정 Java enum이 문서(package-info.java)에
 * 언급돼 있었지만 실제로는 구현된 적이 없고, UserSummaryResponse 주석에 "department는
 * 스윔레인 표에 실제 등장하는 4개 부서로 확정: 학생역량센터 / 비교과운영부서 /
 * 진로심리상담센터 / 취창업지원과.
 */
@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class CommonCodeSeeder implements CommandLineRunner {

    /** 시드 데이터의 created_by 값. 실사용자가 아니라 시스템 시드임을 나타내는 관례상의 값. */
    private static final int SYSTEM_CREATED_BY = 0;

    private final JdbcTemplate jdbcTemplate;

    private record Seed(String group, String code, String name, int sortOrder) {
    }

    private static final List<Seed> SEEDS = List.of(
            // 프로그램 유형 — ExtracurricularProgram.programTypeCodeId 가 참조
            new Seed("PROGRAM_TYPE", "STUDY", "학습", 1),
            new Seed("PROGRAM_TYPE", "CONTEST", "공모전", 2),
            new Seed("PROGRAM_TYPE", "CAREER_STARTUP", "진로창업", 3),
            new Seed("PROGRAM_TYPE", "PSYCH_COUNSEL", "심리상담", 4),
            new Seed("PROGRAM_TYPE", "VOLUNTEER", "사회봉사", 5),
            new Seed("PROGRAM_TYPE", "GLOBAL", "국제화", 6),

            // 학년도 — academicYear는 각 엔티티에 스칼라(Integer)로 저장되지만(FK 아님),
            // 프론트 드롭다운이 쓸 선택 가능 연도 목록의 기준점으로 최소 범위만 제공
            new Seed("ACADEMIC_YEAR", "2024", "2024학년도", 1),
            new Seed("ACADEMIC_YEAR", "2025", "2025학년도", 2),
            new Seed("ACADEMIC_YEAR", "2026", "2026학년도", 3),
            new Seed("ACADEMIC_YEAR", "2027", "2027학년도", 4),

            // 학기 — semesterCode도 스칼라(String) 저장. 기본값 "ALL"(전체)은 정책류
            // 엔티티(MileagePolicy 등)에서만 쓰이는 특수값이라 이 목록엔 포함하지 않음
            new Seed("SEMESTER", "SPRING", "1학기", 1),
            new Seed("SEMESTER", "SUMMER", "여름학기", 2),
            new Seed("SEMESTER", "FALL", "2학기", 3),
            new Seed("SEMESTER", "WINTER", "겨울학기", 4),

            // 부서 — AppUser.departmentCode, SecurityConfig 2단계 부서 판정의 기준값
            new Seed("DEPARTMENT", "STUDENT_COMPETENCY_CENTER", "학생역량센터", 1),
            new Seed("DEPARTMENT", "EXTRACURRICULAR_OPS", "비교과운영부서", 2),
            new Seed("DEPARTMENT", "CAREER_PSYCH_COUNSELING_CENTER", "진로심리상담센터", 3),
            new Seed("DEPARTMENT", "CAREER_EMPLOYMENT_SUPPORT", "취창업지원과", 4)
    );

    @Override
    public void run(String... args) {
        Instant now = Instant.now();
        int inserted = 0;
        for (Seed seed : SEEDS) {
            inserted += jdbcTemplate.update("""
                    INSERT INTO common_code
                        (code_group, code, code_name, sort_order, is_active, created_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, true, ?, ?, ?)
                    ON CONFLICT (code_group, code) DO NOTHING
                    """,
                    seed.group(), seed.code(), seed.name(), seed.sortOrder(),
                    SYSTEM_CREATED_BY, Timestamp.from(now), Timestamp.from(now));
        }
        if (inserted > 0) {
            log.info("공통코드 시드 {}건 삽입", inserted);
        }
    }
}
