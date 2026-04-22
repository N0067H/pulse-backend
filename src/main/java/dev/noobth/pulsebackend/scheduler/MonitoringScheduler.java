package dev.noobth.pulsebackend.scheduler;

import dev.noobth.pulsebackend.domain.Api;
import dev.noobth.pulsebackend.domain.CheckResult;
import dev.noobth.pulsebackend.repository.ApiRepository;
import dev.noobth.pulsebackend.repository.CheckResultRepository;
import dev.noobth.pulsebackend.service.AlertService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class MonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitoringScheduler.class);
    private final ApiRepository apiRepository;
    private final CheckResultRepository checkResultRepository;
    private final AlertService alertService;
    private final WebClient webClient;
    private final TaskScheduler taskScheduler;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private reactor.core.scheduler.Scheduler reactiveScheduler = Schedulers.boundedElastic();

    void setReactiveScheduler(reactor.core.scheduler.Scheduler reactiveScheduler) {
        this.reactiveScheduler = reactiveScheduler;
    }

    @PostConstruct
    public void init() {
        Gauge.builder("api.scheduled.count", scheduledTasks, ConcurrentHashMap::size)
            .description("Number of currently scheduled API monitors")
            .register(meterRegistry);

        apiRepository.findAll().stream()
            .filter(Api::isEnabled)
            .forEach(this::scheduleNextCheck);
        log.info("MonitoringScheduler initialized with {} APIs scheduled", scheduledTasks.size());
    }

    public void scheduleNextCheck(Api api) {
        cancelIfScheduled(api.getApiId());
        Instant now = Instant.now();
        Instant nextCheck = api.getNextCheckAt() != null ? Instant.parse(api.getNextCheckAt()) : now;
        Instant fireAt = nextCheck.isBefore(now) ? now : nextCheck;
        scheduledTasks.put(api.getApiId(), taskScheduler.schedule(() -> executeCheck(api.getApiId()), fireAt));
    }

    public void unschedule(String apiId) {
        cancelIfScheduled(apiId);
    }

    private void cancelIfScheduled(String apiId) {
        ScheduledFuture<?> existing = scheduledTasks.remove(apiId);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    void executeCheck(String apiId) {
        Api api = apiRepository.findById(apiId).orElse(null);
        if (api == null || !api.isEnabled()) {
            scheduledTasks.remove(apiId);
            return;
        }

        long startTime = System.currentTimeMillis();

        webClient
            .method(HttpMethod.valueOf(api.getMethod()))
            .uri(api.getUrl())
            .retrieve()
            .toEntity(String.class)
            .timeout(Duration.ofSeconds(api.getTimeoutSeconds()))
            .retryWhen(Retry.fixedDelay(api.getRetryCount(), Duration.ofSeconds(1))
                .filter(e -> e instanceof TimeoutException || e instanceof WebClientRequestException))
            .publishOn(reactiveScheduler)
            .subscribe(
                entity -> handleSuccess(api, startTime, entity.getStatusCode().value()),
                error -> handleError(api, startTime, error)
            );
    }

    private void handleSuccess(Api api, long startTime, int statusCode) {
        long latency = System.currentTimeMillis() - startTime;
        log.info("Monitoring success: {} {} | status={} latency={}ms", api.getMethod(), api.getUrl(), statusCode, latency);

        meterRegistry.counter("api.check.total", "result", "success").increment();
        meterRegistry.timer("api.check.latency", "result", "success")
            .record(latency, TimeUnit.MILLISECONDS);

        try {
            saveCheckResult(api.getApiId(), statusCode, latency, true, null);
            if (api.getConsecutiveFailures() > 0) {
                api.setConsecutiveFailures(0);
            }
            api.setNextCheckAt(Instant.now().plusSeconds(api.getIntervalSeconds()).toString());
            apiRepository.save(api);
        } catch (Exception e) {
            log.error("Failed to persist result for {}: {}", api.getApiId(), e.getMessage(), e);
        } finally {
            scheduleNextCheck(api);
        }
    }

    private void handleError(Api api, long startTime, Throwable error) {
        long latency = System.currentTimeMillis() - startTime;
        Throwable cause = Exceptions.isRetryExhausted(error) ? error.getCause() : error;
        Integer statusCode = null;
        String errorType;

        switch (cause) {
            case WebClientResponseException ex -> {
                statusCode = ex.getStatusCode().value();
                errorType = "HTTP_" + statusCode;
                log.warn("Monitoring HTTP error: {} {} | status={} latency={}ms", api.getMethod(), api.getUrl(), statusCode, latency);
            }
            case TimeoutException ignored -> {
                errorType = "TIMEOUT";
                log.warn("Monitoring timeout: {} {} | latency={}ms", api.getMethod(), api.getUrl(), latency);
            }
            case WebClientRequestException ignored -> {
                errorType = "CONNECTION_ERROR";
                log.warn("Monitoring connection error: {} {} | error={} latency={}ms", api.getMethod(), api.getUrl(), cause.getMessage(), latency);
            }
            default -> {
                errorType = "UNKNOWN_ERROR";
                log.warn("Monitoring unknown error: {} {} | error={} latency={}ms", api.getMethod(), api.getUrl(), cause.getMessage(), latency);
            }
        }

        meterRegistry.counter("api.check.total", "result", "failure", "error_type", errorType).increment();
        meterRegistry.timer("api.check.latency", "result", "failure")
            .record(latency, TimeUnit.MILLISECONDS);

        try {
            saveCheckResult(api.getApiId(), statusCode, latency, false, errorType);
            api.setConsecutiveFailures(api.getConsecutiveFailures() + 1);
            api.setNextCheckAt(Instant.now().plusSeconds(api.getIntervalSeconds()).toString());
            alertService.alertIfNeeded(api, errorType, statusCode, latency);
            apiRepository.save(api);
        } catch (Exception e) {
            log.error("Failed to persist result for {}: {}", api.getApiId(), e.getMessage(), e);
        } finally {
            scheduleNextCheck(api);
        }
    }

    private void saveCheckResult(String apiId, Integer statusCode, long latencyMs, boolean success, String errorType) {
        CheckResult result = new CheckResult();
        result.setApiId(apiId);
        result.setCheckedAt(Instant.now().toString());
        result.setStatusCode(statusCode);
        result.setLatencyMs(latencyMs);
        result.setSuccess(success);
        result.setErrorType(errorType);
        result.setTtl(Instant.now().plus(30, ChronoUnit.DAYS).getEpochSecond());
        checkResultRepository.save(result);
    }

}
