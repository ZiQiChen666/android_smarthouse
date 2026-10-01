package com.example.smarthouse.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * OneNET 云平台 HTTP API 客户端。
 *
 * 认证方式：鉴权 Token（2018-10-31 版本）
 * 文档：https://open.iot.10086.cn/doc/v5/develop/detail/604
 *
 * 物模型接口：
 *  - GET  /thingmodel/query-device-property   查询设备属性
 *  - POST /thingmodel/set-device-property     设置设备属性（下发指令）
 *
 * 为了适应 1 秒级轮询，这里复用了 OkHttp 连接池与长连接，并对临时性错误做了重试。
 */
class OneNetClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // 复用长连接，减少 1s 轮询下的 TLS 握手与端口占用
        .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
        .build()

    companion object {
        private const val HOST = "https://iot-api.heclouds.com"
        private const val TOKEN_VERSION = "2018-10-31"
        private const val DEFAULT_EXPIRE = 3600L * 24 * 30 // 30 天
        private const val MAX_RETRY = 3
        private const val RETRY_DELAY_MS = 250L
    }

    // ------------------------------------------------------------------
    // Token 生成
    // ------------------------------------------------------------------

    /**
     * 生成 OneNET 鉴权 Token。
     * @param method 支持 "sha1" / "md5" / "sha256"；使用 "sha1" 表示该 token 可用于任意 method 的请求
     */
    fun generateToken(
        productId: String,
        deviceName: String,
        deviceKey: String,
        method: String = "sha1",
        expireSeconds: Long = DEFAULT_EXPIRE
    ): String {
        val et = (System.currentTimeMillis() / 1000L) + expireSeconds
        val res = "products/$productId/devices/$deviceName"
        val stringForSignature = "$et\n$method\n$res\n$TOKEN_VERSION"

        // 设备密钥是 base64 编码的，先解码得到真正的 key
        val decodedKey = Base64.getDecoder().decode(deviceKey)

        val sign = hmacSha1Base64(decodedKey, stringForSignature)

        return "version=$TOKEN_VERSION&res=$res&et=$et&method=$method&sign=$sign"
    }

    private fun hmacSha1Base64(key: ByteArray, data: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val raw = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(raw)
    }

    // ------------------------------------------------------------------
    // 查询属性 (GET)
    // ------------------------------------------------------------------

    /**
     * 查询设备属性。
     * @return identifier -> 值字符串
     */
    fun queryProperties(
        productId: String,
        deviceName: String,
        deviceKey: String
    ): Map<String, String> {
        val token = generateToken(productId, deviceName, deviceKey)
        val url = "$HOST/thingmodel/query-device-property?product_id=$productId&device_name=$deviceName"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", token)
            .get()
            .build()

        return executeWithRetry(request) { body -> parsePropertyData(body) }
    }

    /**
     * 解析 query-device-property 返回：
     * {"code":0,"msg":"succ","data":[{"identifier":"temperature","value":"25.3",...}]}
     */
    private fun parsePropertyData(body: String): Map<String, String> {
        val root = JSONObject(body)
        val code = root.optInt("code", -1)
        if (code != 0) {
            throw OneNetException("云端返回错误 code=$code msg=${root.optString("msg")}")
        }
        val result = LinkedHashMap<String, String>()
        val data = root.optJSONArray("data") ?: return result
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val identifier = item.optString("identifier")
            if (identifier.isEmpty()) continue
            val value = item.opt("value")
            result[identifier] = valueToString(value)
        }
        return result
    }

    private fun valueToString(value: Any?): String = when (value) {
        null -> ""
        is Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        else -> value.toString()
    }

    // ------------------------------------------------------------------
    // 设置属性 / 下发指令 (POST)
    // ------------------------------------------------------------------

    /**
     * 下发属性值到设备。
     * @param params identifier -> 值（数值型请传 Number，字符串请传 String）
     */
    fun setProperties(
        productId: String,
        deviceName: String,
        deviceKey: String,
        params: Map<String, Any>
    ): Boolean {
        val token = generateToken(productId, deviceName, deviceKey)

        val payload = JSONObject().apply {
            put("product_id", productId)
            put("device_name", deviceName)
            put("params", JSONObject(params))
        }

        val request = Request.Builder()
            .url("$HOST/thingmodel/set-device-property")
            .header("Authorization", token)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeWithRetry(request) { body ->
            val root = JSONObject(body)
            val code = root.optInt("code", -1)
            if (code != 0) {
                throw OneNetException("下发失败 code=$code msg=${root.optString("msg")}")
            }
            true
        }
    }

    // ------------------------------------------------------------------
    // 带回退重试的请求执行
    // ------------------------------------------------------------------

    private fun <T> executeWithRetry(request: Request, parse: (String) -> T): T {
        var lastError: Exception? = null
        for (attempt in 1..MAX_RETRY) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()

                    // 5xx / 429 属于临时性错误，重试
                    if (response.code >= 500 || response.code == 429) {
                        lastError = OneNetException("服务繁忙 HTTP ${response.code}")
                        return@use
                    }
                    if (!response.isSuccessful) {
                        // 4xx 一般是请求本身的问题，直接抛出不再重试
                        throw OneNetException("请求失败 HTTP ${response.code}: $body")
                    }
                    return parse(body)
                }
            } catch (e: OneNetException) {
                // 业务错误码（非网络）不再重试
                if (e.message?.startsWith("请求失败") == true) throw e
                lastError = e
            } catch (e: IOException) {
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }

            if (attempt < MAX_RETRY) {
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt)
                } catch (_: InterruptedException) {
                }
            }
        }
        throw lastError ?: OneNetException("请求失败")
    }
}

class OneNetException(message: String) : Exception(message)
