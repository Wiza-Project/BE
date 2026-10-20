package com.gnagnoohc.scms.global.common.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** BoardPost.postStatus 값. */
@Getter
@RequiredArgsConstructor
public enum PostStatus {
    DRAFT("임시저장"),
    PUBLISHED("게시중"),
    HIDDEN("숨김");

    private final String label;
}
