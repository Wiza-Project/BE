package com.gnagnoohc.scms.global.common.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "file_group")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileGroup extends BaseCreatedAtEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_group_id", nullable = false)
    private Integer fileGroupId;

    /** 첨부파일 묶음 신규 생성. 필드가 없어 빌더 대신 팩토리 메서드로 둔다. */
    public static FileGroup create() {
        return new FileGroup();
    }
}
