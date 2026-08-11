package com.gnagnoohc.scms.global.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * 시간 + 작성자까지 기록하는 엔티티의 부모.
 *
 * 승인/반려처럼 "누가 했는지"가 감사(監査) 대상인 데이터에는
 * BaseTimeEntity 대신 이 클래스를 상속하세요.
 * 학교 시스템은 이력 추적 요구가 자주 들어옵니다.
 *
 * 주의: @MappedSuperclass 를 빠뜨리면 createdBy/updatedBy 컬럼이
 *       테이블에 생성되지 않습니다. (부모가 붙어 있어도 상속되지 않음)
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {

    @CreatedBy
    @Column(updatable = false, length = 50)
    private String createdBy;

    @LastModifiedBy
    @Column(length = 50)
    private String updatedBy;
}
