-- 동의 공통 모듈 — consent_policy 초기 시드 (비교과/PROGRAM) — WP-197
--
-- 근거: 2026-08-24 비교과 도메인 담당자 회신
-- (Notion 설계 문서, 별도 공유 — 이 저장소에는 포함되어 있지 않음).
--
-- 로컬/개발 환경에서 수동 실행하세요. (2026-08-20_common_code_seed.sql,
-- 2026-08-24_extracurricular_program_dummy_seed.sql과 동일한 방식 — 별도 시더 클래스 없음)
--
-- 스키마 변경 없음. consent_policy는 기존 컬럼 그대로 사용한다. 지원 건별 별도 동의
-- (grant_type 구분)는 도입하지 않기로 확정했다 — 최초 1회 동의하면 그 뒤로는 철회 전까지
-- 계속 유효한 것으로 취급한다(2026-08-24 재확정).
--
-- 멱등: uq_consent_policy_type_module_version (consent_type, module_code, version) 유니크
-- 제약에 기대어 ON CONFLICT DO NOTHING을 쓰므로 여러 번 실행해도 안전합니다.
--
-- 주의: created_by는 시딩을 수행한 관리자 계정의 user_id다. 아래 1은 placeholder이므로
-- 실행 전 실제 관리자 user_id로 교체할 것.
--
-- !! 주의 — content 본문 미확정 항목 !!
--   비교과(PROGRAM)는 회신에서 보유기간을 "학칙·개인정보처리방침 담당 확인 필요"로
--   남겼다. 아래 본문의 보유기간은 [확인필요]로 표기되어 있다. 개인정보보호법상 보유기간은
--   동의 고지 필수 항목이라, 값이 확정되기 전까지는 로컬/개발 환경 전용이며 운영 반영 금지.

INSERT INTO consent_policy
    (consent_type, module_code, version, title, content,
     is_required, effective_from, effective_to, is_active, created_by, created_at)
VALUES
-- ── PROGRAM — 비교과 (B안: 신청 액션에서 차단) ─────────────────────────────
('TERMS_OF_SERVICE', 'PROGRAM', '2026.1', '비교과 프로그램 이용약관',
 '비교과 프로그램 신청·운영 관련 이용약관 본문...',
 true, '2026-08-24T00:00:00Z', null, true, 1, now()),

-- ⚠ 보유기간 미확정 — 운영 반영 전 개인정보처리방침 담당자 확인 필수, 로컬/개발 전용.
('PERSONAL_INFO', 'PROGRAM', '2026.1', '비교과 개인정보 수집·이용 동의',
 E'■ 수집 항목\n- 신청 이력(신청/취소 일시, 상태), 출결 기록, 이수 판정 결과 및 수료번호,\n'
 '  적립 마일리지 내역\n'
 '■ 수집 목적\n- 비교과 프로그램 운영·이수 인증·마일리지 관리\n'
 '■ 보유 기간\n- [확인필요] 학사기록 보유 규정에 따름. 학칙·개인정보처리방침 담당 확인 후 기재.',
 true, '2026-08-24T00:00:00Z', null, true, 1, now())

ON CONFLICT (consent_type, module_code, version) DO NOTHING;
