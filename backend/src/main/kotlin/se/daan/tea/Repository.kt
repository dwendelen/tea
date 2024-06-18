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
                "s" to AttributeValue.fromS(versionedEntity.status.name),
                "sn" to versionedEntity.supplierInfo?.let {
                    AttributeValue.fromS(it.name)
                },
                "su" to versionedEntity.supplierInfo?.let {
                    it.url?.let { u -> AttributeValue.fromS(u) }
                },
                "sc" to versionedEntity.supplierInfo?.let {
                    it.code?.let { c -> AttributeValue.fromS(c) }
                }
            ).filterValues { it != null }

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
                                "t" to AttributeValue.fromN(pm.tray.toString()),
                                "b" to AttributeValue.fromN(pm.boxes.toString()),
                                "l" to AttributeValue.fromN(pm.loose.toString()),
                            ).filterValues { it != null }
                        )
                    }
                )
            )
            is Delta -> mapOf(
                "pk" to AttributeValue.fromS("tea"),
                "sk" to AttributeValue.fromS("stream-${sortableInt(versionedEntity.version)}"),
                "t" to AttributeValue.fromS("Delta"),
                "id" to AttributeValue.fromN(versionedEntity.id.toString()),
                "d" to AttributeValue.fromS(versionedEntity.date.toString()),
                "pd" to AttributeValue.fromL(versionedEntity.deltas
                    .map { pm ->
                        AttributeValue.fromM(
                            mapOf(
                                "pi" to AttributeValue.fromN(pm.productId.toString()),
                                "pv" to AttributeValue.fromN(pm.productVersion.toString()),
                                "t" to AttributeValue.fromN(pm.tray.toString()),
                                "b" to AttributeValue.fromN(pm.boxes.toString()),
                                "l" to AttributeValue.fromN(pm.loose.toString()),
                            ).filterValues { it != null }
                        )
                    }
                )
            )
            is Tombstone -> mapOf(
                "pk" to AttributeValue.fromS("tea"),
                "sk" to AttributeValue.fromS("stream-${sortableInt(versionedEntity.version)}"),
                "t" to AttributeValue.fromS("Tombstone"),
                "id" to AttributeValue.fromN(versionedEntity.id.toString()),
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
                            Product(
                                id,
                                version,
                                it.string("n"),
                                it.int("fi")!!,
                                it.int("fv")!!,
                                it.int("b")!!,
                                ProductStatus.valueOf(it.string("s")),
                                it.nstring("sn")?.let { sn -> SupplierInfo(sn, it.nstring("su"), it.nstring("sc")) })
                        }

                        "Measurement" -> {
                            Measurement(id, version, fromString(it.string("d")), it.list("m") { pm ->
                                ProductMeasurement(pm.int("pi")!!, pm.int("pv")!!, pm.int("t")!!, pm.int("b")!!, pm.int("l")!!)
                            })
                        }

                        "Delta" -> {
                            Delta(id, version, fromString(it.string("d")), it.list("pd") { pd ->
                                ProductDelta(pd.int("pi")!!, pd.int("pv")!!, pd.int("t")!!, pd.int("b")!!, pd.int("l")!!)
                            })
                        }

                        "Tombstone" -> {
                            Tombstone(id, version)
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
    return nstring(key)!!
}

private fun Map<String, AttributeValue>.nstring(key: String): String? {
    return this[key]?.s()
}

private fun <T> Map<String, AttributeValue>.list(key: String, fn: (Map<String, AttributeValue>) -> T): List<T> {
    return this[key]?.l()!!
        .map { fn(it.m()) }
}
