package com.gnagnoohc.scms.domain.board.dto.response;

import com.gnagnoohc.scms.global.common.entity.StoredFile;

//첨부파일 정보 응답DTO
public record BoardAttachmentResponse(
        Integer storedFileId,
        String originalFileName,
        String contentType,
        Long fileSize
) {
    public static BoardAttachmentResponse from(StoredFile storedFile) {
        return new BoardAttachmentResponse(
                storedFile.getStoredFileId(),
                storedFile.getOriginalFileName(),
                storedFile.getContentType(),
                storedFile.getFileSize());
    }
}
