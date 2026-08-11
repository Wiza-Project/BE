package com.gnagnoohc.scms.global.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Spring 의 Page 를 그대로 반환하면 직렬화 형태가 버전마다 바뀌고
 * 프론트가 쓰지 않는 필드가 잔뜩 붙습니다. 이 DTO 로 감싸서 반환하세요.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
