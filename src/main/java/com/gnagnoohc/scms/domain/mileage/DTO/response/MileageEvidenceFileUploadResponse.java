package com.gnagnoohc.scms.domain.mileage.DTO.response;

/** 외부활동 증빙 파일을 업로드한 뒤 신청 요청에 연결할 파일 그룹 정보다. */
public record MileageEvidenceFileUploadResponse(
        Integer fileGroupId,
        String fileName
) {
}
