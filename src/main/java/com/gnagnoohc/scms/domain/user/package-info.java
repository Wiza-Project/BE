/**
 * <h2>사용자 / 인증</h2>
 *
 * 프로세스 모델의 모든 스윔레인(액터)을 계정으로 표현합니다.
 * 자세한 유형 구분은 {@link com.gnagnoohc.scms.global.common.enums.UserType},
 * 부서 구분은 {@link com.gnagnoohc.scms.global.common.enums.Department} 참고.
 *
 * <h3>구현 체크리스트</h3>
 * <ul>
 *   <li>[x] 로그인 / 토큰 재발급 / 로그아웃 API — {@link com.gnagnoohc.scms.domain.user.controller.AuthController},
 *           {@link com.gnagnoohc.scms.domain.user.service.AuthService}.
 *           30분 미활동 자동 로그아웃은 Access 토큰 만료(app.jwt.access-token-validity-seconds)로,
 *           6개월 이상 미접속 후 로그인 시도 시 휴면 잠금은 AuthService.rejectIfNotLoginable() 로 구현했습니다.
 *           AppUser 엔티티는 손대지 않고, 상태 변경은 AppUserRepository 의 벌크 업데이트로 처리합니다
 *           (엔티티에 세터/비즈니스 메서드가 없기 때문 — DormantAccountLocker 주석 참고).</li>
 *   <li>[ ] <b>계정 생성 경로를 유형별로 결정하세요.</b>
 *           학생·교직원은 학사 시스템 데이터를 받아 일괄 등록하는 경우가 많고,
 *           기업체는 자체 회원가입이 필요합니다. 이 결정이 API 설계를 크게 바꿉니다.
 *           (현재 로그인 기능은 계정이 이미 존재한다고 가정합니다. 계정 생성 API는 아직 없습니다.)</li>
 *   <li>[ ] 비밀번호 정책 / 초기 비밀번호 변경 강제</li>
 *   <li>[x] Refresh Token 저장 위치 결정 → <b>stateless JWT, httpOnly 쿠키</b>로 결정.
 *           별도 저장소(DB/Redis) 없음 — 특정 토큰만 골라 폐기(강제 로그아웃 등)는 불가능합니다.
 *           그런 요구가 생기면 저장소 도입을 재검토하세요.</li>
 *   <li>[ ] 로그인 실패 횟수 기반 계정 잠금(failed_login_count, locked_at)은 이번 범위에 포함하지 않았습니다.
 *           컬럼 자체는 AppUser에 이미 있으니 착수 시 AppUserRepository에 벌크 업데이트만 추가하면 됩니다.</li>
 * </ul>
 */
package com.gnagnoohc.scms.domain.user;
