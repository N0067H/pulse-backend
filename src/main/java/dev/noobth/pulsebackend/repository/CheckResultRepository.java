package dev.noobth.pulsebackend.repository;

import dev.noobth.pulsebackend.domain.CheckResult;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;

@Repository
public class CheckResultRepository {
    private static final String TABLE_NAME = "check_results";
    private final DynamoDbTable<CheckResult> checkResultTable;

    public CheckResultRepository(DynamoDbEnhancedClient enhancedClient) {
        this.checkResultTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(CheckResult.class));
    }

    public CheckResult save(CheckResult checkResult) {
        checkResultTable.putItem(checkResult);
        return checkResult;
    }

    public Optional<CheckResult> findById(String apiId, String checkedAt) {
        CheckResult item = checkResultTable.getItem(
            Key.builder()
                .partitionValue(apiId)
                .sortValue(checkedAt)
                .build()
        );
        return Optional.ofNullable(item);
    }

    public List<CheckResult> findByApiId(String apiId) {
        return checkResultTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(apiId).build()))
            .items()
            .stream()
            .toList();
    }

    public Optional<CheckResult> findLatestByApiId(String apiId) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
            .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(apiId).build()))
            .scanIndexForward(false)
            .limit(1)
            .build();

        return checkResultTable.query(request)
            .items()
            .stream()
            .findFirst();
    }
}
