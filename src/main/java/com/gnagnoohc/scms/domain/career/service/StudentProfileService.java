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
     * 학생의 선택 직무(NCS 코드)를 기반으로 StudentProfile의 임베딩 벡터를 동기화합니다.
     *
     * @param userId  학생 사용자 PK
     * @param ncsCode 선택된 NCS 8자리 직무 코드 (예: "20010102")
     */
    @Transactional
    public void syncStudentEmbeddingFromNcs(Integer userId, String ncsCode) {
        if (ncsCode == null || ncsCode.isBlank()) {
            log.debug("[StudentProfile] NCS 코드가 없어 벡터 동기화를 생략합니다. (userId: {})", userId);
            return;
        }

        // 1. ncs_standard 원장에서 해당 직무의 사전 적재된 임베딩 벡터 조회
        NcsStandard ncsStandard = ncsStandardRepository.findByNcsCode(ncsCode)
                .orElse(null);

        // ncs_standard에 사전에 생성된 벡터가 없는 경우 방어로직 (배포 시 Ollama가 없으므로 새로 만들지 않고 스킵)
        if (ncsStandard == null || ncsStandard.getEmbeddingVector() == null) {
            log.warn("[StudentProfile] 해당 NCS 코드({})의 사전 적재된 임베딩 벡터가 존재하지 않습니다.", ncsCode);
            return;
        }

        float[] targetVector = ncsStandard.getEmbeddingVector();

        // 2. 학생 엔티티(AppUser) 확보
        AppUser student = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 3. student_profile 엔티티 조회 또는 AppUser를 주입하여 신규 생성
        StudentProfile profile = studentProfileRepository.findById(userId)
                .orElseGet(() -> StudentProfile.builder()
                        .user(student) // 또는 엔티티 필드명에 따라 .student(student)
                        .build());

        // 4. 벡터 갱신 및 저장
        profile.updateEmbeddingVector(targetVector);
        studentProfileRepository.save(profile);

        log.info("[StudentProfile] 학생(userId: {}) 임베딩 벡터 동기화 완료 (NCS 코드: {})", userId, ncsCode);
    }
}