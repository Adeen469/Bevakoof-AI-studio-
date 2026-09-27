package com.example.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Using gemini-3.5-flash as mandated by gemini-api skill for basic text/task reasoning
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    fun isApiKeyConfigured(): Boolean {
        return try {
            BuildConfig.GEMINI_API_KEY.isNotBlank() && !BuildConfig.GEMINI_API_KEY.contains("MY_GEMINI_API_KEY")
        } catch (_: Exception) {
            false
        }
    }

    suspend fun generateContent(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isApiKeyConfigured()) {
            return@withContext Result.failure(IllegalStateException("GEMINI_API_KEY not configured in Secrets panel."))
        }

        try {
            val url = "$BASE_URL/$MODEL_NAME:generateContent?key=${BuildConfig.GEMINI_API_KEY}"

            val requestBodyJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "You are Bewakoof, a loyal, highly capable, and secure personal AI voice agent for your owner ('Malik'). Provide concise, clear, and direct answers in natural English or bilingual Hinglish. User request: $prompt")
                            })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)
            }

            val body = requestBodyJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gemini API error ${response.code}: $responseBody"))
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text")
                    return@withContext Result.success(text.trim())
                }
            }

            Result.failure(Exception("Empty candidate returned by Gemini API"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
