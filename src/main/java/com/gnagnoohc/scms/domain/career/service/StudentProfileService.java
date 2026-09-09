package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.entity.NcsStandard;
import com.gnagnoohc.scms.domain.career.entity.StudentProfile;
import com.gnagnoohc.scms.domain.career.repository.NcsStandardRepository;
import com.gnagnoohc.scms.domain.career.repository.StudentProfileRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentProfileService {

    private final StudentProfileRepository studentProfileRepository;
    private final NcsStandardRepository ncsStandardRepository;
    private final AppUserRepository appUserRepository;

    /**
     * 학생의 선택 직무(NCS 코드)를 기반으로 StudentProfile의 임베딩 벡터 동기화 처리
     *
     * @param userId  학생 사용자 PK
     * @param ncsCode 선택된 NCS 8자리 직무 코드 (예: "20010102")
     */

    @Transactional
    public void syncStudentEmbeddingFromNcs(Integer userId, String ncsCode) {
//        if (ncsCode == null || ncsCode.isBlank()) {
//            log.debug("[StudentProfile] NCS 코드가 없어 벡터 동기화를 생략합니다. (userId: {})", userId);
//            return;
//        }

        // 1. ncsCode가 없거나 빈 값이면 기존 프로필 벡터를 비워줌 (무효화)
        if (ncsCode == null || ncsCode.isBlank()) {
            log.debug("[StudentProfile] NCS 코드가 없어 프로필 벡터를 초기화합니다. (userId: {})", userId);

            // 프로필이 이미 존재하는 경우에만 벡터를 null로 업데이트
            studentProfileRepository.findById(userId).ifPresent(profile -> {
                profile.updateEmbeddingVector(null);
                studentProfileRepository.saveAndFlush(profile);
            });
            return;
        }

        // 1. 코드 값 정규화 ('NC100', 'NCS_01' -> 대분류 번호 '10', '01' 등 추출)
        String cleanCode = ncsCode.replace("NCS_", "").replace("NC", "").trim();
        String majorCategoryPrefix = cleanCode.length() >= 2 ? cleanCode.substring(0, 2) : cleanCode;

        // 2. ncs_standard에서 대분류 계열이 일치하고 벡터가 존재하는 표준 직무 탐색
//        float[] targetVector = ncsStandardRepository.findAll().stream()
//                .filter(ncs -> ncs.getNcsCode() != null && ncs.getNcsCode().startsWith(majorCategoryPrefix))
//                .map(NcsStandard::getEmbeddingVector)
//                .filter(vec -> vec != null && vec.length > 0)
//                .findFirst()
//                .orElse(null);

        float[] targetVector = ncsStandardRepository
                .findFirstByNcsCodeStartingWithAndEmbeddingVectorIsNotNullOrderByNcsCodeAsc(majorCategoryPrefix)
                .map(NcsStandard::getEmbeddingVector)
                .orElse(null);
        // 3. 없을 경우 전체 유효 벡터 Fallback
//        if (targetVector == null) {
//            targetVector = ncsStandardRepository.findAll().stream()
//                    .map(NcsStandard::getEmbeddingVector)
//                    .filter(vec -> vec != null && vec.length > 0)
//                    .findFirst()
//                    .orElse(null);
//        }
//
//        if (targetVector == null) {
//            log.warn("[StudentProfile] 적재 가능한 NCS 표준 벡터가 원장에 전혀 존재하지 않습니다.");
//            return;
//        }

        // 엉뚱한 직무 공고가 추천되지 않도록 전체 유효 벡터 Fallback 로직은 완전히 제거함!
        if (targetVector == null) {
            log.warn("[StudentProfile] 대분류({})에 일치하는 NCS 표준 벡터가 존재하지 않아 벡터를 비웁니다.", majorCategoryPrefix);

            // 매칭되는 벡터가 없어도 기존 벡터를 null로 초기화 갱신
            studentProfileRepository.findById(userId).ifPresent(profile -> {
                profile.updateEmbeddingVector(null);
                studentProfileRepository.saveAndFlush(profile);
            });
            return;
        }

        // 4. 학생 계정 조회
        AppUser student = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 5. student_profile 엔티티 조회 또는 새로 생성
        StudentProfile profile = studentProfileRepository.findById(userId)
                .orElseGet(() -> StudentProfile.builder()
                        .user(student)
                        .studentGrade("3")
                        .build());

        // 6. 변경된 직무 계열의 벡터로 갱신 및 즉시 플러시
        profile.updateEmbeddingVector(targetVector);
        studentProfileRepository.saveAndFlush(profile);

        log.info("[StudentProfile] 학생(userId: {}) 임베딩 벡터 동기화 완료 (직무 대분류: {}, 반영 코드: {})",
                userId, majorCategoryPrefix, ncsCode);
    }

}