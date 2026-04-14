package dev.noobth.pulsebackend.service;

import dev.noobth.pulsebackend.domain.Api;
import dev.noobth.pulsebackend.repository.ApiRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AlertService {
    private final SnsClient snsClient;
    private final ApiRepository apiRepository;

    @Value("${aws.sns.topicArn}")
    private String topicArn;

    public void alertIfNeeded(String apiId) {
        Api api = apiRepository.findById(apiId).orElseThrow(() -> new IllegalArgumentException("API not found"));

        if (!shouldSendAlert(api)) {
            return;
        }

        apiRepository.updateAlertSentAt(apiId, Instant.now().toString());

        snsClient.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .subject("[Pulse] API Consecutive failures detected\n")
                .message("API Failed\nID: " + apiId + "\nURL: " + api.getUrl())
                .build());
    }

    private boolean shouldSendAlert(Api api) {
        if (api.getConsecutiveFailures() < api.getAlertThreshold()) {
            return false;
        }

        String alertSentAt = api.getAlertSentAt();
        if (alertSentAt == null) {
            return true;
        }

        long elapsed = Instant.now().getEpochSecond() - Instant.parse(alertSentAt).getEpochSecond();
        return elapsed >= api.getAlertCooldownSeconds();
    }
}
