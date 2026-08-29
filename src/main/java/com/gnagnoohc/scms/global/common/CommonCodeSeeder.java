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
 *
 * ── 함께 챙겨야 하는 별도 시드 ─────────────────────────────────────
 * docs/ddl/2026-08-21_competency_seed.sql (핵심역량 competency 테이블)은 common_code가
 * 여기서 자동 실행하지 않고, 로컬/배포 시 그 SQL을 별도로 수동 실행해야 한다.
 *
 * ── ACADEMIC_YEAR는 여기서 시딩하지 않는다 ─────────────────────────
 * "현재연도 ± N"이라는 결정론적 값이고 기존 값을 절대 안 건드리는(추가만 하는) 안전한
 * 연산이라, local 전용인 이 클래스 대신 {@link AcademicYearCodeExtender}(local·prod
 * 양쪽에서 매 기동마다 실행)가 전담한다 — 사람이 리뷰해야 하는 값(PROGRAM_TYPE 등)과
 * 섞이지 않게 분리했다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class CommonCodeSeeder implements CommandLineRunner {

    /** 시드 데이터의 created_by 값. 실사용자가 아니라 시스템 시드임을 나타내는 관례상의 값. */
    private static final int SYSTEM_CREATED_BY = 0;

    private final JdbcTemplate jdbcTemplate;

    /**
     * parentGroup/parentCode는 부모 행의 {@code (code_group, code)} 복합키를 가리킨다 —
     * {@code run()}에서 실제 삽입 시점에 code_id로 조회해 채운다. {@code common_code}의
     * 실제 유니크 제약이 {@code (code_group, code)}라서(uq_common_code_group_code), code만
     * 보고 조회하면 다른 그룹이 같은 code 문자열을 쓰는 순간 다중 행이 잡혀 조회가 깨진다
     * — 그래서 조회 조건에 항상 parentGroup을 같이 건다.
     * 기존 4-arg 시드 60여 줄이 그대로 컴파일되도록 4-arg 생성자를 남겨둔다(parent 없음).
     */
    private record Seed(String group, String code, String name, int sortOrder, String parentGroup, String parentCode) {
        Seed(String group, String code, String name, int sortOrder) {
            this(group, code, name, sortOrder, null, null);
        }
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

    /**
     * 상담(counsel) 도메인 그룹명 선점 — COUNSEL_METHOD / COUNSEL_CANCEL_REASON /
     * COUNSEL_REJECT_REASON. 값은 상담 BE가 정책 확정 후 전달할 예정이라 아직 아래
     * SEEDS엔 없음 — 다른 그룹이 이 이름들을 먼저 가져다 쓰지 않도록 여기 기록만 해둔다.
     */
    private static final List<Seed> SEEDS = List.of(
            // 프로그램 유형 — ExtracurricularProgram.programTypeCodeId 가 참조.
            // 접두어(PT)+100단위 일련번호: 나중에 세분류를 PT150처럼 중간에 끼워넣을 여유를 둠
            new Seed("PROGRAM_TYPE", "PT100", "학습", 1),
            new Seed("PROGRAM_TYPE", "PT200", "공모전", 2),
            new Seed("PROGRAM_TYPE", "PT300", "진로창업", 3),
            new Seed("PROGRAM_TYPE", "PT400", "심리상담", 4),
            new Seed("PROGRAM_TYPE", "PT500", "사회봉사", 5),
            new Seed("PROGRAM_TYPE", "PT600", "국제화", 6),

            // 학년도(ACADEMIC_YEAR)는 여기서 시딩 삭제
            // AcademicYearCodeExtender가 전담한다.

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
            new Seed("USER_ROLE", "AD100", "관리자", 5),

            // 지역 — JobPosting.regionCode, JobPreference.preferredRegionCode 가 common_code로
            // FK 참조(WP-136). 광역시/도 17개 단위로 확정
            // 접두어(RG)+100단위 일련번호: 나중에 세분류가 필요하면 RG150처럼 끼워넣을 여유를 둠
            new Seed("REGION_CODE", "RG100", "서울", 1),
            new Seed("REGION_CODE", "RG200", "부산", 2),
            new Seed("REGION_CODE", "RG300", "대구", 3),
            new Seed("REGION_CODE", "RG400", "인천", 4),
            new Seed("REGION_CODE", "RG500", "광주", 5),
            new Seed("REGION_CODE", "RG600", "대전", 6),
            new Seed("REGION_CODE", "RG700", "울산", 7),
            new Seed("REGION_CODE", "RG800", "세종", 8),
            new Seed("REGION_CODE", "RG900", "경기", 9),
            new Seed("REGION_CODE", "RG1000", "강원", 10),
            new Seed("REGION_CODE", "RG1100", "충북", 11),
            new Seed("REGION_CODE", "RG1200", "충남", 12),
            new Seed("REGION_CODE", "RG1300", "전북", 13),
            new Seed("REGION_CODE", "RG1400", "전남", 14),
            new Seed("REGION_CODE", "RG1500", "경북", 15),
            new Seed("REGION_CODE", "RG1600", "경남", 16),
            new Seed("REGION_CODE", "RG1700", "제주", 17),

            // 소속학과(MAJOR) — student_academic_detail.major_code_id 가 참조. AppUser.departmentCode
            // (교내 행정조직 4개)와는 완전히 별개 개념이다
            // 접두어(MJ)+100단위. code/sort_order는 전체 85개 가나다순 목록 기준으로 부여한다
            new Seed("MAJOR", "MJ400", "경영학부", 4),
            new Seed("MAJOR", "MJ3200", "산업공학과", 32),
            new Seed("MAJOR", "MJ4400", "심리학과", 44),
            new Seed("MAJOR", "MJ5100", "영어영문학과", 51),
            new Seed("MAJOR", "MJ6400", "전기·정보공학부", 64),
            new Seed("MAJOR", "MJ8000", "컴퓨터공학부", 80),
            new Seed("MAJOR", "MJ8100", "통계학과", 81),
            new Seed("MAJOR", "MJ8500", "화학생물공학부", 85),

            // 학적변동코드(ACADEMIC_CHANGE_TYPE) — student_academic_change.change_type_code_id 가 참조.
            // 접두어(AC)+100단위. 값 6개는 설계 문서(2026-08-23_academic-record-table-design.md) 4장 확정.
            new Seed("ACADEMIC_CHANGE_TYPE", "AC100", "입학", 1),
            new Seed("ACADEMIC_CHANGE_TYPE", "AC200", "휴학", 2),
            new Seed("ACADEMIC_CHANGE_TYPE", "AC300", "복학", 3),
            new Seed("ACADEMIC_CHANGE_TYPE", "AC400", "졸업", 4),
            new Seed("ACADEMIC_CHANGE_TYPE", "AC500", "제적", 5),
            new Seed("ACADEMIC_CHANGE_TYPE", "AC600", "자퇴", 6),

            // 학적변동사유(ACADEMIC_CHANGE_REASON) — student_academic_change.change_reason_code_id 가 참조.
            // parent_code_id로 위 ACADEMIC_CHANGE_TYPE에 종속된다(예: AC200 휴학을 고르면
            // 사유가 AR200/AR300/AR400으로 좁혀짐). 접두어(AR)+100단위.
            new Seed("ACADEMIC_CHANGE_REASON", "AR100", "신입학", 1, "ACADEMIC_CHANGE_TYPE", "AC100"),
            new Seed("ACADEMIC_CHANGE_REASON", "AR200", "일반휴학", 2, "ACADEMIC_CHANGE_TYPE", "AC200"),
            new Seed("ACADEMIC_CHANGE_REASON", "AR300", "군휴학", 3, "ACADEMIC_CHANGE_TYPE", "AC200"),
            new Seed("ACADEMIC_CHANGE_REASON", "AR400", "질병휴학", 4, "ACADEMIC_CHANGE_TYPE", "AC200"),
            new Seed("ACADEMIC_CHANGE_REASON", "AR500", "일반복학", 5, "ACADEMIC_CHANGE_TYPE", "AC300"),
            new Seed("ACADEMIC_CHANGE_REASON", "AR600", "군복학", 6, "ACADEMIC_CHANGE_TYPE", "AC300"),
            new Seed("ACADEMIC_CHANGE_REASON", "AR700", "졸업", 7, "ACADEMIC_CHANGE_TYPE", "AC400"),
            new Seed("ACADEMIC_CHANGE_REASON", "AR800", "미등록제적", 8, "ACADEMIC_CHANGE_TYPE", "AC500"),
            new Seed("ACADEMIC_CHANGE_REASON", "AR900", "자퇴", 9, "ACADEMIC_CHANGE_TYPE", "AC600"),

            // FAQ 카테고리(FAQ_CATEGORY)
            // 접두어(FC)+100단위. 초안이라 실제 문의 유형에 맞춰 이름/개수 조정 필요.
            new Seed("FAQ_CATEGORY", "FC100", "계정/로그인", 1),
            new Seed("FAQ_CATEGORY", "FC200", "비교과 프로그램", 2),
            new Seed("FAQ_CATEGORY", "FC300", "핵심역량진단", 3),
            new Seed("FAQ_CATEGORY", "FC400", "마일리지/장학", 4),
            new Seed("FAQ_CATEGORY", "FC500", "상담", 5),
            new Seed("FAQ_CATEGORY", "FC600", "취업/진로", 6),
            new Seed("FAQ_CATEGORY", "FC700", "이용방법", 7),
            new Seed("FAQ_CATEGORY", "FC800", "기타", 8)
    );

    @Override
    public void run(String... args) {
        RENAMES.forEach((oldCode, newCode) ->
                jdbcTemplate.update("UPDATE common_code SET code = ? WHERE code = ?", newCode, oldCode));

        Instant now = Instant.now();
        int inserted = 0;
        for (Seed seed : SEEDS) {
            // parentCode가 있으면(ACADEMIC_CHANGE_REASON) 부모 행의 code_id를 먼저 조회한다.
            // SEEDS 목록에서 부모(ACADEMIC_CHANGE_TYPE)가 자식보다 앞서 나열돼 있어야
            // 이 시점에 이미 삽입돼 조회된다 — 순서를 바꾸지 말 것.
            Integer parentCodeId = seed.parentCode() == null ? null
                    : jdbcTemplate.queryForObject(
                            "SELECT code_id FROM common_code WHERE code_group = ? AND code = ?",
                            Integer.class, seed.parentGroup(), seed.parentCode());

            inserted += jdbcTemplate.update("""
                    INSERT INTO common_code
                        (code_group, code, code_name, sort_order, parent_code_id, is_active, created_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, true, ?, ?, ?)
                    ON CONFLICT (code_group, code) DO NOTHING
                    """,
                    seed.group(), seed.code(), seed.name(), seed.sortOrder(), parentCodeId,
                    SYSTEM_CREATED_BY, Timestamp.from(now), Timestamp.from(now));
        }
        if (inserted > 0) {
            log.info("공통코드 시드 {}건 삽입", inserted);
        }
    }
}
