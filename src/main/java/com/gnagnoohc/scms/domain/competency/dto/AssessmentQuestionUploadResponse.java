package com.gnagnoohc.scms.domain.competency.dto;

import java.util.List;

public record AssessmentQuestionUploadResponse(
        int totalRows,
        int successCount,
        int failureCount,
        List<RowFailure> failures,
        List<String> warnings
) {
    public record RowFailure(int excelRowNo, String reason) {}
}
