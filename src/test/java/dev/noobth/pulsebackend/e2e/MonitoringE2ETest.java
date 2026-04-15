package dev.noobth.pulsebackend.e2e;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MonitoringE2ETest extends AbstractE2ETest {

    private MockWebServer mockWebServer;

    @BeforeEach
    void startMockWebServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void stopMockWebServer() throws IOException {
        mockWebServer.shutdown();
    }

    private String registerApi(String url) {
        Map<String, Object> request = Map.of(
                "url", url,
                "method", "GET",
                "interval_seconds", 1,
                "timeout_seconds", 3,
                "retry_count", 0,
                "alert_threshold", 5,
                "alert_cooldown_seconds", 3600
        );
        return webTestClient.post().uri("/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody()
                .get("api_id").toString();
    }

    private List<?> getResults(String apiId) {
        return webTestClient.get()
                .uri("/apis/" + apiId + "/results")
                .exchange()
                .expectBody(List.class)
                .returnResult().getResponseBody();
    }

    private Map<?, ?> getApi(String apiId) {
        return webTestClient.get()
                .uri("/apis/" + apiId)
                .exchange()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
    }

    // ---- 성공 케이스 ----

    @Test
    void scheduler_recordsSuccessResult_whenEndpointReturns200() {
        // 스케줄러가 여러 번 실행될 수 있으므로 여분의 응답을 준비합니다.
        for (int i = 0; i < 10; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(200));
        }

        String apiId = registerApi(mockWebServer.url("/health").toString());

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    List<?> results = getResults(apiId);
                    return results != null && !results.isEmpty();
                });

        webTestClient.get().uri("/apis/" + apiId + "/results")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].success").isEqualTo(true)
                .jsonPath("$[0].statusCode").isEqualTo(200)
                .jsonPath("$[0].latencyMs").isNotEmpty();
    }

    // ---- HTTP 에러 케이스 ----

    @Test
    void scheduler_recordsFailureResult_whenEndpointReturns503() {
        for (int i = 0; i < 10; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        }

        String apiId = registerApi(mockWebServer.url("/health").toString());

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    List<?> results = getResults(apiId);
                    return results != null && !results.isEmpty();
                });

        webTestClient.get().uri("/apis/" + apiId + "/results")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].success").isEqualTo(false)
                .jsonPath("$[0].statusCode").isEqualTo(503)
                .jsonPath("$[0].errorType").isEqualTo("HTTP_503");
    }

    @Test
    void scheduler_recordsFailureResult_whenEndpointReturns500() {
        for (int i = 0; i < 10; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        }

        String apiId = registerApi(mockWebServer.url("/health").toString());

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    List<?> results = getResults(apiId);
                    return results != null && !results.isEmpty();
                });

        webTestClient.get().uri("/apis/" + apiId + "/results")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].success").isEqualTo(false)
                .jsonPath("$[0].errorType").isEqualTo("HTTP_500");
    }

    // ---- 연속 실패 카운터 ----

    @Test
    void scheduler_incrementsConsecutiveFailures_onRepeatedErrors() {
        for (int i = 0; i < 10; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        }

        String apiId = registerApi(mockWebServer.url("/health").toString());

        // 결과가 3개 이상 쌓일 때까지 대기 (3번의 스케줄러 실행)
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    List<?> results = getResults(apiId);
                    return results != null && results.size() >= 3;
                });

        Map<?, ?> api = getApi(apiId);
        int consecutiveFailures = ((Number) api.get("consecutiveFailures")).intValue();
        assertThat(consecutiveFailures).isGreaterThanOrEqualTo(3);
    }

    // ---- 성공 후 연속 실패 카운터 리셋 ----

    @Test
    void scheduler_resetsConsecutiveFailures_afterSuccessFollowingFailure() {
        // 첫 번째 요청: 실패
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        // 이후 요청들: 성공
        for (int i = 0; i < 10; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(200));
        }

        String apiId = registerApi(mockWebServer.url("/health").toString());

        // consecutiveFailures가 0으로 리셋되고, 결과가 2개 이상인 상태를 대기
        // (실패 1개 + 성공 1개 이상)
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    List<?> results = getResults(apiId);
                    Map<?, ?> api = getApi(apiId);
                    if (results == null || results.size() < 2 || api == null) {
                        return false;
                    }
                    int consecutiveFailures = ((Number) api.get("consecutiveFailures")).intValue();
                    return consecutiveFailures == 0;
                });

        // 최종 검증: consecutiveFailures가 0이고 가장 최신 결과가 성공
        webTestClient.get().uri("/apis/" + apiId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.consecutiveFailures").isEqualTo(0);

        webTestClient.get().uri("/apis/" + apiId + "/results")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].success").isEqualTo(true);
    }

    // ---- 비활성화된 API는 모니터링되지 않음 ----

    @Test
    void scheduler_skipsDisabledApis() {
        for (int i = 0; i < 10; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(200));
        }

        String apiId = registerApi(mockWebServer.url("/health").toString());

        // 등록 직후 비활성화
        webTestClient.patch().uri("/apis/" + apiId + "/enabled")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.enabled").isEqualTo(false);

        // 스케줄러가 최소 1회 이상 실행될 시간(7초)을 대기합니다.
        // 비활성화된 API는 모니터링 요청을 받지 않아야 합니다.
        try {
            Thread.sleep(7_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 비활성화 직후 모니터링이 이미 진행 중이었을 수 있어 최대 1건의 결과만 허용
        List<?> results = getResults(apiId);
        assertThat(results).hasSizeLessThanOrEqualTo(1);

        // MockWebServer에 소비되지 않은 응답이 남아있어야 합니다 (많은 요청이 오지 않음)
        assertThat(mockWebServer.getRequestCount()).isLessThanOrEqualTo(1);
    }
}
