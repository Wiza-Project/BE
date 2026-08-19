package com.gnagnoohc.scms.domain.counsel.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity @Getter @Table(name = "counseling_reservation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CounselingReservation extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "counseling_reservation_id", nullable = false) private Integer counselingReservationId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "counseling_type_id", nullable = false) private CounselingType counselingType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "counseling_schedule_id") private CounselingSchedule counselingSchedule;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id", nullable = false) private AppUser student;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_consent_id") private UserConsent userConsent;
    @Column(name = "request_content", nullable = false, columnDefinition = "text") private String requestContent;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "intake_data", columnDefinition = "jsonb") private JsonNode intakeData;
    @Column(name = "reservation_status", nullable = false, length = 20) private String reservationStatus = "REQUESTED";
    @Column(name = "processed_by") private Integer processedBy;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "decision_reason", columnDefinition = "text") private String decisionReason;
    @Column(name = "canceled_at") private Instant canceledAt;
    @Column(name = "cancellation_reason", columnDefinition = "text") private String cancellationReason;
    @Column(name = "change_reason", columnDefinition = "text") private String changeReason;
}
