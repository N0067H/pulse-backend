package dev.noobth.pulsebackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;

public record CreateApiRequestDto(
        @NotBlank @URL String url,
        @NotNull Method method,
        @JsonProperty("interval_seconds") @Min(1) int intervalSeconds,
        @JsonProperty("timeout_seconds") @Min(1) int timeoutSeconds,
        @JsonProperty("retry_count") @Min(0) @Max(2) int retryCount,
        @JsonProperty("alert_threshold") @Min(1) int alertThreshold,
        @JsonProperty("alert_cooldown_seconds") @Min(0) int alertCooldownSeconds
) {
}
