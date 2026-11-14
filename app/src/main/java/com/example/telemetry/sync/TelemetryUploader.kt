package com.example.telemetry.sync

import com.example.telemetry.data.TelemetryRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

sealed class UploadOutcome {
    object Success : UploadOutcome()
    object Retryable : UploadOutcome()
    object Permanent : UploadOutcome()
}

class TelemetryUploader(private val endpoint: String) {

    init {
        require(endpoint.startsWith("https://")) { "Telemetry endpoint must use HTTPS" }
    }

    fun upload(records: List<TelemetryRecord>): UploadOutcome {
        val body = buildPayload(records).toString()
        return try {
            val conn = (URL(endpoint).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                when (conn.responseCode) {
                    in 200..299 -> UploadOutcome.Success
                    408, 429, in 500..599 -> UploadOutcome.Retryable
                    else -> UploadOutcome.Permanent
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            UploadOutcome.Retryable
        }
    }

    private fun buildPayload(records: List<TelemetryRecord>): JSONObject {
        val array = JSONArray()
        records.forEach { r ->
            array.put(JSONObject().apply {
                put("id", r.id)
                put("timestampMs", r.timestampMs)
                put("eventType", r.eventType)
                r.latitude?.let { put("latitude", it) }
                r.longitude?.let { put("longitude", it) }
                r.accuracyMeters?.let { put("accuracyMeters", it.toDouble()) }
                put("payload", JSONObject(r.payloadJson))
            })
        }
        return JSONObject().put("records", array)
    }
}