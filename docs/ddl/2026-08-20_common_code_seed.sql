-- 공통코드(common_code) 초기 시드 데이터 — WP-63
--
-- 로컬/개발 환경은 CommonCodeSeeder(CommandLineRunner, !prod 프로필에서만 동작)
-- 배포 시 이 스크립트를 직접 실행해서 반영하세요.
--
-- 멱등: (code_group, code) 유니크 제약(uq_common_code_group_code)에 기대어
-- ON CONFLICT DO NOTHING을 쓰므로 여러 번 실행해도 안전합니다.
--
-- 주의: 이 파일은 CommonCodeSeeder.java의 SEEDS 목록과 내용이 같아야 합니다.
-- 그룹/코드를 추가·변경하면 두 곳 다 같이 고치세요(자동 동기화 아님).

INSERT INTO common_code
    (code_group, code, code_name, sort_order, is_active, created_by, created_at, updated_at)
VALUES
    -- 프로그램 유형 — ExtracurricularProgram.programTypeCodeId 가 참조
    ('PROGRAM_TYPE', 'STUDY',          '학습',       1, true, 0, now(), now()),
    ('PROGRAM_TYPE', 'CONTEST',        '공모전',     2, true, 0, now(), now()),
    ('PROGRAM_TYPE', 'CAREER_STARTUP', '진로창업',   3, true, 0, now(), now()),
    ('PROGRAM_TYPE', 'PSYCH_COUNSEL',  '심리상담',   4, true, 0, now(), now()),
    ('PROGRAM_TYPE', 'VOLUNTEER',      '사회봉사',   5, true, 0, now(), now()),
    ('PROGRAM_TYPE', 'GLOBAL',         '국제화',     6, true, 0, now(), now()),

    -- 학년도 — 각 엔티티엔 스칼라(Integer)로 저장되지만(FK 아님), 드롭다운 선택지 기준값
    ('ACADEMIC_YEAR', '2024', '2024학년도', 1, true, 0, now(), now()),
    ('ACADEMIC_YEAR', '2025', '2025학년도', 2, true, 0, now(), now()),
    ('ACADEMIC_YEAR', '2026', '2026학년도', 3, true, 0, now(), now()),
    ('ACADEMIC_YEAR', '2027', '2027학년도', 4, true, 0, now(), now()),

    -- 학기 — semesterCode도 스칼라(String) 저장. "ALL"(전체)은 정책류 엔티티 전용 특수값이라 제외
    ('SEMESTER', 'SPRING', '1학기',     1, true, 0, now(), now()),
    ('SEMESTER', 'SUMMER', '여름학기', 2, true, 0, now(), now()),
    ('SEMESTER', 'FALL',   '2학기',     3, true, 0, now(), now()),
    ('SEMESTER', 'WINTER', '겨울학기', 4, true, 0, now(), now()),

    -- 부서 — AppUser.departmentCode, ExtracurricularProgram.operatingUnitCodeId,
    -- SecurityConfig "2단계 부서 판정"의 기준값. domain package-info.java 스윔레인에
    -- 실제 등장하는 4개로 확정.
    ('DEPARTMENT', 'STUDENT_COMPETENCY_CENTER',       '학생역량센터',       1, true, 0, now(), now()),
    ('DEPARTMENT', 'EXTRACURRICULAR_OPS',              '비교과운영부서',     2, true, 0, now(), now()),
    ('DEPARTMENT', 'CAREER_PSYCH_COUNSELING_CENTER',   '진로심리상담센터', 3, true, 0, now(), now()),
    ('DEPARTMENT', 'CAREER_EMPLOYMENT_SUPPORT',        '취창업지원과',       4, true, 0, now(), now())
ON CONFLICT (code_group, code) DO NOTHING;
