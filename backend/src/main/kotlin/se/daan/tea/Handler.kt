package se.daan.tea

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import se.daan.tea.api.VersionedEntity

import software.amazon.awssdk.services.dynamodb.DynamoDbClient

class Handler : RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private val repository: VersionRepository

    init {
        val tableName = System.getenv("TEA_TABLE_NAME")!!
        val dynamoClient = DynamoDbClient.create()
        repository = VersionRepository(dynamoClient, tableName)
    }

    override fun handleRequest(input: APIGatewayV2HTTPEvent, context: Context): APIGatewayV2HTTPResponse {
        return when (input.routeKey) {
            "POST /ping" ->
                APIGatewayV2HTTPResponse(204, null, null, null, null, false)
            "GET /stream" -> {
                val start = input.queryStringParameters?.let{ it["start"]}?.toInt() ?: 0
                val stream = repository.fetchAll(start)
                APIGatewayV2HTTPResponse(
                    200,
                    mapOf("Content-Type" to "application/json"),
                    null,
                    null,
                    Json.encodeToString(stream),
                    false
                )
            }
            "POST /stream" -> {
                if(input.isBase64Encoded) {
                    throw UnsupportedOperationException()
                }
                val entity = Json.decodeFromString<VersionedEntity>(input.body)
                repository.append(entity)
                APIGatewayV2HTTPResponse(204, null, null, null, null, false)
            }
            else -> throw IllegalArgumentException()
        }
    }
}