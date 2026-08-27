-- program_application.user_consent_id FK 컬럼 추가 — WP-197 ConsentVerifier 연동 후속조치
--
-- 근거: 커밋 dbabb12(feat(WP-197) 프로그램 신청에 공통 동의 모듈(ConsentVerifier) 연동)에서
-- ProgramApplication 엔티티에 CAREER/COUNSELING 도메인과 동일한 크로스 도메인 FK 패턴으로
-- user_consent_id(@ManyToOne UserConsent, nullable)를 추가했다. 운영은 ddl-auto: validate라
-- 이 파일을 직접 실행해야 한다(로컬은 ddl-auto: update로 자동 반영됨).
--
-- nullable: 엔티티에 nullable=false가 없으므로 컬럼도 nullable로 추가한다.
-- 멱등: 컬럼은 IF NOT EXISTS, 제약은 pg_constraint 존재 확인 후 추가(DO 블록)라 여러 번 실행해도 안전하다.

ALTER TABLE program_application
    ADD COLUMN IF NOT EXISTS user_consent_id INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_program_application_user_consent_id'
    ) THEN
        ALTER TABLE program_application
            ADD CONSTRAINT fk_program_application_user_consent_id
            FOREIGN KEY (user_consent_id) REFERENCES user_consent (user_consent_id);
    END IF;
END $$;
