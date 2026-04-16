package dev.noobth.pulsebackend.scheduler;

import dev.noobth.pulsebackend.domain.Api;
import dev.noobth.pulsebackend.domain.CheckResult;
import dev.noobth.pulsebackend.repository.ApiRepository;
import dev.noobth.pulsebackend.repository.CheckResultRepository;
import dev.noobth.pulsebackend.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
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
    @Mock private TaskScheduler taskScheduler;
    @Mock private WebClient.RequestBodyUriSpec requestSpec;
    @Mock private WebClient.ResponseSpec responseSpec;
    @Mock private ScheduledFuture<?> mockFuture;

    @InjectMocks private MonitoringScheduler scheduler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
            .thenReturn((ScheduledFuture) mockFuture);
    }

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

    // ---- init() ----

    @Test
    void init_schedulesEnabledApisOnly() {
        Api enabled = createApi();
        Api disabled = createApi();
        disabled.setEnabled(false);
        when(apiRepository.findAll()).thenReturn(List.of(enabled, disabled));

        scheduler.init();

        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    // ---- scheduleNextCheck ----

    @Test
    void scheduleNextCheck_firesImmediately_whenNextCheckAtIsNull() {
        Api api = createApi(); // nextCheckAt = null

        scheduler.scheduleNextCheck(api);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), captor.capture());
        assertThat(captor.getValue()).isBeforeOrEqualTo(Instant.now().plusMillis(100));
    }

    @Test
    void scheduleNextCheck_firesImmediately_whenNextCheckAtIsInPast() {
        Api api = createApi();
        api.setNextCheckAt(Instant.now().minusSeconds(60).toString());

        scheduler.scheduleNextCheck(api);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), captor.capture());
        assertThat(captor.getValue()).isBeforeOrEqualTo(Instant.now().plusMillis(100));
    }

    @Test
    void scheduleNextCheck_schedulesAtFutureTime_whenNextCheckAtIsInFuture() {
        Api api = createApi();
        Instant future = Instant.now().plusSeconds(60);
        api.setNextCheckAt(future.toString());

        scheduler.scheduleNextCheck(api);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), captor.capture());
        assertThat(captor.getValue()).isEqualTo(Instant.parse(api.getNextCheckAt()));
    }

    @Test
    void unschedule_cancelsFuture() {
        Api api = createApi();
        scheduler.scheduleNextCheck(api);

        scheduler.unschedule(api.getApiId());

        verify(mockFuture).cancel(false);
    }

    // ---- executeCheck: skip conditions ----

    @Test
    void executeCheck_skipsDisabledApi() {
        Api api = createApi();
        api.setEnabled(false);
        when(apiRepository.findById("api1")).thenReturn(Optional.of(api));

        scheduler.executeCheck("api1");

        verify(webClient, never()).method(any());
    }

    @Test
    void executeCheck_skipsDeletedApi() {
        when(apiRepository.findById("api1")).thenReturn(Optional.empty());

        scheduler.executeCheck("api1");

        verify(webClient, never()).method(any());
    }

    // ---- executeCheck: success path ----

    @Test
    void executeCheck_onSuccess_savesCheckResultWithSuccessTrue() {
        when(apiRepository.findById("api1")).thenReturn(Optional.of(createApi()));
        mockWebClient(Mono.just(ResponseEntity.status(200).body("ok")));

        scheduler.executeCheck("api1");

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
    void executeCheck_onSuccess_updatesNextCheckAt() {
        Api api = createApi();
        when(apiRepository.findById("api1")).thenReturn(Optional.of(api));
        mockWebClient(Mono.just(ResponseEntity.ok("ok")));

        Instant before = Instant.now();
        scheduler.executeCheck("api1");

        assertThat(api.getNextCheckAt()).isNotNull();
        assertThat(Instant.parse(api.getNextCheckAt())).isAfter(before);
    }

    @Test
    void executeCheck_onSuccess_resetsConsecutiveFailures() {
        Api api = createApi();
        api.setConsecutiveFailures(3);
        when(apiRepository.findById("api1")).thenReturn(Optional.of(api));
        mockWebClient(Mono.just(ResponseEntity.ok("ok")));

        scheduler.executeCheck("api1");

        assertThat(api.getConsecutiveFailures()).isEqualTo(0);
    }

    @Test
    void executeCheck_onSuccess_savesApiOnce() {
        Api api = createApi(); // consecutiveFailures = 0
        when(apiRepository.findById("api1")).thenReturn(Optional.of(api));
        mockWebClient(Mono.just(ResponseEntity.ok("ok")));

        scheduler.executeCheck("api1");

        verify(apiRepository, times(1)).save(api);
    }

    @Test
    void executeCheck_onSuccess_schedulesNextCheck() {
        when(apiRepository.findById("api1")).thenReturn(Optional.of(createApi()));
        mockWebClient(Mono.just(ResponseEntity.ok("ok")));

        scheduler.executeCheck("api1");

        // scheduleNextCheck 호출: executeCheck 시작 1회 + handleSuccess finally 1회 = 2회
        // 하지만 executeCheck 자체는 외부에서 직접 호출하므로 scheduleNextCheck는 finally에서 1회만 발생
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    // ---- executeCheck: error path: HTTP error ----

    @Test
    void executeCheck_onHttpError_savesCheckResultWithStatusCode() {
        when(apiRepository.findById("api1")).thenReturn(Optional.of(createApi()));
        mockWebClient(Mono.error(WebClientResponseException.create(
            404, "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8)));

        scheduler.executeCheck("api1");

        ArgumentCaptor<CheckResult> captor = ArgumentCaptor.forClass(CheckResult.class);
        verify(checkResultRepository).save(captor.capture());
        assertThat(captor.getValue().getSuccess()).isFalse();
        assertThat(captor.getValue().getStatusCode()).isEqualTo(404);
        assertThat(captor.getValue().getErrorType()).isEqualTo("HTTP_404");
    }

    // ---- executeCheck: error path: timeout ----

    @Test
    void executeCheck_onTimeout_savesCheckResultWithTimeoutType() {
        when(apiRepository.findById("api1")).thenReturn(Optional.of(createApi()));
        mockWebClient(Mono.error(new TimeoutException()));

        scheduler.executeCheck("api1");

        ArgumentCaptor<CheckResult> captor = ArgumentCaptor.forClass(CheckResult.class);
        verify(checkResultRepository).save(captor.capture());
        assertThat(captor.getValue().getSuccess()).isFalse();
        assertThat(captor.getValue().getErrorType()).isEqualTo("TIMEOUT");
        assertThat(captor.getValue().getStatusCode()).isNull();
    }

    // ---- executeCheck: error path: connection error ----

    @Test
    void executeCheck_onConnectionError_savesCheckResultWithConnectionErrorType() {
        when(apiRepository.findById("api1")).thenReturn(Optional.of(createApi()));
        mockWebClient(Mono.error(connectionError()));

        scheduler.executeCheck("api1");

        ArgumentCaptor<CheckResult> captor = ArgumentCaptor.forClass(CheckResult.class);
        verify(checkResultRepository).save(captor.capture());
        assertThat(captor.getValue().getSuccess()).isFalse();
        assertThat(captor.getValue().getErrorType()).isEqualTo("CONNECTION_ERROR");
        assertThat(captor.getValue().getStatusCode()).isNull();
    }

    // ---- executeCheck: error path: unknown error ----

    @Test
    void executeCheck_onUnknownError_savesCheckResultWithUnknownType() {
        when(apiRepository.findById("api1")).thenReturn(Optional.of(createApi()));
        mockWebClient(Mono.error(new RuntimeException("unexpected")));

        scheduler.executeCheck("api1");

        ArgumentCaptor<CheckResult> captor = ArgumentCaptor.forClass(CheckResult.class);
        verify(checkResultRepository).save(captor.capture());
        assertThat(captor.getValue().getSuccess()).isFalse();
        assertThat(captor.getValue().getErrorType()).isEqualTo("UNKNOWN_ERROR");
    }

    // ---- executeCheck: error path: common behaviors ----

    @Test
    void executeCheck_onError_incrementsConsecutiveFailures() {
        Api api = createApi();
        api.setConsecutiveFailures(2);
        when(apiRepository.findById("api1")).thenReturn(Optional.of(api));
        mockWebClient(Mono.error(new TimeoutException()));

        scheduler.executeCheck("api1");

        assertThat(api.getConsecutiveFailures()).isEqualTo(3);
    }

    @Test
    void executeCheck_onError_callsAlertService() {
        when(apiRepository.findById("api1")).thenReturn(Optional.of(createApi()));
        mockWebClient(Mono.error(new TimeoutException()));

        scheduler.executeCheck("api1");

        verify(alertService).alertIfNeeded(any(Api.class), anyString(), any(), anyLong());
    }

    @Test
    void executeCheck_onError_updatesNextCheckAt() {
        Api api = createApi();
        when(apiRepository.findById("api1")).thenReturn(Optional.of(api));
        mockWebClient(Mono.error(new TimeoutException()));

        Instant before = Instant.now();
        scheduler.executeCheck("api1");

        assertThat(api.getNextCheckAt()).isNotNull();
        assertThat(Instant.parse(api.getNextCheckAt())).isAfter(before);
    }

    // ---- retry behavior ----

    @Test
    void executeCheck_doesNotRetry_onHttpError() {
        // retryCount=2이지만 HTTP 에러는 retry 필터를 통과하지 못해야 한다
        Api api = createApi();
        api.setRetryCount(2);
        when(apiRepository.findById("api1")).thenReturn(Optional.of(api));
        mockWebClient(Mono.error(WebClientResponseException.create(
            500, "Server Error", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8)));

        scheduler.executeCheck("api1");

        verify(checkResultRepository, times(1)).save(any(CheckResult.class));
        verify(alertService, times(1)).alertIfNeeded(any(Api.class), anyString(), any(), anyLong());
    }
}
