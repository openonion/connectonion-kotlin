package com.connectonion.llm

import com.connectonion.core.FunctionSchema
import com.connectonion.core.ToolCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Abstract interface for Language Model implementations
 */
abstract class LLM {
    /**
     * Send a completion request to the LLM
     * @param messages List of conversation messages
     * @param tools Optional list of available tools
     * @param temperature Sampling temperature (0-2)
     * @return LLM response
     */
    abstract suspend fun complete(
        messages: List<Message>,
        tools: List<FunctionSchema>? = null,
        temperature: Double = 0.7
    ): LLMResponse
}

/**
 * Message in a conversation
 */
@Serializable
data class Message(
    val role: MessageRole,
    val content: String? = null,
    val name: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null
)

/**
 * Message role enum
 */
@Serializable
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

/**
 * Response from LLM
 */
@Serializable
data class LLMResponse(
    val content: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val finishReason: String? = null,
    val usage: Usage? = null
)

/**
 * Token usage statistics
 */
@Serializable
data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)