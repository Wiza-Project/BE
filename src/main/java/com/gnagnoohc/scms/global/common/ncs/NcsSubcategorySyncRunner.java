package com.gnagnoohc.scms.global.common.ncs;

import com.gnagnoohc.scms.domain.career.service.NcsStandardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * NCS 임베딩/잡매칭용 데이터 최초 적재 러너
 *
 * <p>{@link NcsCodeSyncRunner}가 공통코드({@code NCS_CODE}) 대분류만 가볍게 채우는 것과 달리,
 * 이 러너는 {@code ncs_standard} 원장에 대·중·소·세분류 전체 계층 구조와 직무 설명을
 * 연쇄적으로 조회하여 적재하는 임베딩 기반 매칭 기능 전용 러너</p>
 *
 * <p><b>주의:</b> 다수의 공공 API 호출이 연쇄적으로 발생하여 기동 시간에 영향을 줄 수 있으므로,
 * TODO: 현재는 로컬 개발 환경({@code local})에서만 동작하도록 제한 처리, 추후 개발(dev) 서버 등에서 최초 데이터 적재가 필요할 경우 프로필 설정을 검토 필수(주석으로 프로필 설정된 걸 바꿔서 처리해도 무방)</p>
 *
 * @author YUN
 */
@Slf4j
@Component
@Profile("local")
//@Profile("!test")
@RequiredArgsConstructor
public class NcsSubcategorySyncRunner implements ApplicationRunner {

    private final NcsStandardService ncsStandardService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[NCS] 로컬 세분류 직무 동기화 시작...");
        try {
            int count = ncsStandardService.syncNcsStandard();
            log.info("[NCS] 로컬 세분류 직무 동기화 완료 (적재 건수: {})", count);
        } catch (Exception e) {
            log.error("[NCS] 로컬 세분류 직무 동기화 실패", e);
        }
    }
}