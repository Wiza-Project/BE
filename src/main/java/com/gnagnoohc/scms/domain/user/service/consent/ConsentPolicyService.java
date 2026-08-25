package com.gnagnoohc.scms.domain.user.service.consent;

import com.gnagnoohc.scms.domain.user.dto.consent.ConsentPolicyResponse;
import com.gnagnoohc.scms.domain.user.repository.ConsentPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 동의 정책(약관) 조회 전용 서비스.
 * 정책 변경은 DB 시드로 관리하며, 본 서비스는 읽기 전용 로직만 제공합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsentPolicyService {

    private final ConsentPolicyRepository consentPolicyRepository;

    /** 특정 모듈의 현재 시점 유효한 동의 정책 목록을 조회합니다. */
    public List<ConsentPolicyResponse> getEffectivePolicies(String moduleCode) {
        ConsentModuleCode module = ConsentModuleCode.from(moduleCode);
        return consentPolicyRepository.findEffectivePolicies(module.name(), Instant.now())
                .stream()
                .map(ConsentPolicyResponse::from)
                .toList();
    }
}