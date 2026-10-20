package com.gnagnoohc.scms.domain.user.service.consent;

/**
 * ConsentPolicy.consentType 컬럼에 저장되는 정책 유형 화이트리스트 enum.
 */
public enum ConsentType {
    /** 이용약관 */
    TERMS_OF_SERVICE,

    /** 개인정보 수집·이용 (구 PRIVACY_COLLECTION / PERSONAL_INFO_COLLECTION 통합) */
    PERSONAL_INFO,

    /** 개인정보 제3자 제공 */
    THIRD_PARTY_SHARE,

    /** 민감정보 처리 동의 (진단 응답, 상담 기록 등) */
    SENSITIVE_INFO,

    /** AI 및 자동화 추천 연산(프로파일링) 활용 동의 (선택 약관 분리용) */
    PROFILING
}