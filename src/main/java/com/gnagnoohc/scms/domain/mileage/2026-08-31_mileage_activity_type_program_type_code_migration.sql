-- mileage_activity_type.program_type_code_id 운영 마이그레이션
--
-- MileageActivityType.programTypeCode 매핑에 필요한 운영 스키마 변경이다.
-- 운영 환경은 spring.jpa.hibernate.ddl-auto=validate이므로 애플리케이션이 시작되기
-- 전에 이 SQL을 실행해야 한다.
--
-- program_type_code_id는 비교과 프로그램 유형(PROGRAM_TYPE)을 참조하며,
-- 기존 핵심역량 기준 활동 유형은 이 값이 NULL일 수 있으므로 nullable로 추가한다.
-- 여러 번 실행해도 이미 존재하는 컬럼·외래키를 다시 만들지 않는다.

ALTER TABLE mileage_activity_type
    ADD COLUMN IF NOT EXISTS program_type_code_id INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'mileage_activity_type'::regclass
          AND conname = 'fk_mileage_activity_type_program_type_code'
    ) THEN
        ALTER TABLE mileage_activity_type
            ADD CONSTRAINT fk_mileage_activity_type_program_type_code
                FOREIGN KEY (program_type_code_id) REFERENCES common_code(code_id);
    END IF;
END
$$;
