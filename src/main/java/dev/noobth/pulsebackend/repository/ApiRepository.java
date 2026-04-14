package dev.noobth.pulsebackend.repository;

import dev.noobth.pulsebackend.domain.Api;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.util.List;
import java.util.Optional;

@Repository
public class ApiRepository {
    private static final String TABLE_NAME = "apis";
    private final DynamoDbTable<Api> apiTable;

    public ApiRepository(DynamoDbEnhancedClient enhancedClient) {
        this.apiTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(Api.class));
    }

    public Api save(Api api) {
        apiTable.putItem(api);
        return api;
    }

    public Optional<Api> findById(String apiId) {
        Api api = apiTable.getItem(Key.builder().partitionValue(apiId).build());
        return Optional.ofNullable(api);
    }

    public List<Api> findAll() {
        return apiTable.scan(ScanEnhancedRequest.builder().build())
            .items()
            .stream()
            .toList();
    }

    public void deleteById(String apiId) {
        apiTable.deleteItem(Key.builder().partitionValue(apiId).build());
    }

    public void updateAlertSentAt(String apiId, String alertSentAt) {
        Api api = findById(apiId)
            .orElseThrow(() -> new IllegalArgumentException("API not found: " + apiId));
        api.setAlertSentAt(alertSentAt);
        apiTable.updateItem(api);
    }
}
