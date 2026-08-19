package com.gnagnoohc.scms.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRoleId implements Serializable {
    @Column(name = "user_id", nullable = false) private Integer userId;
    @Column(name = "role_code", nullable = false, length = 40) private String roleCode;
}
