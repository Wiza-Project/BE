package com.gnagnoohc.scms.domain.program.entity;

import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity @Getter
@Table(name = "program_session", uniqueConstraints = @UniqueConstraint(
        name = "uq_program_session_program_no", columnNames = {"program_id", "session_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProgramSession extends BaseCreatedAtEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "program_session_id", nullable = false) private Integer programSessionId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "program_id", nullable = false) private ExtracurricularProgram program;
    @Column(name = "session_no", nullable = false) private Integer sessionNo;
    @Column(name = "session_name", length = 150) private String sessionName;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Column(name = "location", length = 300) private String location;
    @Column(name = "created_by", nullable = false) private Integer createdBy;
}
