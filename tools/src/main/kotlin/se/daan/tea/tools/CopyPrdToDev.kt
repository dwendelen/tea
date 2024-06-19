package se.daan.tea.tools

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.ScanRequest

fun main() {
    val dynamoAsyncClient = DynamoDbAsyncClient
        .builder()
        .region(Region.EU_CENTRAL_1)
        .build()

    val prdItems = scan(dynamoAsyncClient, "tea-prd")
    val devItems = scan(dynamoAsyncClient, "tea-dev")

    println("dev: ${devItems.size}")
    println("prd: ${prdItems.size}")

    zip(devItems, prdItems)
        .forEach { (k, v) ->
            val (d, p) = v
            if(d != null && p == null) {
                println("Deleting ${k.first} ${k.second}")
                val get = dynamoAsyncClient.deleteItem(
                    DeleteItemRequest.builder()
                        .tableName("tea-dev")
                        .key(mapOf("pk" to k.first, "sk" to k.second))
                        .build()
                ).get()
            } else if(p != null && p != d) {
                println("Upserting ${k.first} ${k.second}")
                val get = dynamoAsyncClient.putItem(
                    PutItemRequest.builder()
                        .tableName("tea-dev")
                        .item(p)
                        .build()
                ).get()
            }
        }
}

private fun scan(dynamoAsyncClient: DynamoDbAsyncClient, table: String): Map<Pair<AttributeValue, AttributeValue>, Map<String, AttributeValue>> {
    val items = mutableListOf<Map<String, AttributeValue>>()
    var lastKeyEvaluated: Map<String, AttributeValue>? = null
    do {
        val result = dynamoAsyncClient.scan(
            ScanRequest.builder()
                .tableName(table)
                .exclusiveStartKey(lastKeyEvaluated)
                .build()
        ).get()
        items.addAll(result.items())
        lastKeyEvaluated = result.lastEvaluatedKey()
    } while(lastKeyEvaluated?.isNotEmpty() == true)

    return items
        .associateBy { (it["pk"]!! to it["sk"]!!) }
}

private fun <K, A, B> zip(map1: Map<K, A>, map2: Map<K, B>): Map<K, Pair<A?, B?>> {
    return map1.keys.union(map2.keys)
        .associateWith { (map1[it] to map2[it]) }
}