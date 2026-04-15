package dev.noobth.pulsebackend.e2e;

import dev.noobth.pulsebackend.scheduler.MonitoringScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiCrudE2ETest extends AbstractE2ETest {

    // Scheduler를 mock하여 등록된 API에 대한 실제 모니터링을 방지합니다.
    @MockitoBean
    MonitoringScheduler monitoringScheduler;

    private Map<String, Object> validRequest() {
        return Map.of(
                "url", "https://httpbin.org/get",
                "method", "GET",
                "interval_seconds", 180,
                "timeout_seconds", 5,
                "retry_count", 0,
                "alert_threshold", 3,
                "alert_cooldown_seconds", 3600
        );
    }

    private String registerApiAndGetId() {
        return webTestClient.post().uri("/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequest())
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody()
                .get("api_id").toString();
    }

    // ---- POST /apis ----

    @Test
    void registerApi_returns201_withApiIdAndMessage() {
        webTestClient.post().uri("/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequest())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.api_id").isNotEmpty()
                .jsonPath("$.message").isEqualTo("registered");
    }

    @Test
    void registerApi_returns400_whenUrlIsInvalid() {
        Map<String, Object> request = new HashMap<>(validRequest());
        request.put("url", "not-a-valid-url");

        webTestClient.post().uri("/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void registerApi_returns400_whenUrlIsBlank() {
        Map<String, Object> request = new HashMap<>(validRequest());
        request.put("url", "");

        webTestClient.post().uri("/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void registerApi_returns400_whenMethodIsNull() {
        Map<String, Object> request = new HashMap<>(validRequest());
        request.remove("method");

        webTestClient.post().uri("/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void registerApi_returns400_whenRetryCountExceedsMax() {
        Map<String, Object> request = new HashMap<>(validRequest());
        request.put("retry_count", 3);

        webTestClient.post().uri("/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void registerApi_returns400_whenIntervalSecondsIsZero() {
        Map<String, Object> request = new HashMap<>(validRequest());
        request.put("interval_seconds", 0);

        webTestClient.post().uri("/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void registerApi_returns400_whenTimeoutSecondsIsZero() {
        Map<String, Object> request = new HashMap<>(validRequest());
        request.put("timeout_seconds", 0);

        webTestClient.post().uri("/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ---- GET /apis ----

    @Test
    void getAllApis_returns200_withEmptyList_whenNoneRegistered() {
        webTestClient.get().uri("/apis")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void getAllApis_returns200_withAllRegisteredApis() {
        registerApiAndGetId();
        registerApiAndGetId();

        webTestClient.get().uri("/apis")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);
    }

    // ---- GET /apis/{apiId} ----

    @Test
    void getApi_returns200_withCorrectFields() {
        String apiId = registerApiAndGetId();

        webTestClient.get().uri("/apis/" + apiId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.apiId").isEqualTo(apiId)
                .jsonPath("$.url").isEqualTo("https://httpbin.org/get")
                .jsonPath("$.method").isEqualTo("GET")
                .jsonPath("$.intervalSeconds").isEqualTo(180)
                .jsonPath("$.timeoutSeconds").isEqualTo(5)
                .jsonPath("$.retryCount").isEqualTo(0)
                .jsonPath("$.alertThreshold").isEqualTo(3)
                .jsonPath("$.enabled").isEqualTo(true)
                .jsonPath("$.consecutiveFailures").isEqualTo(0);
    }

    @Test
    void getApi_returns404_whenNotFound() {
        webTestClient.get().uri("/apis/non-existent-id")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").value(msg ->
                        assertThat(msg.toString()).contains("non-existent-id"));
    }

    // ---- DELETE /apis/{apiId} ----

    @Test
    void deleteApi_returns204_andApiIsNoLongerAccessible() {
        String apiId = registerApiAndGetId();

        webTestClient.delete().uri("/apis/" + apiId)
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get().uri("/apis/" + apiId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteApi_returns404_whenApiNotFound() {
        webTestClient.delete().uri("/apis/non-existent-id")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteApi_removesApiFromList() {
        String apiId = registerApiAndGetId();

        webTestClient.delete().uri("/apis/" + apiId)
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get().uri("/apis")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);
    }

    // ---- PATCH /apis/{apiId}/enabled ----

    @Test
    void toggleEnabled_disablesApi() {
        String apiId = registerApiAndGetId();

        webTestClient.patch().uri("/apis/" + apiId + "/enabled")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.enabled").isEqualTo(false);
    }

    @Test
    void toggleEnabled_disablesThenReEnablesApi() {
        String apiId = registerApiAndGetId();

        // true → false
        webTestClient.patch().uri("/apis/" + apiId + "/enabled")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.enabled").isEqualTo(false);

        // false → true
        webTestClient.patch().uri("/apis/" + apiId + "/enabled")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.enabled").isEqualTo(true);
    }

    @Test
    void toggleEnabled_isPersisted() {
        String apiId = registerApiAndGetId();

        webTestClient.patch().uri("/apis/" + apiId + "/enabled")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/apis/" + apiId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.enabled").isEqualTo(false);
    }

    @Test
    void toggleEnabled_returns404_whenApiNotFound() {
        webTestClient.patch().uri("/apis/non-existent-id/enabled")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ---- GET /apis/{apiId}/results ----

    @Test
    void getResults_returns200_withEmptyList_whenNoChecksPerformed() {
        String apiId = registerApiAndGetId();

        webTestClient.get().uri("/apis/" + apiId + "/results")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void getResults_returns200_withDefaultLimit50() {
        String apiId = registerApiAndGetId();

        webTestClient.get().uri("/apis/" + apiId + "/results")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getResults_returns200_withCustomLimit() {
        String apiId = registerApiAndGetId();

        webTestClient.get().uri("/apis/" + apiId + "/results?limit=10")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getResults_returns404_whenApiNotFound() {
        webTestClient.get().uri("/apis/non-existent-id/results")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").value(msg ->
                        assertThat(msg.toString()).contains("non-existent-id"));
    }
}
