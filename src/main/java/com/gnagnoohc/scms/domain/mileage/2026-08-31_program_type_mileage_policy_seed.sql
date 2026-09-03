-- 비교과 프로그램 유형별 마일리지 정책 구조 및 초기 데이터
--
-- 전제조건:
--   1) mileage_activity_type, mileage_policy 테이블이 이미 생성되어 있어야 한다.
--   2) common_code에 PROGRAM_TYPE의 PT100~PT600이 먼저 들어 있어야 한다.
--
-- 비교과 이수 정책은 핵심역량이 아니라 프로그램 유형(PT100~PT600)을 기준으로 한다.
-- competency_id는 비교과 프로그램 유형 정책에서는 사용하지 않으므로 NULL로 저장한다.
--
-- 아래 학년도와 적용 기간은 운영 기준에 맞게 실행 전에 변경한다.

BEGIN;

-- 기존 mileage_activity_type을 유지하면서 프로그램 유형 연결 컬럼을 추가한다.
ALTER TABLE mileage_activity_type
    ADD COLUMN IF NOT EXISTS program_type_code_id INTEGER;

ALTER TABLE mileage_activity_type
    ALTER COLUMN competency_id DROP NOT NULL;

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

-- 이전 핵심역량 기준 일괄 구성으로 남아 있는 비교과 활동/정책은 새 기준과 섞이지 않게 비활성화한다.
-- 기존 이력은 삭제하지 않고 보존한다.
UPDATE mileage_activity_type
SET is_active = false,
    updated_at = now()
WHERE category_code = 'EXTRACURRICULAR'
  AND earning_route = 'PROGRAM_COMPLETION'
  AND program_type_code_id IS NULL
  AND activity_code LIKE 'EXTRACURRICULAR_C%';

UPDATE mileage_policy
SET policy_status = 'INACTIVE'
WHERE activity_type_id IN (
    SELECT activity_type_id
    FROM mileage_activity_type
    WHERE category_code = 'EXTRACURRICULAR'
      AND earning_route = 'PROGRAM_COMPLETION'
      AND program_type_code_id IS NULL
      AND activity_code LIKE 'EXTRACURRICULAR_C%'
);

-- 프로그램 유형별 비교과 활동 유형을 생성한다.
-- activity_code는 정책 조회 시 프로그램 유형과 연결하는 안정적인 업무 코드다.
INSERT INTO mileage_activity_type (
    competency_id,
    program_type_code_id,
    activity_code,
    category_code,
    activity_name,
    earning_route,
    is_active,
    created_by,
    created_at,
    updated_at
)
SELECT
    NULL,
    code.code_id,
    'EXTRACURRICULAR_' || code.code,
    'EXTRACURRICULAR',
    '비교과 프로그램 - ' || code.code_name,
    'PROGRAM_COMPLETION',
    true,
    0,
    now(),
    now()
FROM common_code code
WHERE code.code_group = 'PROGRAM_TYPE'
  AND code.code IN ('PT100', 'PT200', 'PT300', 'PT400', 'PT500', 'PT600')
ON CONFLICT (activity_code) DO UPDATE
SET competency_id = NULL,
    program_type_code_id = EXCLUDED.program_type_code_id,
    category_code = EXCLUDED.category_code,
    activity_name = EXCLUDED.activity_name,
    earning_route = EXCLUDED.earning_route,
    is_active = EXCLUDED.is_active,
    updated_at = now();

-- 2026학년도 전 기간에 유형별 5점 정책을 등록한다.
-- 같은 조건의 정책이 있으면 다시 만들지 않고, 조건이 다르면 다음 버전으로 저장한다.
WITH policy_config AS (
    SELECT
        2026::INTEGER AS academic_year,
        'ALL'::VARCHAR(20) AS semester_code,
        DATE '2026-01-01' AS valid_from,
        DATE '2026-12-31' AS valid_to
)
INSERT INTO mileage_policy (
    activity_type_id,
    academic_year,
    semester_code,
    version_no,
    points,
    maximum_points,
    valid_from,
    valid_to,
    duplicate_rule,
    policy_status,
    created_by,
    created_at
)
SELECT
    activity.activity_type_id,
    config.academic_year,
    config.semester_code,
    COALESCE((
        SELECT MAX(existing.version_no) + 1
        FROM mileage_policy existing
        WHERE existing.activity_type_id = activity.activity_type_id
          AND existing.academic_year = config.academic_year
          AND existing.semester_code = config.semester_code
    ), 1),
    5.00,
    NULL,
    config.valid_from,
    config.valid_to,
    NULL,
    'ACTIVE',
    0,
    now()
FROM mileage_activity_type activity
JOIN common_code program_type
  ON program_type.code_id = activity.program_type_code_id
CROSS JOIN policy_config config
WHERE program_type.code_group = 'PROGRAM_TYPE'
  AND program_type.code IN ('PT100', 'PT200', 'PT300', 'PT400', 'PT500', 'PT600')
  AND activity.category_code = 'EXTRACURRICULAR'
  AND activity.earning_route = 'PROGRAM_COMPLETION'
  AND activity.is_active = true
  AND NOT EXISTS (
      SELECT 1
      FROM mileage_policy existing
      WHERE existing.activity_type_id = activity.activity_type_id
        AND existing.academic_year = config.academic_year
        AND existing.semester_code = config.semester_code
        AND existing.points = 5.00
        AND existing.valid_from = config.valid_from
        AND existing.valid_to IS NOT DISTINCT FROM config.valid_to
        AND existing.policy_status = 'ACTIVE'
  );

COMMIT;
