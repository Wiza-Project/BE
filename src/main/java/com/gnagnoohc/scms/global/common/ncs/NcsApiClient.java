package com.gnagnoohc.scms.global.common.ncs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
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

    /** 공공데이터포털 고정 엔드포인트 — 환경(로컬/운영)에 따라 달라지지 않아 상수로 둔다. */
    private static final String BASE_URL = "https://apis.data.go.kr/B490007/hrdkapi";
    private static final int DEFAULT_PAGE_SIZE = 100;

    private final RestClient restClient;
    private final String serviceKey;

    public NcsApiClient(@Value("${app.ncs.service-key:}") String serviceKey) {
        this.serviceKey = serviceKey;
        this.restClient = RestClient.create();
    }

    /** NCS 대분류 전체를 조회한다(NCS001, USG_YN=Y — 최신 차수만). 총 24건 고정. */
    public List<NcsLclasItem> fetchAllLargeCategories() {
        return fetchAll("/NCS001", "&USG_YN=Y", new ParameterizedTypeReference<>() {
        });
    }

    /**
     * NCS001~NCS007 공통 페이징 전체 조회. {@code operation}만 바꾸면 다른 분류/능력단위
     * 오퍼레이션도 그대로 탄다 — 오퍼레이션별 파라미터는 {@code extraQuery}로,
     * 응답 item 모양은 {@code itemType}으로 넘긴다.
     *
     * @param operation 오퍼레이션 경로, 예: "/NCS001"
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

        NcsEnvelope<T> response = restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(itemType);

        if (response == null || response.response() == null || response.response().header() == null) {
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
