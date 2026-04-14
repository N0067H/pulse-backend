package dev.noobth.pulsebackend.service;

import dev.noobth.pulsebackend.domain.Api;
import dev.noobth.pulsebackend.dto.CreateApiRequestDto;
import dev.noobth.pulsebackend.dto.CreateApiResponseDto;
import dev.noobth.pulsebackend.repository.ApiRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiService {

    private final ApiRepository apiRepository;

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
    }

    public boolean toggleEnabled(String apiId) {
        Api api = apiRepository.findById(apiId)
            .orElseThrow(() -> new NoSuchElementException("API not found: " + apiId));
        api.setEnabled(!api.isEnabled());
        apiRepository.save(api);
        return api.isEnabled();
    }
}
