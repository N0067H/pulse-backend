package dev.noobth.pulsebackend.scheduler;

import dev.noobth.pulsebackend.domain.Api;
import dev.noobth.pulsebackend.domain.CheckResult;
import dev.noobth.pulsebackend.repository.ApiRepository;
import dev.noobth.pulsebackend.repository.CheckResultRepository;
import dev.noobth.pulsebackend.service.AlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitoringSchedulerTest {

    @Mock private ApiRepository apiRepository;
    @Mock private CheckResultRepository checkResultRepository;
    @Mock private AlertService alertService;
    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec requestSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    @InjectMocks private MonitoringScheduler scheduler;

    private Api createApi() {
        Api api = new Api();
        api.setApiId("api1");
        api.setUrl("http://test.com");
        api.setMethod("GET");
        api.setEnabled(true);
        api.setNextCheckAt(null);
        api.setTimeoutSeconds(5);
        api.setRetryCount(0);
        api.setIntervalSeconds(60);
        api.setConsecutiveFailures(0);
        api.setAlertThreshold(3);
        api.setAlertCooldownSeconds(3600);
        return api;
    }

    @SuppressWarnings("unchecked")
    private void mockWebClient(Mono<ResponseEntity<String>> response) {
        when(webClient.method(any())).thenReturn(requestSpec);
        when(requestSpec.uri(anyString())).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntity(String.class)).thenReturn(response);
    }

    private static WebClientRequestException connectionError() {
        return new WebClientRequestException(
                new IOException("Connection refused"), HttpMethod.GET, URI.create("http://test.com"), new HttpHeaders());
    }

    // ---- filter: enabled / nextCheckAt ----

    @Test
    void monitor_skipsDisabledApi() {
        Api api = createApi();
        api.setEnabled(false);
        when(apiRepository.findAll()).thenReturn(List.of(api));

        scheduler.monitor();

        verify(webClient, never()).method(any());
    }

    @Test
    void monitor_skipsApiWithFutureNextCheckAt() {
        Api api = createApi();
        api.setNextCheckAt(Instant.now().plusSeconds(60).toString());
        when(apiRepository.findAll()).thenReturn(List.of(api));

        scheduler.monitor();

        verify(webClient, never()).method(any());
    }

    @Test
    void monitor_checksApiWithNullNextCheckAt() {
        Api api = createApi(); // nextCheckAt = null
        when(apiRepository.findAll()).thenReturn(List.of(api));
        mockWebClient(Mono.just(ResponseEntity.ok("ok")));

        scheduler.monitor();

        verify(webClient).method(HttpMethod.GET);
    }

    @Test
    void monitor_checksApiWithPastNextCheckAt() {
        Api api = createApi();
        api.setNextCheckAt(Instant.now().minusSeconds(1).toString());
        when(apiRepository.findAll()).thenReturn(List.of(api));
        mockWebClient(Mono.just(ResponseEntity.ok("ok")));

        scheduler.monitor();

        verify(webClient).method(HttpMethod.GET);
    }

    // ---- success path ----

    @Test
    void monitor_onSuccess_savesCheckResultWithSuccessTrue() {
        when(apiRepository.findAll()).thenReturn(List.of(createApi()));
        mockWebClient(Mono.just(ResponseEntity.status(200).body("ok")));

        scheduler.monitor();

        ArgumentCaptor<CheckResult> captor = ArgumentCaptor.forClass(CheckResult.class);
        verify(checkResultRepository).save(captor.capture());
        CheckResult saved = captor.getValue();
        assertThat(saved.getSuccess()).isTrue();
        assertThat(saved.getStatusCode()).isEqualTo(200);
        assertThat(saved.getErrorType()).isNull();
        assertThat(saved.getLatencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(saved.getTtl()).isNotNull();
        assertThat(saved.getApiId()).isEqualTo("api1");
    }

    @Test
    void monitor_onSuccess_updatesNextCheckAt() {
        Api api = createApi();
        when(apiRepository.findAll()).thenReturn(List.of(api));
        mockWebClient(Mono.just(ResponseEntity.ok("ok")));

        Instant before = Instant.now();
        scheduler.monitor();

        assertThat(api.getNextCheckAt()).isNotNull();
        assertThat(Instant.parse(api.getNextCheckAt())).isAfter(before);
    }

    @Test
    void monitor_onSuccess_resetsConsecutiveFailures() {
        Api api = createApi();
        api.setConsecutiveFailures(3);
        when(apiRepository.findAll()).thenReturn(List.of(api));
        mockWebClient(Mono.just(ResponseEntity.ok("ok")));

        scheduler.monitor();

        assertThat(api.getConsecutiveFailures()).isEqualTo(0);
    }

    @Test
    void monitor_onSuccess_doesNotSaveApi_whenConsecutiveFailuresAlreadyZero() {
        Api api = createApi(); // consecutiveFailures = 0
        when(apiRepository.findAll()).thenReturn(List.of(api));
        mockWebClient(Mono.just(ResponseEntity.ok("ok")));

        scheduler.monitor();

        // resetConsecutiveFailures는 0이면 save를 호출하지 않으므로 nextCheckAt 업데이트 1번만 발생
        verify(apiRepository, times(1)).save(api);
    }

    // ---- error path: HTTP error ----

    @Test
    void monitor_onHttpError_savesCheckResultWithStatusCode() {
        when(apiRepository.findAll()).thenReturn(List.of(createApi()));
        mockWebClient(Mono.error(WebClientResponseException.create(
                404, "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8)));

        scheduler.monitor();

        ArgumentCaptor<CheckResult> captor = ArgumentCaptor.forClass(CheckResult.class);
        verify(checkResultRepository).save(captor.capture());
        assertThat(captor.getValue().getSuccess()).isFalse();
        assertThat(captor.getValue().getStatusCode()).isEqualTo(404);
        assertThat(captor.getValue().getErrorType()).isEqualTo("HTTP_404");
    }

    // ---- error path: timeout ----

    @Test
    void monitor_onTimeout_savesCheckResultWithTimeoutType() {
        when(apiRepository.findAll()).thenReturn(List.of(createApi()));
        mockWebClient(Mono.error(new TimeoutException()));

        scheduler.monitor();

        ArgumentCaptor<CheckResult> captor = ArgumentCaptor.forClass(CheckResult.class);
        verify(checkResultRepository).save(captor.capture());
        assertThat(captor.getValue().getSuccess()).isFalse();
        assertThat(captor.getValue().getErrorType()).isEqualTo("TIMEOUT");
        assertThat(captor.getValue().getStatusCode()).isNull();
    }

    // ---- error path: connection error ----

    @Test
    void monitor_onConnectionError_savesCheckResultWithConnectionErrorType() {
        when(apiRepository.findAll()).thenReturn(List.of(createApi()));
        mockWebClient(Mono.error(connectionError()));

        scheduler.monitor();

        ArgumentCaptor<CheckResult> captor = ArgumentCaptor.forClass(CheckResult.class);
        verify(checkResultRepository).save(captor.capture());
        assertThat(captor.getValue().getSuccess()).isFalse();
        assertThat(captor.getValue().getErrorType()).isEqualTo("CONNECTION_ERROR");
        assertThat(captor.getValue().getStatusCode()).isNull();
    }

    // ---- error path: unknown error ----

    @Test
    void monitor_onUnknownError_savesCheckResultWithUnknownType() {
        when(apiRepository.findAll()).thenReturn(List.of(createApi()));
        mockWebClient(Mono.error(new RuntimeException("unexpected")));

        scheduler.monitor();

        ArgumentCaptor<CheckResult> captor = ArgumentCaptor.forClass(CheckResult.class);
        verify(checkResultRepository).save(captor.capture());
        assertThat(captor.getValue().getSuccess()).isFalse();
        assertThat(captor.getValue().getErrorType()).isEqualTo("UNKNOWN_ERROR");
    }

    // ---- error path: common behaviors ----

    @Test
    void monitor_onError_incrementsConsecutiveFailures() {
        Api api = createApi();
        api.setConsecutiveFailures(2);
        when(apiRepository.findAll()).thenReturn(List.of(api));
        mockWebClient(Mono.error(new TimeoutException()));

        scheduler.monitor();

        assertThat(api.getConsecutiveFailures()).isEqualTo(3);
    }

    @Test
    void monitor_onError_callsAlertService() {
        when(apiRepository.findAll()).thenReturn(List.of(createApi()));
        mockWebClient(Mono.error(new TimeoutException()));

        scheduler.monitor();

        verify(alertService).alertIfNeeded(any(Api.class));
    }

    @Test
    void monitor_onError_updatesNextCheckAt() {
        Api api = createApi();
        when(apiRepository.findAll()).thenReturn(List.of(api));
        mockWebClient(Mono.error(new TimeoutException()));

        Instant before = Instant.now();
        scheduler.monitor();

        assertThat(api.getNextCheckAt()).isNotNull();
        assertThat(Instant.parse(api.getNextCheckAt())).isAfter(before);
    }

    // ---- retry behavior ----

    @Test
    void monitor_doesNotRetry_onHttpError() {
        // retryCount=2이지만 HTTP 에러는 retry 필터를 통과하지 못해야 한다
        Api api = createApi();
        api.setRetryCount(2);
        when(apiRepository.findAll()).thenReturn(List.of(api));
        mockWebClient(Mono.error(WebClientResponseException.create(
                500, "Server Error", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8)));

        scheduler.monitor();

        verify(checkResultRepository, times(1)).save(any(CheckResult.class));
        verify(alertService, times(1)).alertIfNeeded(any());
    }
}
