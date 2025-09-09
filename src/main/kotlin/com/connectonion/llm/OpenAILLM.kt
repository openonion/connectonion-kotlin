package com.connectonion.llm

import com.connectonion.core.FunctionSchema
import com.connectonion.core.ToolCall
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * OpenAI API implementation of LLM interface
 * @param apiKey OpenAI API key
 * @param model Model to use (default: gpt-4)
 * @param baseUrl Base URL for OpenAI API
 * @param maxTokens Maximum tokens in response
 */
class OpenAILLM(
    private val apiKey: String,
    private val model: String = "gpt-4",
    private val baseUrl: String = "https://api.openai.com/v1",
    private val maxTokens: Int? = null
) : LLM() {
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
        }
        defaultRequest {
            header("Authorization", "Bearer $apiKey")
            header("Content-Type", "application/json")
        }
    }
    
    override suspend fun complete(
        messages: List<Message>,
        tools: List<FunctionSchema>?,
        temperature: Double
    ): LLMResponse {
        val request = OpenAIRequest(
            model = model,
            messages = messages.map { it.toOpenAIMessage() },
            tools = tools?.map { it.toOpenAITool() },
            temperature = temperature,
            maxTokens = maxTokens
        )
        
        logger.debug { "Sending request to OpenAI with ${messages.size} messages" }
        
        val response: HttpResponse = client.post("$baseUrl/chat/completions") {
            setBody(request)
        }
        
        val openAIResponse = response.body<OpenAIResponse>()
        val choice = openAIResponse.choices.firstOrNull()
            ?: throw RuntimeException("No response from OpenAI")
        
        return LLMResponse(
            content = choice.message.content,
            toolCalls = choice.message.toolCalls?.map { it.toToolCall() },
            finishReason = choice.finishReason,
            usage = openAIResponse.usage?.let {
                Usage(
                    promptTokens = it.promptTokens,
                    completionTokens = it.completionTokens,
                    totalTokens = it.totalTokens
                )
            }
        )
    }
    
    fun close() {
        client.close()
    }
}

// OpenAI API request/response models

@Serializable
private data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val tools: List<OpenAITool>? = null,
    val temperature: Double,
    val maxTokens: JsonElement? = null
) {
    constructor(
        model: String,
        messages: List<OpenAIMessage>,
        tools: List<OpenAITool>?,
        temperature: Double,
        maxTokens: Int?
    ) : this(
        model = model,
        messages = messages,
        tools = tools,
        temperature = temperature,
        maxTokens = maxTokens?.let { JsonPrimitive(it) }
    )
}

@Serializable
private data class OpenAIMessage(
    val role: String,
    val content: String? = null,
    val name: String? = null,
    val toolCalls: JsonElement? = null,
    val toolCallId: JsonElement? = null
)

@Serializable
private data class OpenAITool(
    val type: String = "function",
    val function: OpenAIFunction
)

@Serializable
private data class OpenAIFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
private data class OpenAIResponse(
    val id: String,
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage? = null
)

@Serializable
private data class OpenAIChoice(
    val index: Int,
    val message: OpenAIResponseMessage,
    val finishReason: String? = null
)

@Serializable
private data class OpenAIResponseMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<OpenAIToolCall>? = null
)

@Serializable
private data class OpenAIToolCall(
    val id: String,
    val type: String,
    val function: OpenAIToolCallFunction
)

@Serializable
private data class OpenAIToolCallFunction(
    val name: String,
    val arguments: String
)

@Serializable
private data class OpenAIUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

// Extension functions for conversion

private fun Message.toOpenAIMessage(): OpenAIMessage {
    return OpenAIMessage(
        role = role.name.lowercase(),
        content = content,
        name = name,
        toolCalls = toolCalls?.let { 
            JsonArray(it.map { toolCall ->
                buildJsonObject {
                    put("id", toolCall.id)
                    put("type", "function")
                    put("function", buildJsonObject {
                        put("name", toolCall.name)
                        put("arguments", toolCall.arguments.toString())
                    })
                }
            })
        },
        toolCallId = toolCallId?.let { JsonPrimitive(it) }
    )
}

private fun FunctionSchema.toOpenAITool(): OpenAITool {
    return OpenAITool(
        function = OpenAIFunction(
            name = name,
            description = description,
            parameters = parameters
        )
    )
}

private fun OpenAIToolCall.toToolCall(): ToolCall {
    return ToolCall(
        id = id,
        name = function.name,
        arguments = Json.parseToJsonElement(function.arguments)
    )
}