package com.gnagnoohc.scms.global.common.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(name = "board_post")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardPost extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "board_post_id", nullable = false) private Integer boardPostId;
    // Q&A(답변글) 확장을 위해 만들어둔 초안 필드. 공지/FAQ API에서는 쓰지 않는다.
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_post_id") private BoardPost parentPost;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "file_group_id") private FileGroup fileGroup;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_user_id", nullable = false) private AppUser authorUser;
    // 공지/FAQ 구분(NOTICE/FAQ) - BoardType 참고.
    @Column(name = "board_type", nullable = false, length = 20) private String boardType;
    // 공지의 범위 구분(GLOBAL 등). FAQ 글에는 의미가 없어 상수값으로 고정한다.
    @Column(name = "module_code", nullable = false, length = 30) private String moduleCode;
    // FAQ 카테고리. common_code(code_group='FAQ_CATEGORY')의 code 값을 그대로 담는다(FK 없음).
    @Column(name = "category_code", length = 40) private String categoryCode;
    @Column(name = "title", nullable = false, length = 300) private String title;
    @Column(name = "content", nullable = false, columnDefinition = "text") private String content;
    // 비밀글(Q&A) 확장을 위해 만들어둔 초안 필드. 이번 스코프에서는 항상 false로 고정한다.
    @Column(name = "is_private", nullable = false) private boolean privatePost = false;
    // 상단 고정. NOTICE에서만 true를 허용한다(서비스 계층에서 검증).
    @Column(name = "is_pinned", nullable = false) private boolean pinned = false;
    @Column(name = "post_status", nullable = false, length = 20) private String postStatus = "PUBLISHED";
    @Column(name = "deleted_at") private Instant deletedAt;

    private BoardPost(BoardType boardType, String moduleCode, String categoryCode, AppUser authorUser,
                       String title, String content, FileGroup fileGroup, boolean pinned, PostStatus postStatus) {
        this.boardType = boardType.name();
        this.moduleCode = moduleCode;
        this.categoryCode = categoryCode;
        this.authorUser = authorUser;
        this.title = title;
        this.content = content;
        this.fileGroup = fileGroup;
        this.pinned = pinned;
        this.postStatus = postStatus.name();
    }

    public static BoardPost create(BoardType boardType, String moduleCode, String categoryCode, AppUser authorUser,
                                    String title, String content, FileGroup fileGroup, boolean pinned,
                                    PostStatus postStatus) {
        return new BoardPost(boardType, moduleCode, categoryCode, authorUser, title, content, fileGroup, pinned, postStatus);
    }

    /**
     * 전달된 값만 반영한다(null은 "변경하지 않음"). categoryCode/moduleCode는 서비스가 미리
     * 계산한 최종값을 그대로 받아 무조건 반영한다 - categoryCode 해제(null)를 표현할 수 있어야 하기 때문.
     */
    public void update(String title, String content, String categoryCode, String moduleCode, Boolean pinned,
                        PostStatus postStatus) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        this.categoryCode = categoryCode;
        if (moduleCode != null) {
            this.moduleCode = moduleCode;
        }
        if (pinned != null) {
            this.pinned = pinned;
        }
        if (postStatus != null) {
            this.postStatus = postStatus.name();
        }
    }

    public void attachFileGroup(FileGroup fileGroup) {
        this.fileGroup = fileGroup;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public BoardType getBoardTypeEnum() {
        return BoardType.valueOf(boardType);
    }

    public PostStatus getPostStatusEnum() {
        return PostStatus.valueOf(postStatus);
    }
}
