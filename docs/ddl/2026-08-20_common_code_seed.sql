-- 공통코드(common_code) 초기 시드 데이터 — WP-63
--
-- 로컬/개발 환경은 CommonCodeSeeder(CommandLineRunner, local 프로필에서만 동작)
-- 배포 시 이 스크립트를 직접 실행해서 반영하세요.
--
-- 멱등: (code_group, code) 유니크 제약(uq_common_code_group_code)에 기대어
-- ON CONFLICT DO NOTHING을 쓰므로 여러 번 실행해도 안전합니다.
--
-- 주의: 이 파일은 CommonCodeSeeder.java의 SEEDS 목록과 내용이 같아야 합니다.
-- 그룹/코드를 추가·변경하면 두 곳 다 같이 고치세요(자동 동기화 아님).

-- PROGRAM_TYPE/DEPARTMENT를 설명형 코드(STUDY, STUDENT_COMPETENCY_CENTER 등)에서
-- 접두어+100단위 코드(PT100, D100 ...)로 바꾸면서, 이미 옛 이름으로 시드돼있는 DB를
-- 위해 이름만 변경한다(codeId 유지 — 이미 참조 중인 FK가 있어도 안전). 빈 DB에서는
-- 매치되는 게 없어 no-op.
UPDATE common_code SET code = 'PT100' WHERE code = 'STUDY';
UPDATE common_code SET code = 'PT200' WHERE code = 'CONTEST';
UPDATE common_code SET code = 'PT300' WHERE code = 'CAREER_STARTUP';
UPDATE common_code SET code = 'PT400' WHERE code = 'PSYCH_COUNSEL';
UPDATE common_code SET code = 'PT500' WHERE code = 'VOLUNTEER';
UPDATE common_code SET code = 'PT600' WHERE code = 'GLOBAL';
UPDATE common_code SET code = 'D100' WHERE code = 'STUDENT_COMPETENCY_CENTER';
UPDATE common_code SET code = 'D200' WHERE code = 'EXTRACURRICULAR_OPS';
UPDATE common_code SET code = 'D300' WHERE code = 'CAREER_PSYCH_COUNSELING_CENTER';
UPDATE common_code SET code = 'D400' WHERE code = 'CAREER_EMPLOYMENT_SUPPORT';

INSERT INTO common_code
    (code_group, code, code_name, sort_order, is_active, created_by, created_at, updated_at)
VALUES
    -- 프로그램 유형 — ExtracurricularProgram.programTypeCodeId 가 참조.
    -- 접두어(PT)+100단위 일련번호: 나중에 세분류를 PT150처럼 중간에 끼워넣을 여유를 둠
    ('PROGRAM_TYPE', 'PT100', '학습',     1, true, 0, now(), now()),
    ('PROGRAM_TYPE', 'PT200', '공모전',   2, true, 0, now(), now()),
    ('PROGRAM_TYPE', 'PT300', '진로창업', 3, true, 0, now(), now()),
    ('PROGRAM_TYPE', 'PT400', '심리상담', 4, true, 0, now(), now()),
    ('PROGRAM_TYPE', 'PT500', '사회봉사', 5, true, 0, now(), now()),
    ('PROGRAM_TYPE', 'PT600', '국제화',   6, true, 0, now(), now()),

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
    -- 실제 등장하는 4개로 확정. 접두어(D)+100단위 일련번호
    ('DEPARTMENT', 'D100', '학생역량센터',       1, true, 0, now(), now()),
    ('DEPARTMENT', 'D200', '비교과운영부서',     2, true, 0, now(), now()),
    ('DEPARTMENT', 'D300', '진로심리상담센터',   3, true, 0, now(), now()),
    ('DEPARTMENT', 'D400', '취창업지원과',       4, true, 0, now(), now()),

    -- 지역 — JobPosting.regionCode, JobPreference.preferredRegionCode 가 common_code로
    -- FK 참조(WP-136). 광역시/도 17개 단위로 확정(시/군/구 세분류는 범위 밖).
    -- 접두어(RG)+100단위 일련번호: 나중에 세분류가 필요하면 RG150처럼 끼워넣을 여유를 둠
    ('REGION_CODE', 'RG100',  '서울', 1,  true, 0, now(), now()),
    ('REGION_CODE', 'RG200',  '부산', 2,  true, 0, now(), now()),
    ('REGION_CODE', 'RG300',  '대구', 3,  true, 0, now(), now()),
    ('REGION_CODE', 'RG400',  '인천', 4,  true, 0, now(), now()),
    ('REGION_CODE', 'RG500',  '광주', 5,  true, 0, now(), now()),
    ('REGION_CODE', 'RG600',  '대전', 6,  true, 0, now(), now()),
    ('REGION_CODE', 'RG700',  '울산', 7,  true, 0, now(), now()),
    ('REGION_CODE', 'RG800',  '세종', 8,  true, 0, now(), now()),
    ('REGION_CODE', 'RG900',  '경기', 9,  true, 0, now(), now()),
    ('REGION_CODE', 'RG1000', '강원', 10, true, 0, now(), now()),
    ('REGION_CODE', 'RG1100', '충북', 11, true, 0, now(), now()),
    ('REGION_CODE', 'RG1200', '충남', 12, true, 0, now(), now()),
    ('REGION_CODE', 'RG1300', '전북', 13, true, 0, now(), now()),
    ('REGION_CODE', 'RG1400', '전남', 14, true, 0, now(), now()),
    ('REGION_CODE', 'RG1500', '경북', 15, true, 0, now(), now()),
    ('REGION_CODE', 'RG1600', '경남', 16, true, 0, now(), now()),
    ('REGION_CODE', 'RG1700', '제주', 17, true, 0, now(), now())
ON CONFLICT (code_group, code) DO NOTHING;
