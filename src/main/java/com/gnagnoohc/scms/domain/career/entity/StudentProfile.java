package com.gnagnoohc.scms.domain.career.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Embeddings must be produced by the same model used for {@link NcsStandard}. */
@Entity
@Getter
@Table(name = "student_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentProfile {

    @Id
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "student_grade", nullable = true, length = 255)
    private String studentGrade;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "embedding_vector", nullable = true, columnDefinition = "vector")
    private float[] embeddingVector;
}
