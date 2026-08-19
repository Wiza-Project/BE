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
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_post_id") private BoardPost parentPost;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "file_group_id") private FileGroup fileGroup;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_user_id", nullable = false) private AppUser authorUser;
    @Column(name = "board_type", nullable = false, length = 20) private String boardType;
    @Column(name = "module_code", nullable = false, length = 30) private String moduleCode;
    @Column(name = "category_code", length = 40) private String categoryCode;
    @Column(name = "title", nullable = false, length = 300) private String title;
    @Column(name = "content", nullable = false, columnDefinition = "text") private String content;
    @Column(name = "is_private", nullable = false) private boolean privatePost = false;
    @Column(name = "is_pinned", nullable = false) private boolean pinned = false;
    @Column(name = "post_status", nullable = false, length = 20) private String postStatus = "PUBLISHED";
    @Column(name = "deleted_at") private Instant deletedAt;
}
