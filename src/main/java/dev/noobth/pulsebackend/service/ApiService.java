package dev.noobth.pulsebackend.service;

import dev.noobth.pulsebackend.domain.Api;
import dev.noobth.pulsebackend.domain.CheckResult;
import dev.noobth.pulsebackend.dto.CreateApiRequestDto;
import dev.noobth.pulsebackend.dto.CreateApiResponseDto;
import dev.noobth.pulsebackend.repository.ApiRepository;
import dev.noobth.pulsebackend.repository.CheckResultRepository;
import dev.noobth.pulsebackend.scheduler.MonitoringScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiService {

    private final ApiRepository apiRepository;
    private final CheckResultRepository checkResultRepository;
    private final MonitoringScheduler monitoringScheduler;

    public CreateApiResponseDto registerApi(CreateApiRequestDto request) {
        Api api = new Api();
        api.setApiId(UUID.randomUUID().toString());
        api.setUrl(request.url());
        api.setMethod(request.method().name());
        api.setIntervalSeconds(request.intervalSeconds());
        api.setTimeoutSeconds(request.timeoutSeconds());
        api.setRetryCount(request.retryCount());
        api.setAlertThreshold(request.alertThreshold());
        api.setAlertCooldownSeconds(request.alertCooldownSeconds());
        api.setNextCheckAt(Instant.now().toString());

        apiRepository.save(api);
        monitoringScheduler.scheduleNextCheck(api);

        return new CreateApiResponseDto(api.getApiId(), "registered");
    }

    public List<Api> getAllApis() {
        return apiRepository.findAll();
    }

    public Api getApi(String apiId) {
        return apiRepository.findById(apiId)
            .orElseThrow(() -> new NoSuchElementException("API not found: " + apiId));
    }

    public void deleteApi(String apiId) {
        apiRepository.findById(apiId)
            .orElseThrow(() -> new NoSuchElementException("API not found: " + apiId));
        apiRepository.deleteById(apiId);
        monitoringScheduler.unschedule(apiId);
    }

    public boolean toggleEnabled(String apiId) {
        Api api = apiRepository.findById(apiId)
            .orElseThrow(() -> new NoSuchElementException("API not found: " + apiId));
        api.setEnabled(!api.isEnabled());
        apiRepository.save(api);
        if (api.isEnabled()) {
            monitoringScheduler.scheduleNextCheck(api);
        } else {
            monitoringScheduler.unschedule(apiId);
        }
        return api.isEnabled();
    }

    public List<CheckResult> getResults(String apiId, int limit) {
        apiRepository.findById(apiId)
            .orElseThrow(() -> new NoSuchElementException("API not found: " + apiId));
        return checkResultRepository.findByApiId(apiId).stream()
            .sorted(Comparator.comparing(CheckResult::getCheckedAt).reversed())
            .limit(limit)
            .toList();
    }
}
