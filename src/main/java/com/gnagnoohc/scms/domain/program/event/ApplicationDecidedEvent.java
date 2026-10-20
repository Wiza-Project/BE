package com.gnagnoohc.scms.domain.program.event;

public record ApplicationDecidedEvent(
        Integer applicationId,
        Integer studentId,
        Integer programId,
        String programName,
        String decisionStatus,
        String reason
) {
}
