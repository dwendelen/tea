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
                "sk" to AttributeValue.fromS("stream-${sortableInt(versionedEntity.version)}"),
                "t" to AttributeValue.fromS("Flavour"),
                "id" to AttributeValue.fromN(versionedEntity.id.toString()),
                "n" to AttributeValue.fromS(versionedEntity.name)
            )

            is Product -> mapOf(
                "pk" to AttributeValue.fromS("tea"),
                "sk" to AttributeValue.fromS("stream-${sortableInt(versionedEntity.version)}"),
                "t" to AttributeValue.fromS("Product"),
                "id" to AttributeValue.fromN(versionedEntity.id.toString()),
                "n" to AttributeValue.fromS(versionedEntity.name),
                "fi" to AttributeValue.fromN(versionedEntity.flavourId.toString()),
                "fv" to AttributeValue.fromN(versionedEntity.flavourVersion.toString()),
                "b" to AttributeValue.fromN(versionedEntity.boxSize.toString()),
                "d" to AttributeValue.fromBool(versionedEntity.deprecated)
            )

            is Measurement -> mapOf(
                "pk" to AttributeValue.fromS("tea"),
                "sk" to AttributeValue.fromS("stream-${sortableInt(versionedEntity.version)}"),
                "t" to AttributeValue.fromS("Measurement"),
                "id" to AttributeValue.fromN(versionedEntity.id.toString()),
                "d" to AttributeValue.fromS(versionedEntity.date.toString()),
                "m" to AttributeValue.fromL(versionedEntity.measurements
                    .map { pm ->
                        AttributeValue.fromM(
                            mapOf(
                                "pi" to AttributeValue.fromN(pm.productId.toString()),
                                "pv" to AttributeValue.fromN(pm.productVersion.toString()),
                                "t" to pm.tray?.let { AttributeValue.fromN(it.toString()) },
                                "b" to pm.boxes?.let { AttributeValue.fromN(it.toString()) },
                                "l" to pm.loose?.let { AttributeValue.fromN(it.toString()) },
                            ).filterValues { it != null }
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

    private fun sortableInt(int: Int): String {
        val asString = int.toString()
        val prefix = ('a'.code + asString.length).toChar()
        return prefix + asString
    }

    fun fetchLastVersion(): Int? {
        return client.query(
            QueryRequest.builder()
                .tableName(table)
                .keyConditionExpression("pk = :pk AND begins_with(sk, :skprefix)")
                .expressionAttributeValues(
                    mapOf(
                        ":pk" to AttributeValue.fromS("tea"),
                        ":skprefix" to AttributeValue.fromS("stream-"),
                    )
                )
                .scanIndexForward(false)
                .limit(1)
                // We want consistent because if events are coming in quickly, it must not fail
                .consistentRead(true)
                .build()
        ).items()
            .map {
                val pieces = it.string("sk").split("-")
                val version = pieces[1].drop(1).toInt()
                version
            }
            .firstOrNull()
    }

    fun fetchAll(start: Int): List<VersionedEntity> {
        return client.queryPaginator(
            QueryRequest.builder()
                .tableName(table)
                .keyConditionExpression("pk = :pk AND sk >= :sk")
                .expressionAttributeValues(
                    mapOf(
                        ":pk" to AttributeValue.fromS("tea"),
                        ":sk" to AttributeValue.fromS("stream-${sortableInt(start)}")
                    )
                )
                .build()
        ).items()
            .map {
                val pieces = it.string("sk").split("-")
                if (pieces.size == 2 && pieces[0] == "stream") {
                    val id = it.int("id")!!
                    val version = pieces[1].drop(1).toInt()
                    when (it.string("t")) {
                        "Flavour" -> {
                            Flavour(id, version, it.string("n"))
                        }

                        "Product" -> {
                            Product(id, version, it.string("n"), it.int("fi")!!, it.int("fv")!!, it.int("b")!!, it.bool("d"))
                        }

                        "Measurement" -> {
                            Measurement(id, version, fromString(it.string("d")), it.list("m") { pm ->
                                ProductMeasurement(pm.int("pi")!!, pm.int("pv")!!, pm.int("t")!!, pm.int("b")!!, pm.int("l")!!)
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

private fun Map<String, AttributeValue>.bool(key: String): Boolean {
    return this[key]?.bool()!!
}

private fun Map<String, AttributeValue>.int(key: String): Int? {
    return this[key]?.n()?.toInt()
}

private fun Map<String, AttributeValue>.string(key: String): String {
    return this[key]?.s()!!
}

private fun <T> Map<String, AttributeValue>.list(key: String, fn: (Map<String, AttributeValue>) -> T): List<T> {
    return this[key]?.l()!!
        .map { fn(it.m()) }
}
