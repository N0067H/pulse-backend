package dev.noobth.pulsebackend.e2e;

import dev.noobth.pulsebackend.domain.Api;
import dev.noobth.pulsebackend.domain.CheckResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
abstract class AbstractE2ETest {

    @Container
    static final LocalStackContainer localStack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.DYNAMODB, LocalStackContainer.Service.SNS);

    @Autowired
    protected WebTestClient webTestClient;

    @Autowired
    protected DynamoDbEnhancedClient dynamoDbEnhancedClient;

    @DynamicPropertySource
    static void overrideAwsProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.dynamodb.endpoint",
                () -> localStack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB).toString());
        registry.add("aws.sns.endpoint",
                () -> localStack.getEndpointOverride(LocalStackContainer.Service.SNS).toString());
        registry.add("aws.sns.topicArn",
                () -> "arn:aws:sns:us-east-1:000000000000:pulse-alert");
    }

    @BeforeAll
    static void setUpInfrastructure() {
        try (DynamoDbClient dynamoDb = buildDynamoDbClient()) {
            createApisTable(dynamoDb);
            createCheckResultsTable(dynamoDb);
        }
        try (SnsClient sns = buildSnsClient()) {
            sns.createTopic(CreateTopicRequest.builder().name("pulse-alert").build());
        }
    }

    @BeforeEach
    void cleanUpTables() {
        DynamoDbTable<Api> apisTable = dynamoDbEnhancedClient.table("apis", TableSchema.fromBean(Api.class));
        apisTable.scan().items().forEach(apisTable::deleteItem);

        DynamoDbTable<CheckResult> resultsTable = dynamoDbEnhancedClient.table("check_results", TableSchema.fromBean(CheckResult.class));
        resultsTable.scan().items().forEach(resultsTable::deleteItem);
    }

    static DynamoDbClient buildDynamoDbClient() {
        return DynamoDbClient.builder()
                .endpointOverride(localStack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
                .region(Region.of(localStack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
                .build();
    }

    private static SnsClient buildSnsClient() {
        return SnsClient.builder()
                .endpointOverride(localStack.getEndpointOverride(LocalStackContainer.Service.SNS))
                .region(Region.of(localStack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
                .build();
    }

    private static void createApisTable(DynamoDbClient dynamoDb) {
        try {
            dynamoDb.createTable(CreateTableRequest.builder()
                    .tableName("apis")
                    .keySchema(KeySchemaElement.builder()
                            .attributeName("api_id")
                            .keyType(KeyType.HASH)
                            .build())
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName("api_id")
                            .attributeType(ScalarAttributeType.S)
                            .build())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
        } catch (ResourceInUseException ignored) {
        }
    }

    private static void createCheckResultsTable(DynamoDbClient dynamoDb) {
        try {
            dynamoDb.createTable(CreateTableRequest.builder()
                    .tableName("check_results")
                    .keySchema(
                            KeySchemaElement.builder()
                                    .attributeName("api_id")
                                    .keyType(KeyType.HASH)
                                    .build(),
                            KeySchemaElement.builder()
                                    .attributeName("checked_at")
                                    .keyType(KeyType.RANGE)
                                    .build())
                    .attributeDefinitions(
                            AttributeDefinition.builder()
                                    .attributeName("api_id")
                                    .attributeType(ScalarAttributeType.S)
                                    .build(),
                            AttributeDefinition.builder()
                                    .attributeName("checked_at")
                                    .attributeType(ScalarAttributeType.S)
                                    .build())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
        } catch (ResourceInUseException ignored) {
        }
    }
}
