package com.nao200128nao.BitSharesAsset

import com.neovisionaries.ws.client.WebSocketFactory
import com.squareup.kotlinpoet.*
import cy.agorise.graphenej.Asset
import cy.agorise.graphenej.api.ListAssets
import cy.agorise.graphenej.interfaces.WitnessResponseListener
import cy.agorise.graphenej.models.BaseResponse
import cy.agorise.graphenej.models.WitnessResponse
import java.io.File
import java.lang.System.err

fun main(args: Array<String>) {
    val ws = WebSocketFactory().createSocket(findAvailableBitSharesNode())
    val assetClass = Asset::class.asClassName()
    val pkgName = ""
    val clsName = "BitSharesAsset"
    val localFile = File("./build/bsa.kt")
    val file = FileSpec.builder(pkgName, clsName)
    val type = TypeSpec.classBuilder(clsName)
            .addModifiers(KModifier.SEALED)
            .superclass(assetClass)
            .primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter("assetId", String::class.asClassName())
                    .addParameter("symbol", String::class.asClassName())
                    .addParameter("decimals", Int::class.asClassName())
                    .build())
            .addSuperclassConstructorParameter("assetId")
            .addSuperclassConstructorParameter("symbol")
            .addSuperclassConstructorParameter("decimals")
    ws.addListener(ListAssets("", -1, true, object : WitnessResponseListener {
        override fun onSuccess(p0: WitnessResponse<*>) {
            val assets = p0.result as List<Asset>
            assets
                    .sortedBy { it.objectId.split("\\.".toRegex()).last().toInt() }
                    .distinctBy { it.objectId.split("\\.".toRegex()).last().toInt() }
                    .forEach {
                val memberName = it.symbol.symbolToFieldName()
                err.println("Adding ${it.symbol} (${it.objectId}) as $memberName")
                type.addType(TypeSpec.objectBuilder(memberName)
                        .superclass(ClassName(pkgName, clsName))
                        .addSuperclassConstructorParameter("%S", it.objectId)
                        .addSuperclassConstructorParameter("%S", it.symbol)
                        .addSuperclassConstructorParameter("%L", it.precision)
                        .build())
            }
            err.println("===".repeat(10))
            file.addType(type.build())
            StringBuilder().also {
                file.build().writeTo(it)
                localFile.writeText(it.toString())
                println(it)
            }
            System.exit(0)
        }

        override fun onError(p0: BaseResponse.Error?) {
            err.println(p0?.message)
            System.exit(1)
        }
    }))
    ws.connect()
}

/**
 * Transforms symbol name to the following:
 * BTC -> BTC
 * BRIDGE.ZNY -> BridgeZNY
 * EXAMPLE.YAY.TEST -> ExampleYayTEST
 * 0XX -> _0XX
 * */
fun String.symbolToFieldName(): String = when {
    !contains("[^a-zA-Z0-9]".toRegex()) -> toUpperCase()
    else -> {
        val segments = toLowerCase().split("[^a-z0-9]+".toRegex())
        (segments.dropLast(1)
                .map { it.replace("^.".toRegex()) { it.value.toUpperCase() } }
                + segments.last().toUpperCase()).joinToString("")
    }
}.run {
    when {
        matches("^[0-9]".toRegex()) -> "_$this"
        else -> this
    }
}

val bitSharesFullNodes = listOf(
        "wss://proj.tokyo:8090",
        "wss://bts.ai.la/ws",
        "wss://openledger.hk/ws",
        "wss://bitshares.openledger.info/ws",
        "wss://bitshares-api.wancloud.io/ws",
        "wss://bit.btsabc.org/ws"
)


fun findAvailableBitSharesNode(): String = bitSharesFullNodes.first {
    try {
        WebSocketFactory().createSocket(it).also {
            it.connect()
            it.disconnect()
        }
        true
    } catch (e: Throwable) {
        false
    }
}
