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

    public void alertIfNeeded(Api api, String errorType, Integer statusCode, long latencyMs) {
        if (!shouldSendAlert(api)) {
            return;
        }

        apiRepository.updateAlertSentAt(api.getApiId(), Instant.now().toString());

        snsClient.publish(PublishRequest.builder()
            .topicArn(topicArn)
            .subject("[Pulse] API monitoring alert - " + api.getUrl())
            .message(buildMessage(api, errorType, statusCode, latencyMs))
            .build());
    }

    private String buildMessage(Api api, String errorType, Integer statusCode, long latencyMs) {
        return "API Monitoring Alert\n"
            + "====================\n\n"
            + "[API]\n"
            + "  ID      : " + api.getApiId() + "\n"
            + "  Request : " + api.getMethod() + " " + api.getUrl() + "\n\n"
            + "[Failure]\n"
            + "  Type    : " + errorType + "\n"
            + "  Status  : " + (statusCode != null ? statusCode : "N/A") + "\n"
            + "  Latency : " + latencyMs + "ms\n\n"
            + "[Summary]\n"
            + "  Consecutive failures : " + api.getConsecutiveFailures() + " (threshold: " + api.getAlertThreshold() + ")\n"
            + "  Detected at          : " + Instant.now() + "\n";
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
