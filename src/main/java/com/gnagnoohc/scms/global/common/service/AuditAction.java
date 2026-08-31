package com.gnagnoohc.scms.global.common.service;

/** 다른 도메인에서 공통으로 쓰는 감사 행위 코드. */
public enum AuditAction {
    LOGIN,      //로그인
    LOCK,       //잠금
    DORMANT,    //휴면
    READ,       //조회
    CREATE,     //생성
    UPDATE,     //수정
    DELETE,     //삭제
    DOWNLOAD,   //다운로드
    STATUS_CHANGE,  //상태 변경(게시글 숨김->발행 등)
    APPROVE,    //승인
    REJECT,     //반려
    CANCEL      //취소
}
