/**
 * <h2>사용자 / 인증</h2>
 *
 * 프로세스 모델의 모든 스윔레인(액터)을 계정으로 표현합니다.
 * 자세한 유형 구분은 {@link com.gnagnoohc.scms.global.common.enums.UserType},
 * 부서 구분은 {@link com.gnagnoohc.scms.global.common.enums.Department} 참고.
 *
 * <h3>구현 체크리스트</h3>
 * <ul>
 *   <li>[ ] 로그인 / 토큰 재발급 / 로그아웃 API</li>
 *   <li>[ ] <b>계정 생성 경로를 유형별로 결정하세요.</b>
 *           학생·교직원은 학사 시스템 데이터를 받아 일괄 등록하는 경우가 많고,
 *           기업체는 자체 회원가입이 필요합니다. 이 결정이 API 설계를 크게 바꿉니다.</li>
 *   <li>[ ] 비밀번호 정책 / 초기 비밀번호 변경 강제</li>
 *   <li>[ ] Refresh Token 저장 위치 결정 (DB vs Redis vs httpOnly 쿠키)</li>
 * </ul>
 */
package com.gnagnoohc.scms.domain.user;
