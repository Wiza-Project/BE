package com.gnagnoohc.scms.domain.board;

/** 공지/FAQ 게시판이 공유하는 상수. Repository/Service 양쪽에서 참조한다. */
public final class BoardConstants {

    private BoardConstants() {
    }

    /** FAQ 카테고리로 쓰는 common_code의 code_group 값. 카테고리 이름/정렬/활성 여부는 이 그룹의 행에서 조회한다. */
    public static final String FAQ_CATEGORY_CODE_GROUP = "FAQ_CATEGORY";

    /** module_code 기본값. 전체 공지가 이 값을 쓰고, FAQ 글은 module_code에 실질적 의미가 없어 그대로 고정한다. */
    public static final String DEFAULT_MODULE_CODE = "GLOBAL";
}
