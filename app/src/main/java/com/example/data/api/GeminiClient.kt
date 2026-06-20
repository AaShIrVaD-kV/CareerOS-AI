package com.example.data.api

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ==========================================
// GEMINI API REQUEST BODIES (MOSHI COMPATIBLE)
// ==========================================

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null,
    val tools: List<GeminiTool>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiTool(
    val googleSearch: GoogleSearchTool? = null
)

@JsonClass(generateAdapter = true)
class GoogleSearchTool

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Double? = 0.4,
    val responseMimeType: String? = null,
    val thinkingConfig: ThinkingConfig? = null
)

@JsonClass(generateAdapter = true)
data class ThinkingConfig(
    val thinkingLevel: String? = null,
    val thinkingBudget: Int? = null
)

// ==========================================
// GEMINI API RESPONSE BODIES
// ==========================================

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

// ==========================================
// BUSINESS MODELS CARRIED IN SYSTEM JSON PANELS
// ==========================================

@JsonClass(generateAdapter = true)
data class MockInterviewQuestion(
    val question: String,
    val type: String,
    val modelAnswer: String,
    val suggestion: String
)

@JsonClass(generateAdapter = true)
data class StructuredLearningPlan(
    val days7: List<String>,
    val days30: List<String>,
    val days60: List<String>,
    val days90: List<String>
)

@JsonClass(generateAdapter = true)
data class StructuredAnalysisResult(
    val jobTitle: String,
    val company: String,
    val location: String,
    val matchScore: Int,
    val summary: String,
    val matchingSkills: List<String>,
    val missingSkills: List<String>,
    val atsKeywords: List<String>,
    val resumeImprovements: List<String>,
    val customResumeDraft: String,
    val coverLetterDraft: String,
    val interviewQuestions: List<MockInterviewQuestion>,
    val recommendedLearning: StructuredLearningPlan
)

@JsonClass(generateAdapter = true)
data class StructuredResumeAnalysis(
    val matchScore: Int,
    val skillsMatchScore: Int,
    val keywordMatchScore: Int,
    val educationMatch: String,
    val experienceMatch: String,
    val missingSkills: List<String>,
    val missingKeywords: List<String>,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val interviewReadinessScore: Int
)

@JsonClass(generateAdapter = true)
data class RewriteSection(
    val sectionName: String,
    val originalText: String,
    val rewrittenText: String,
    val explanation: String
)

@JsonClass(generateAdapter = true)
data class RecommendedSkillItem(
    val skillName: String,
    val occurrenceRatePercent: Int,
    val reason: String
)

@JsonClass(generateAdapter = true)
data class StructuredResumeRewrite(
    val originalVsRewrittenComparison: String,
    val atsImprovementEstimatePercent: Int,
    val keywordImprovements: List<String>,
    val sectionBySectionRewrite: List<RewriteSection>,
    val recommendedFutureSkills: List<RecommendedSkillItem>,
    val finalResumeVersionText: String
)

// ==========================================
// RETROFIT CLIENT
// ==========================================

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContentDynamic(
        @retrofit2.http.Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    // Parse the inner JSON response generated by Gemini
    fun parseAnalysisResult(jsonString: StringOrNull): StructuredAnalysisResult? {
        if (jsonString.isNullOrEmpty()) return null
        return try {
            val jsonAdapter = moshi.adapter(StructuredAnalysisResult::class.java)
            // Trim markdown fences if Gemini added them despite rules
            val cleaned = jsonString.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            jsonAdapter.fromJson(cleaned)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parseResumeAnalysis(jsonString: StringOrNull): StructuredResumeAnalysis? {
        if (jsonString.isNullOrEmpty()) return null
        return try {
            val jsonAdapter = moshi.adapter(StructuredResumeAnalysis::class.java)
            val cleaned = jsonString.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            jsonAdapter.fromJson(cleaned)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parseResumeRewrite(jsonString: StringOrNull): StructuredResumeRewrite? {
        if (jsonString.isNullOrEmpty()) return null
        return try {
            val jsonAdapter = moshi.adapter(StructuredResumeRewrite::class.java)
            val cleaned = jsonString.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            jsonAdapter.fromJson(cleaned)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

private typealias StringOrNull = String?
