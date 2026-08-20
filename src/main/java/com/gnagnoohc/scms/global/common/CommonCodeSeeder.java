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
import java.util.Map;

/**
 * TODO: 개발 편의를 위해 초기 시드데이터 세팅만 이 방식으로 합니다. 이후 삭제예정
 * TODO: sql파일 따로 생성완료
 * 공통코드 초기 시드 데이터.
 *
 * local 프로필에서만 동작합니다. "!prod"로 하면 테스트 실행 시(활성 프로필이 없어
 * !prod에도 해당됨) 이 러너가 H2 테스트 DB에 대고 Postgres 전용 문법
 * (ON CONFLICT)을 실행하려다 ScmsApplicationTests의 컨텍스트 로딩이 깨집니다
 * (JdbcSQLSyntaxErrorException) — 실제로 겪은 문제라 남겨둡니다.
 *
 * ── 부서(DEPARTMENT) 코드 ────────────────────────────────────────
 * global.common.enums.Department 라는 고정 Java enum이 문서(package-info.java)에
 * 언급돼 있었지만 실제로는 구현된 적이 없고, UserSummaryResponse 주석에 "department는
 * 스윔레인 표에 실제 등장하는 4개 부서로 확정: 학생역량센터 / 비교과운영부서 /
 * 진로심리상담센터 / 취창업지원과.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class CommonCodeSeeder implements CommandLineRunner {

    /** 시드 데이터의 created_by 값. 실사용자가 아니라 시스템 시드임을 나타내는 관례상의 값. */
    private static final int SYSTEM_CREATED_BY = 0;

    private final JdbcTemplate jdbcTemplate;

    private record Seed(String group, String code, String name, int sortOrder) {
    }

    /**
     * 접두어+100단위 코드(PT100, D100 ...)로 바꾸기 전에 이미 옛 이름(STUDY,
     * STUDENT_COMPETENCY_CENTER 등)으로 시드돼있는 로컬 DB를 위한 이름 변경 매핑.
     * codeId를 그대로 유지한 채(이미 참조 중인 FK가 있어도 안전) code 값만 바꾼다.
     * 새로 받는 팀원의 빈 DB에서는 매치되는 게 없어 그냥 no-op.
     */
    private static final Map<String, String> RENAMES = Map.ofEntries(
            Map.entry("STUDY", "PT100"),
            Map.entry("CONTEST", "PT200"),
            Map.entry("CAREER_STARTUP", "PT300"),
            Map.entry("PSYCH_COUNSEL", "PT400"),
            Map.entry("VOLUNTEER", "PT500"),
            Map.entry("GLOBAL", "PT600"),
            Map.entry("STUDENT_COMPETENCY_CENTER", "D100"),
            Map.entry("EXTRACURRICULAR_OPS", "D200"),
            Map.entry("CAREER_PSYCH_COUNSELING_CENTER", "D300"),
            Map.entry("CAREER_EMPLOYMENT_SUPPORT", "D400")
    );

    private static final List<Seed> SEEDS = List.of(
            // 프로그램 유형 — ExtracurricularProgram.programTypeCodeId 가 참조.
            // 접두어(PT)+100단위 일련번호: 나중에 세분류를 PT150처럼 중간에 끼워넣을 여유를 둠
            new Seed("PROGRAM_TYPE", "PT100", "학습", 1),
            new Seed("PROGRAM_TYPE", "PT200", "공모전", 2),
            new Seed("PROGRAM_TYPE", "PT300", "진로창업", 3),
            new Seed("PROGRAM_TYPE", "PT400", "심리상담", 4),
            new Seed("PROGRAM_TYPE", "PT500", "사회봉사", 5),
            new Seed("PROGRAM_TYPE", "PT600", "국제화", 6),

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

            // 부서 — AppUser.departmentCode, SecurityConfig 2단계 부서 판정의 기준값.
            // 접두어(D)+100단위 일련번호
            new Seed("DEPARTMENT", "D100", "학생역량센터", 1),
            new Seed("DEPARTMENT", "D200", "비교과운영부서", 2),
            new Seed("DEPARTMENT", "D300", "진로심리상담센터", 3),
            new Seed("DEPARTMENT", "D400", "취창업지원과", 4),

            // 사용자 유형 — AppUser.userType. common_code FK는 아니고(자유 varchar) 값만
            // 일치시켜 코드→한글명 조회 용도로만 시딩한다.
            new Seed("USER_TYPE", "ADMIN", "관리자", 1),
            new Seed("USER_TYPE", "STAFF", "교직원", 2),
            new Seed("USER_TYPE", "STUDENT", "학생", 3),

            // 사용자 역할(user_role.role_code, N:M) — 마찬가지로 common_code FK 아님, 조회 전용 시딩.
            // 접두어+100단위 일련번호: SD(학생)/ST(교직원 계열)/AD(관리자)
            new Seed("USER_ROLE", "SD100", "학생", 1),
            new Seed("USER_ROLE", "ST100", "일반교직원", 2),
            new Seed("USER_ROLE", "ST200", "카운셀러", 3),
            new Seed("USER_ROLE", "ST300", "교수", 4),
            new Seed("USER_ROLE", "AD100", "관리자", 5)
    );

    @Override
    public void run(String... args) {
        RENAMES.forEach((oldCode, newCode) ->
                jdbcTemplate.update("UPDATE common_code SET code = ? WHERE code = ?", newCode, oldCode));

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
