package com.gnagnoohc.scms.global.common.ncs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 국가직무능력표준(NCS) 기준정보 조회 API(공공데이터포털, 서비스 B490007/hrdkapi) 클라이언트.
 *
 * <p>지금 이 티켓(WP-138)에서 실제로 쓰는 건 대분류 조회(NCS001, {@link #fetchAllLargeCategories()})
 * 뿐이다 — {@code JobPosting.ncsCode}(common_code, 대분류 드롭다운)만 채우면 된다. 다만
 * NCS001~007은 응답 봉투 구조가 완전히 동일해서({@link NcsEnvelope} 참고), URL 조립·
 * serviceKey 이중 인코딩 회피·페이징·에러 체크를 담당하는 {@link #fetchAll} 은 처음부터
 * 오퍼레이션에 무관하게 재사용 가능한 형태로 만들어뒀다. 세분류/능력단위(NCS004~006)를
 * {@code NcsStandard}(임베딩 기반 매칭용, 이번 티켓과 무관)에 적재하는 별도 작업이 생기면,
 * item 레코드만 새로 추가해서 이 메서드를 그대로 호출하면 되고 이 클래스를 다시 만들
 * 필요는 없다.</p>
 */
@Component
public class NcsApiClient {

    // TODO: [@author YUN] LOG 확인 전용으로 추가, 이후 어노테이션 형태로 변경하거나 LOG 삭제 후 상수처리 제거해도 무방합니다.
    private static final Logger log = LoggerFactory.getLogger(NcsApiClient.class);

    /**
     * 공공데이터포털 고정 엔드포인트 — 환경(로컬/운영)에 따라 달라지지 않아 상수로 둔다.
     */
    private static final String BASE_URL = "https://apis.data.go.kr/B490007/hrdkapi";
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final String serviceKey;

    public NcsApiClient(@Value("${app.ncs.service-key:}") String serviceKey) {
        this.serviceKey = serviceKey;
        // NcsCodeSyncRunner가 앱 기동 중 동기 호출하므로, 타임아웃이 없으면 외부 API 지연이
        // 기동 자체를 무기한 멈추게 할 수 있다. 커넥션/읽기 타임아웃을 명시적으로 건다.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * NCS 대분류 전체를 조회한다(NCS001, USG_YN=Y — 최신 차수만). 총 24건 고정.
     */
    public List<NcsLclasItem> fetchAllLargeCategories() {
        return fetchAll("/NCS001", "&USG_YN=Y", new ParameterizedTypeReference<>() {
        });
    }

    /**
     * [임베딩 전용] 대분류 코드로 하위 중분류 목록 조회 (NCS002).
     *
     * @param largeCategoryCode NCS 대분류 코드 (예: "01", "02")
     * @return 해당 대분류에 속한 중분류 목록
     */
    public List<NcsSubItem> fetchMediumCategories(String largeCategoryCode) {
        return fetchAll("/NCS002", "&NCS_LCLAS_CD=" + largeCategoryCode, new ParameterizedTypeReference<>() {
        });
    }

    /**
     * [임베딩 전용] 중분류 코드로 하위 소분류 목록 조회 (NCS003).
     *
     * @param largeCategoryCode  NCS 대분류 코드
     * @param mediumCategoryCode NCS 중분류 코드
     * @return 해당 중분류에 속한 소분류 목록
     */
    public List<NcsSubItem> fetchSmallCategories(String largeCategoryCode, String mediumCategoryCode) {
        return fetchAll("/NCS003", "&NCS_LCLAS_CD=" + largeCategoryCode + "&NCS_MCLAS_CD=" + mediumCategoryCode + "&USG_YN=Y", new ParameterizedTypeReference<>() {
        });
    }

    /**
     * [임베딩 전용] 소분류 코드로 최종 세분류 직무 및 직무설명 조회 (NCS004).
     *
     * @param largeCategoryCode  NCS 대분류 코드
     * @param mediumCategoryCode NCS 중분류 코드
     * @param smallCategoryCode  NCS 소분류 코드
     * @return 최종 세분류(직무) 목록
     */
    public List<NcsSubItem> fetchSubcategoriesBySmallCategory(String largeCategoryCode, String mediumCategoryCode, String smallCategoryCode) {
        return fetchAll("/NCS004", "&NCS_LCLAS_CD=" + largeCategoryCode + "&NCS_MCLAS_CD=" + mediumCategoryCode + "&NCS_SCLAS_CD=" + smallCategoryCode + "&USG_YN=Y", new ParameterizedTypeReference<>() {
        });
    }

    /**
     * [임베딩 전용] 특정 대분류 코드(2자리)에 속한 세분류(직무) 목록 조회.
     * 공공 API 필수 파라미터인 dutyLclasCd를 포함하여 호출.
     *
     * @param largeCategoryCode NCS 대분류 코드
     * @return 해당 대분류 산하의 모든 세분류 목록
     * @deprecated 전체 트리를 순회해야 할 때는 {@link #fetchAllSubcategories()}를 권장함.
     */
    public List<NcsSubItem> fetchSubcategoriesByLargeCategory(String largeCategoryCode) {
        return fetchAll("/NCS004", "&NCS_LCLAS_CD=" + largeCategoryCode, new ParameterizedTypeReference<>() {
        });
    }

    /**
     * [임베딩 전용] NCS 대·중·소·세분류 코드와 명칭, 직무설명을 모두 수집하기 위해 전체 트리를 순회한다.
     * <p><b>주의:</b> 대분류부터 세분류까지 계층별로 수십 번의 API 요청을 연쇄적으로 발생시키므로,
     * 일반적인 요청 주기에 호출하면 안 되며 데이터 초기 적재(Batch/Runner) 시에만 제한적으로 호출해야 한다.</p>
     *
     * @return 수집된 전체 세분류(직무) 아이템 리스트
     * TODO: 최초 적재 1회 제한으로 데이터 적재 테스트 확인 완료 / 필요 시 LOG 삭제
     * @author YUN
     */
    public List<NcsSubItem> fetchAllSubcategories() {
        List<NcsLclasItem> largeCategories = fetchAllLargeCategories();
        List<NcsSubItem> allSubItems = new ArrayList<>();

        log.info("[NCS] 트리 순회 시작 - 대분류 {}건", largeCategories.size());

        for (NcsLclasItem lclas : largeCategories) {
            try {
                log.info("[NCS] 대분류 [{}]{} 순회 시작", lclas.code(), lclas.name());

                List<NcsSubItem> mediumList = fetchMediumCategories(lclas.code());
                log.info("[NCS] 대분류 [{}] 중분류 조회 완료 - {}건", lclas.code(), mediumList.size());

                for (NcsSubItem mclas : mediumList) {
                    List<NcsSubItem> smallList = fetchSmallCategories(lclas.code(), mclas.mediumCategoryCode());
                    log.debug("[NCS]   중분류 [{}] 소분류 조회 완료 - {}건", mclas.mediumCategoryCode(), smallList.size());

                    for (NcsSubItem sclas : smallList) {
                        List<NcsSubItem> subItems = fetchSubcategoriesBySmallCategory(
                                lclas.code(), mclas.mediumCategoryCode(), sclas.smallCategoryCode());
                        log.debug("[NCS]     소분류 [{}] 세분류(직무) 조회 완료 - {}건",
                                sclas.smallCategoryCode(), subItems.size());
                        allSubItems.addAll(subItems);
                    }
                }
                log.info("[NCS] 대분류 [{}]{} 순회 완료 (누적 세분류 {}건)", lclas.code(), lclas.name(), allSubItems.size());
            } catch (Exception e) {
                log.error("[NCS] 대분류({}) 하위 세분류 탐색 실패", lclas.code(), e);
            }
        }
        log.info("[NCS] 트리 순회 완료 - 총 세분류(직무) {}건 수집", allSubItems.size());
        return allSubItems;
    }

    /**
     * NCS001~NCS007 공통 페이징 전체 조회. {@code operation}만 바꾸면 다른 분류/능력단위
     * 오퍼레이션도 그대로 탄다 — 오퍼레이션별 파라미터는 {@code extraQuery}로,
     * 응답 item 모양은 {@code itemType}으로 넘긴다.
     *
     * @param operation  오퍼레이션 경로, 예: "/NCS001"
     * @param extraQuery 오퍼레이션 전용 추가 쿼리 파라미터(반드시 '&'로 시작, 없으면 "")
     * @param itemType   응답 item의 제네릭 타입 참조, 예: {@code new ParameterizedTypeReference<>() {}}
     */
    public <T> List<T> fetchAll(String operation, String extraQuery, ParameterizedTypeReference<NcsEnvelope<T>> itemType) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException(
                    "app.ncs.service-key 가 설정되어 있지 않습니다 (application-secret.yml 확인)");
        }

        List<T> result = new ArrayList<>();
        int pageNo = 1;
        int totalCount;
        do {
            NcsEnvelope.Body<T> body = fetchPage(operation, extraQuery, pageNo, itemType).response().body();
            List<T> items = (body.items() != null && body.items().item() != null)
                    ? body.items().item()
                    : List.of();
            result.addAll(items);
            totalCount = body.totalCount();
            pageNo++;
        } while (result.size() < totalCount && !result.isEmpty());

        return result;
    }

    private <T> NcsEnvelope<T> fetchPage(String operation, String extraQuery, int pageNo,
                                         ParameterizedTypeReference<NcsEnvelope<T>> itemType) {
        // serviceKey는 공공데이터포털에서 이미 URL-encode된 값 그대로 발급된다(예: '/' -> %2F).
        // UriComponentsBuilder.queryParam() 등으로 다시 조립하면 %2F가 %252F로 이중 인코딩되어
        // 인증에 실패하므로, 완성된 문자열을 URI.create()로 그대로 감싸 재인코딩을 피한다.
        String url = BASE_URL + operation
                + "?serviceKey=" + serviceKey
                + "&pageNo=" + pageNo
                + "&numOfRows=" + DEFAULT_PAGE_SIZE
                + extraQuery;

        // 1. 공공 API 원본 응답 문자열 먼저 확인
        String rawBody = restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(String.class);
        System.out.println("==================================================");
        System.out.println(">>> [" + operation + " 원본 응답]: " + rawBody);
        System.out.println("==================================================");

        NcsEnvelope<T> response = restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(itemType);

        if (response == null || response.response() == null || response.response().header() == null
                || response.response().body() == null) {
            throw new IllegalStateException("NCS API 응답 형식이 예상과 다릅니다");
        }
        String resultCode = response.response().header().resultCode();
        if (!"00".equals(resultCode)) {
            throw new IllegalStateException(
                    "NCS API 오류 응답: " + resultCode + " " + response.response().header().resultMsg());
        }
        return response;
    }
}
