-- 외부활동 마일리지 신청의 증빙 파일 그룹 중복 연결 방지
-- 비교과 프로그램의 운영계획서 FileGroup 재사용 방지와 같은 목적이다.

CREATE UNIQUE INDEX IF NOT EXISTS uq_external_activity_claim_file_group_id
    ON external_activity_claim (file_group_id)
    WHERE file_group_id IS NOT NULL;
