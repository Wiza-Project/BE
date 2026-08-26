-- 비교과프로그램 첨부파일(file_group_id) 유니크 제약 — WP-172 코드리뷰 후속조치
--
-- ProgramService.validateFileGroupForLinking()의 "이미 연결된 그룹인지" 검사는 조회 후
-- 판단(check-then-act) 방식이라, 두 요청이 동시에 같은 fileGroupId로 서로 다른 프로그램을
-- 등록/수정하면 둘 다 검사를 통과할 수 있다. 애플리케이션 계층 검사는 유지하되(비-레이스
-- 상황에서 더 빠르고 친절한 오류를 주므로), DB 유니크 제약으로 레이스 상황도 원자적으로 막는다.
--
-- file_group_id는 nullable이며, Postgres UNIQUE 제약은 NULL 여러 개를 서로 다른 값으로
-- 취급해 통과시키므로 첨부파일 미연결 프로그램에는 영향 없다.
--
-- 멱등: 제약 존재 확인 후 생성(DO 블록)이라 여러 번 실행해도 안전하다.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_extracurricular_program_file_group_id'
    ) THEN
        ALTER TABLE extracurricular_program
            ADD CONSTRAINT uq_extracurricular_program_file_group_id UNIQUE (file_group_id);
    END IF;
END $$;
