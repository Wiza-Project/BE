package com.gnagnoohc.scms.global.common.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** BoardPost.board_type 값. 이번 스코프는 NOTICE/FAQ 두 종류만 다룬다. */
@Getter
@RequiredArgsConstructor
public enum BoardType {
    NOTICE("공지사항"),
    FAQ("FAQ");

    private final String label;
}
