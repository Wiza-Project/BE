package com.gnagnoohc.scms.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(name = "user_role")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRole {
    @EmbeddedId private UserRoleId id;
    @MapsId("userId") @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) private AppUser user;
    @Column(name = "granted_by") private Integer grantedBy;
    @Column(name = "granted_at", nullable = false) private Instant grantedAt = Instant.now();
}
