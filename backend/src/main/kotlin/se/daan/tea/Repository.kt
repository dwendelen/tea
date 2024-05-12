package se.daan.tea

import se.daan.tea.api.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest

class VersionRepository(
    private val client: DynamoDbClient,
    private val table: String
) {
    fun append(versionedEntity: VersionedEntity) {
        val item = when (versionedEntity) {
            is Flavour -> mapOf(
                "pk" to AttributeValue.fromS("tea"),
                "sk" to AttributeValue.fromS("stream-${versionedEntity.version}"),
                "n" to AttributeValue.fromS(versionedEntity.name)
            )

            is Product -> mapOf(
                "pk" to AttributeValue.fromS("tea"),
                "sk" to AttributeValue.fromS("stream-${versionedEntity.version}"),
                "n" to AttributeValue.fromS(versionedEntity.name),
                "f" to AttributeValue.fromN(versionedEntity.flavour.toString())
            )

            is Measurement -> mapOf(
                "pk" to AttributeValue.fromS("tea"),
                "sk" to AttributeValue.fromS("stream-${versionedEntity.version}"),
                "d" to AttributeValue.fromS(versionedEntity.date),
                "m" to AttributeValue.fromL(versionedEntity.measurements
                    .map { pm ->
                        AttributeValue.fromM(
                            mapOf(
                                "p" to AttributeValue.fromN(pm.product.toString()),
                                "t" to AttributeValue.fromN(pm.tray.toString()),
                                "b" to AttributeValue.fromN(pm.boxes.toString()),
                                "l" to AttributeValue.fromN(pm.loose.toString())
                            )
                        )
                    }
                )
            )
        }
        client.putItem(
            PutItemRequest.builder()
                .tableName(table)
                .item(item)
                .conditionExpression("attribute_not_exists(pk)")
                .build()
        )
    }

    fun fetchAll(start: Int): List<VersionedEntity> {
        return client.queryPaginator(
            QueryRequest.builder()
                .tableName(table)
                .keyConditionExpression("pk = :pk AND sk >= :sk")
                .expressionAttributeValues(
                    mapOf(
                        ":pk" to AttributeValue.fromS("tea"),
                        ":sk" to AttributeValue.fromS("stream-$start")
                    )
                )
                .build()
        ).items()
            .map {
                val pieces = it.string("sk").split("-")
                if (pieces.size == 2 && pieces[0] == "stream") {
                    val id = it.int("id")
                    val version = pieces[1].toInt()
                    when (it.string("t")) {
                        "Flavour" -> {
                            Flavour(id, version, it.string("n"))
                        }

                        "Product" -> {
                            Product(id, version, it.string("n"), it.int("f"))
                        }

                        "Measurement" -> {
                            Measurement(id, version, it.string("d"), it.list("m") { pm ->
                                ProductMeasurement(pm.int("p"), pm.int("t"), pm.int("b"), pm.int("l"))
                            })
                        }

                        else -> throw IllegalStateException()
                    }
                } else {
                    throw IllegalStateException()
                }
            }
    }
}

private fun Map<String, AttributeValue>.int(key: String): Int {
    return this[key]?.n()!!.toInt()
}

private fun Map<String, AttributeValue>.string(key: String): String {
    return this[key]?.s()!!
}

private fun <T> Map<String, AttributeValue>.list(key: String, fn: (Map<String, AttributeValue>) -> T): List<T> {
    return this[key]?.l()!!
        .map { fn(it.m()) }
}
