package com.gnagnoohc.scms.domain.career.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Embeddings must be produced by the same model used for {@link StudentProfile}. */
@Entity
@Getter
@Table(name = "ncs_standard")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NcsStandard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ncs_standard_id", nullable = false)
    private Integer ncsStandardId;

    @Column(name = "ncs_code", nullable = false, unique = true, length = 255)
    private String ncsCode;

    @Column(name = "category_name", nullable = true, length = 255)
    private String categoryName;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "embedding_vector", nullable = true, columnDefinition = "vector")
    private float[] embeddingVector;

    @Column(name = "capability_element", nullable = true, length = 255)
    private String capabilityElement;
}
