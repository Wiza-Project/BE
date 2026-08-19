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
}
