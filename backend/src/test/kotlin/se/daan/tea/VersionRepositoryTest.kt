package se.daan.tea

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import se.daan.tea.api.*
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.net.URI
import kotlin.test.Test


@Testcontainers
class VersionRepositoryTest {
    companion object {
        @Container
        val dynamoDbContainer: GenericContainer<*> = GenericContainer(DockerImageName.parse("amazon/dynamodb-local"))
            .withExposedPorts(8000)
    }

    private lateinit var versionRepository: VersionRepository

    @BeforeEach
    fun setUp() {
        val client: DynamoDbClient = DynamoDbClient.builder()
            .endpointOverride(URI.create("http://${dynamoDbContainer.host}:${dynamoDbContainer.getMappedPort(8000)}"))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("A", "S")))
            .build()

        val listTables = client.listTables()
        if(listTables.tableNames().contains("tea")) {
            client.deleteTable(DeleteTableRequest.builder().tableName("tea").build())
        }
        client.createTable(CreateTableRequest.builder()
            .tableName("tea")
            .attributeDefinitions(
                AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build()
            )
            .keySchema(
                KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build()
            )
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build())

        versionRepository = VersionRepository(
            client,
            "tea"
        )
    }

    @Test
    fun startEmpty() {
        val fetchLastVersion = versionRepository.fetchLastVersion()
        val fetchAll = versionRepository.fetchAll(0)

        assertThat(fetchLastVersion).isNull()
        assertThat(fetchAll).isEmpty()
    }

    @Test
    fun addingAndReadingOneEvent() {
        versionRepository.append(Flavour(0, 0, ""))

        val fetchLastVersion = versionRepository.fetchLastVersion()
        val fetched = versionRepository.fetchAll(0)

        assertThat(fetchLastVersion).isEqualTo(0)
        assertThat(fetched).hasSize(1)
    }

    @Test
    fun streamInCorrectOrder() {
        versionRepository.append(Flavour(0, 0, "0"))
        versionRepository.append(Flavour(1, 1, "1"))
        versionRepository.append(Flavour(2, 2, "2"))
        versionRepository.append(Flavour(3, 3, "3"))

        val fetchLastVersion = versionRepository.fetchLastVersion()
        val fetchAll = versionRepository.fetchAll(0)

        assertThat(fetchLastVersion).isEqualTo(3)
        assertThat(fetchAll.map { it.version }).containsExactly(0, 1, 2, 3)
    }

    @Test
    fun fetchWithOffset() {
        versionRepository.append(Flavour(0, 0, "0"))
        versionRepository.append(Flavour(1, 1, "1"))
        versionRepository.append(Flavour(2, 2, "2"))
        versionRepository.append(Flavour(3, 3, "3"))

        val fetchAll = versionRepository.fetchAll(2)

        assertThat(fetchAll.map { it.version }).containsExactly(2, 3)
    }

    @Test
    fun doubleAppend() {
        versionRepository.append(Flavour(0, 0, "0"))
        assertThatThrownBy {
            versionRepository.append(Flavour(0, 0, "0"))
        }
    }

    @Test
    fun flavourMapping() {
        writeRead(Flavour(8, 6, "bla"))
    }

    @Test
    fun productMapping() {
        writeRead(Product(8, 6, "bla", 89, 1, 5, true))
    }

    @Test
    fun measurementMapping_nonNull() {
        writeRead(Measurement(8, 4, fromString("2029-08-15T19:19"), listOf(
            ProductMeasurement(5, 6, 9, 81, 2)
        )))
    }

    @Test
    fun measurementMapping_nullMeasurement() {
        writeRead(Measurement(8, 4, fromString("2029-08-15T19:19"), listOf(
            ProductMeasurement(5, 6, null, null, null)
        )))
    }

    @Test
    fun measurementMapping_multipleMeasurements() {
        writeRead(Measurement(11, 12, fromString("2029-08-15T19:19"), listOf(
            ProductMeasurement(1, 2, 3, 4, 5),
            ProductMeasurement(6, 7, 8, 9, 10)
        )))
    }

    private fun writeRead(expected: VersionedEntity) {
        versionRepository.append(expected)
        val actual = versionRepository.fetchAll(0).first()

        assertThat(actual).isEqualTo(expected)
    }
}