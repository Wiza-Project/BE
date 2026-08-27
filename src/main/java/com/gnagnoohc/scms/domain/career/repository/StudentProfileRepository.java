package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 학생 AI 프로필 및 임베딩 벡터 원장 데이터 접근 계층 레파지토리
 */
public interface StudentProfileRepository extends JpaRepository<StudentProfile, Integer> {

    /** 학생 사용자 PK(user_id)로 프로필 및 벡터 조회 */
    Optional<StudentProfile> findByUserId(Integer userId);

    /** 학생 프로필 존재 여부 확인 */
    boolean existsByUserId(Integer userId);
}