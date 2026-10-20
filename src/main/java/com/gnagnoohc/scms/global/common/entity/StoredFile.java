package com.gnagnoohc.scms.global.common.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(name = "stored_file")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stored_file_id", nullable = false) private Integer storedFileId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "file_group_id", nullable = false)
    private FileGroup fileGroup;
    @Column(name = "original_file_name", nullable = false, length = 255) private String originalFileName;
    @Column(name = "storage_key", nullable = false, unique = true, length = 1000) private String storageKey;
    @Column(name = "content_type", length = 100) private String contentType;
    @Column(name = "file_size", nullable = false) private Long fileSize;
    @Column(name = "uploaded_at", nullable = false) private Instant uploadedAt = Instant.now();
    @Column(name = "created_by", nullable = false) private Integer createdBy;

    /** 신규 저장 시에만 사용하는 전용 빌더 생성자 (PK/업로드시각 기본값은 빌더 제외). */
    @Builder
    public StoredFile(FileGroup fileGroup, String originalFileName, String storageKey,
                       String contentType, Long fileSize, Integer createdBy) {
        this.fileGroup = fileGroup;
        this.originalFileName = originalFileName;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.createdBy = createdBy;
    }
}
