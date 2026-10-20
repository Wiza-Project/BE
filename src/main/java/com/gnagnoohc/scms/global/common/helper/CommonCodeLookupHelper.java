package com.gnagnoohc.scms.global.common.helper;

import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 도메인 엔티티 저장 전 공통코드(code_group + code) 참조 유효성을 1차 검증하고 바인딩을 돕는 헬퍼.
 */
@Component
@RequiredArgsConstructor
public class CommonCodeLookupHelper {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 공통코드 유효성을 검증하고, 미존재/비활성 시 INVALID_INPUT(또는 도메인 예외)을 발생시킵니다.
     */
    public void validateActiveCode(String codeGroup, String code) {
        if (code == null || code.isBlank()) {
            return;
        }

        String sql = "SELECT COUNT(*) FROM common_code WHERE code_group = ? AND code = ? AND is_active = true";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, codeGroup, code);

        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    /**
     * 공통코드의 코드명(code_name)을 단건 조회합니다.
     */
    public String getCodeName(String codeGroup, String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        String sql = "SELECT code_name FROM common_code WHERE code_group = ? AND code = ? AND is_active = true";
        return jdbcTemplate.queryForObject(sql, String.class, codeGroup, code);
    }
}