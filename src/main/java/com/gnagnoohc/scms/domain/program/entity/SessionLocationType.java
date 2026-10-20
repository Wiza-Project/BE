package com.gnagnoohc.scms.domain.program.entity;

// 회차 등록/수정 시 장소 입력 방식. DIRECT_INPUT이면 location을 그대로 쓰고,
// SAME_AS_PREVIOUS이면 서버가 직전 회차(sessionNo - 1)의 location을 복사해서 쓴다.
public enum SessionLocationType {
    DIRECT_INPUT,
    SAME_AS_PREVIOUS
}
