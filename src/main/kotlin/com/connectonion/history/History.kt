package com.connectonion.history

import com.connectonion.llm.MessageRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Manages behavior tracking and persistence for agents
 * @param agentName Name of the agent to track
 */
class History(private val agentName: String) {
    private val historyDir = File(System.getProperty("user.home"), ".connectonion/agents/$agentName")
    private val behaviorFile = File(historyDir, "behavior.json")
    private val behaviors = mutableListOf<BehaviorEntry>()
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    init {
        historyDir.mkdirs()
        loadExistingHistory()
    }
    
    /**
     * Record a message in the conversation
     */
    fun recordMessage(role: MessageRole, content: String) {
        behaviors.add(
            BehaviorEntry(
                id = UUID.randomUUID().toString(),
                timestamp = Instant.now().toString(),
                type = BehaviorType.MESSAGE,
                role = role.name,
                content = content
            )
        )
    }
    
    /**
     * Record a tool call execution
     */
    fun recordToolCall(
        callId: String,
        toolName: String,
        parameters: Map<String, Any?>,
        success: Boolean,
        result: String?
    ) {
        behaviors.add(
            BehaviorEntry(
                id = callId,
                timestamp = Instant.now().toString(),
                type = BehaviorType.TOOL_CALL,
                toolName = toolName,
                parameters = parameters.mapValues { it.value.toString() },
                success = success,
                result = result
            )
        )
    }
    
    /**
     * Save current behavior history to disk
     */
    fun save() {
        try {
            val historyData = BehaviorHistory(
                agentName = agentName,
                entries = behaviors.toList()
            )
            behaviorFile.writeText(json.encodeToString(historyData))
            logger.debug { "Saved ${behaviors.size} behavior entries for agent '$agentName'" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save behavior history" }
        }
    }
    
    /**
     * Load existing history from disk
     */
    private fun loadExistingHistory() {
        if (!behaviorFile.exists()) return
        
        try {
            val content = behaviorFile.readText()
            val history = json.decodeFromString<BehaviorHistory>(content)
            behaviors.addAll(history.entries)
            logger.debug { "Loaded ${history.entries.size} existing behavior entries" }
        } catch (e: Exception) {
            logger.warn { "Could not load existing history: ${e.message}" }
        }
    }
    
    /**
     * Get all behavior entries
     */
    fun getBehaviors(): List<BehaviorEntry> = behaviors.toList()
    
    /**
     * Clear all behavior history
     */
    fun clear() {
        behaviors.clear()
        if (behaviorFile.exists()) {
            behaviorFile.delete()
        }
    }
}

/**
 * Types of behavior entries
 */
@Serializable
enum class BehaviorType {
    MESSAGE,
    TOOL_CALL
}

/**
 * Single behavior entry
 */
@Serializable
data class BehaviorEntry(
    val id: String,
    val timestamp: String,
    val type: BehaviorType,
    val role: String? = null,
    val content: String? = null,
    val toolName: String? = null,
    val parameters: Map<String, String>? = null,
    val success: Boolean? = null,
    val result: String? = null
)

/**
 * Complete behavior history
 */
@Serializable
data class BehaviorHistory(
    val agentName: String,
    val entries: List<BehaviorEntry>
)