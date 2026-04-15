package dev.noobth.pulsebackend.controller;

import dev.noobth.pulsebackend.domain.Api;
import dev.noobth.pulsebackend.domain.CheckResult;
import dev.noobth.pulsebackend.dto.CreateApiResponseDto;
import dev.noobth.pulsebackend.service.ApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiControllerTest {

    @Mock
    private ApiService apiService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient
                .bindToController(new ApiController(apiService))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Map<String, Object> validRequest() {
        return Map.of(
                "url", "https://example.com",
                "method", "GET",
                "interval_seconds", 180,
                "timeout_seconds", 5,
                "retry_count", 0,
                "alert_threshold", 3,
                "alert_cooldown_seconds", 3600
        );
    }

    private Api createApi(String apiId) {
        Api api = new Api();
        api.setApiId(apiId);
        api.setUrl("https://example.com");
        api.setMethod("GET");
        api.setEnabled(true);
        return api;
    }

    // ---- POST /apis ----

    @Test
    void registerApi_returns201_whenValidRequest() {
        when(apiService.registerApi(any()))
                .thenReturn(new CreateApiResponseDto("api-id", "registered"));

        webTestClient.post().uri("/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequest())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.api_id").isEqualTo("api-id")
                .jsonPath("$.message").isEqualTo("registered");
    }

    @Test
    void registerApi_returns400_whenUrlIsBlank() {
        Map<String, Object> request = new java.util.HashMap<>(validRequest());
        request.put("url", "");

        webTestClient.post().uri("/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();

        verify(apiService, never()).registerApi(any());
    }

    @Test
    void registerApi_returns400_whenMethodIsNull() {
        Map<String, Object> request = new java.util.HashMap<>(validRequest());
        request.remove("method");

        webTestClient.post().uri("/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();

        verify(apiService, never()).registerApi(any());
    }

    @Test
    void registerApi_returns400_whenRetryCountExceedsMax() {
        Map<String, Object> request = new java.util.HashMap<>(validRequest());
        request.put("retry_count", 3);

        webTestClient.post().uri("/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();

        verify(apiService, never()).registerApi(any());
    }

    // ---- GET /apis ----

    @Test
    void getAllApis_returns200_withList() {
        when(apiService.getAllApis()).thenReturn(List.of(createApi("api1"), createApi("api2")));

        webTestClient.get().uri("/apis")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);
    }

    // ---- GET /apis/{apiId} ----

    @Test
    void getApi_returns200_whenFound() {
        when(apiService.getApi("api1")).thenReturn(createApi("api1"));

        webTestClient.get().uri("/apis/api1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.apiId").isEqualTo("api1");
    }

    @Test
    void getApi_returns404_whenNotFound() {
        when(apiService.getApi("api1")).thenThrow(new NoSuchElementException("API not found: api1"));

        webTestClient.get().uri("/apis/api1")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo("API not found: api1");
    }

    // ---- DELETE /apis/{apiId} ----

    @Test
    void deleteApi_returns204_whenFound() {
        doNothing().when(apiService).deleteApi("api1");

        webTestClient.delete().uri("/apis/api1")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void deleteApi_returns404_whenNotFound() {
        doThrow(new NoSuchElementException("API not found: api1")).when(apiService).deleteApi("api1");

        webTestClient.delete().uri("/apis/api1")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo("API not found: api1");
    }

    // ---- PATCH /apis/{apiId}/enabled ----

    @Test
    void toggleEnabled_returns200_withEnabledState() {
        when(apiService.toggleEnabled("api1")).thenReturn(false);

        webTestClient.patch().uri("/apis/api1/enabled")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.enabled").isEqualTo(false);
    }

    @Test
    void toggleEnabled_returns404_whenNotFound() {
        when(apiService.toggleEnabled(eq("api1")))
                .thenThrow(new NoSuchElementException("API not found: api1"));

        webTestClient.patch().uri("/apis/api1/enabled")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo("API not found: api1");
    }

    // ---- GET /apis/{apiId}/results ----

    @Test
    void getResults_returns200_withResultsList() {
        CheckResult r = new CheckResult();
        r.setApiId("api1");
        r.setCheckedAt("2024-01-01T03:00:00Z");
        r.setSuccess(true);

        when(apiService.getResults("api1", 50)).thenReturn(List.of(r));

        webTestClient.get().uri("/apis/api1/results")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].checkedAt").isEqualTo("2024-01-01T03:00:00Z");
    }

    @Test
    void getResults_returns404_whenApiNotFound() {
        when(apiService.getResults(eq("api1"), anyInt()))
                .thenThrow(new NoSuchElementException("API not found: api1"));

        webTestClient.get().uri("/apis/api1/results")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo("API not found: api1");
    }

    @Test
    void getResults_passesLimitParam() {
        when(apiService.getResults("api1", 10)).thenReturn(List.of());

        webTestClient.get().uri("/apis/api1/results?limit=10")
                .exchange()
                .expectStatus().isOk();

        verify(apiService).getResults("api1", 10);
    }
}
