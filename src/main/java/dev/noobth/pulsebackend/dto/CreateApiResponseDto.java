package dev.noobth.pulsebackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateApiResponseDto(
        @JsonProperty("api_id") String apiId,
        String message
) {
}
