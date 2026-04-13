package dev.noobth.pulsebackend.domain;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
public class Api {
    private String apiId;
    private String url;
    private String method;
    private int intervalSeconds = 180;
    private int timeoutSeconds = 5;
    private int retryCount = 0;
    private int alertThreshold = 3;
    private int alertCooldownSeconds = 3600;
    private int consecutiveFailures = 0;
    private String alertSentAt;
    private String nextCheckAt;
    private boolean enabled = true;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("api_id")
    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    @DynamoDbAttribute("url")
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @DynamoDbAttribute("method")
    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    @DynamoDbAttribute("interval_seconds")
    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    @DynamoDbAttribute("timeout_seconds")
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @DynamoDbAttribute("retry_count")
    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        if (retryCount < 0 || retryCount > 2) {
            throw new IllegalArgumentException("retryCount must be between 0 and 2");
        }
        this.retryCount = retryCount;
    }

    @DynamoDbAttribute("alert_threshold")
    public int getAlertThreshold() {
        return alertThreshold;
    }

    public void setAlertThreshold(int alertThreshold) {
        this.alertThreshold = alertThreshold;
    }

    @DynamoDbAttribute("alert_cooldown_seconds")
    public int getAlertCooldownSeconds() {
        return alertCooldownSeconds;
    }

    public void setAlertCooldownSeconds(int alertCooldownSeconds) {
        this.alertCooldownSeconds = alertCooldownSeconds;
    }

    @DynamoDbAttribute("consecutive_failures")
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    @DynamoDbAttribute("alert_sent_at")
    public String getAlertSentAt() {
        return alertSentAt;
    }

    public void setAlertSentAt(String alertSentAt) {
        this.alertSentAt = alertSentAt;
    }

    @DynamoDbAttribute("next_check_at")
    public String getNextCheckAt() {
        return nextCheckAt;
    }

    public void setNextCheckAt(String nextCheckAt) {
        this.nextCheckAt = nextCheckAt;
    }

    @DynamoDbAttribute("enabled")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
