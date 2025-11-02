package com.connectonion.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * @purpose Abstract base class for all agent tools - enables agents to perform actions beyond text generation
 * @llm-note
 *   Dependencies: imports from [kotlinx.serialization.*] | imported by [core/Agent.kt, tools/BuiltInTools.kt, examples/*] | tested by [core/ToolTest.kt, tools/BuiltInToolsTest.kt]
 *   Data flow: receives parameters: Map<String, Any?> from Agent.executeToolCall() → validates → executes logic → returns ToolResult{success, output/error}
 *   State/Effects: None in abstract class | implementations may read/write files, call APIs, modify external state
 *   Integration: exposes run() and toFunctionSchema() abstract methods | used by Agent via toolMap lookup | FunctionSchema sent to LLM for function calling
 *   Performance: suspend fun for async I/O | implementations should handle timeouts | Agent executes tools in parallel via coroutineScope
 *   Errors: implementations return ToolResult(false, error=msg) for failures | Agent catches exceptions and converts to ToolResult
 *
 * ## Creating a Custom Tool
 *
 * To create a custom tool, extend this class and implement the required methods:
 *
 * ```kotlin
 * class MyCustomTool : Tool() {
 *     override val name = "my_tool"
 *     override val description = "Does something useful"
 *
 *     override suspend fun run(parameters: Map<String, Any?>): ToolResult {
 *         // Your tool logic here
 *         return ToolResult(success = true, output = "Result")
 *     }
 *
 *     override fun toFunctionSchema(): FunctionSchema {
 *         // Define parameter schema for OpenAI function calling
 *     }
 * }
 * ```
 *
 * @see FunctionTool for a simpler way to create tools from functions
 * @see BuiltInTools for examples of pre-built tools
 */
abstract class Tool {
    /**
     * Unique name identifier for the tool.
     * 
     * This name is used by the LLM to identify which tool to call.
     * Should be lowercase with underscores (e.g., "read_file", "get_weather").
     */
    abstract val name: String
    
    /**
     * Human-readable description of what the tool does.
     * 
     * This description helps the LLM understand when to use this tool.
     * Be clear and specific about the tool's capabilities and limitations.
     * 
     * Example: "Reads the contents of a file from the filesystem. Returns an error if the file doesn't exist."
     */
    abstract val description: String
    
    /**
     * Execute the tool with given parameters.
     * 
     * This method is called by the agent when the LLM requests to use this tool.
     * It should handle all parameter validation and error cases gracefully.
     * 
     * @param parameters Map of parameter names to their values, as provided by the LLM
     * @return ToolResult containing either successful output or error information
     * 
     * ## Implementation Guidelines
     * - Validate all required parameters exist
     * - Handle type conversions safely (parameters come as Any?)
     * - Return descriptive error messages for debugging
     * - Use suspend for async operations (file I/O, network calls, etc.)
     * 
     * ## Example Implementation
     * ```kotlin
     * override suspend fun run(parameters: Map<String, Any?>): ToolResult {
     *     val input = parameters["input"] as? String
     *         ?: return ToolResult(false, error = "Missing 'input' parameter")
     *     
     *     return try {
     *         val result = processInput(input)
     *         ToolResult(true, output = result)
     *     } catch (e: Exception) {
     *         ToolResult(false, error = e.message)
     *     }
     * }
     * ```
     */
    abstract suspend fun run(parameters: Map<String, Any?>): ToolResult
    
    /**
     * Convert this tool to OpenAI function schema format.
     * 
     * This schema tells the LLM what parameters the tool accepts and their types.
     * The schema follows the JSON Schema specification.
     * 
     * @return FunctionSchema containing the tool's name, description, and parameter schema
     * 
     * ## Schema Structure Example
     * ```kotlin
     * FunctionSchema(
     *     name = "read_file",
     *     description = "Read contents of a file",
     *     parameters = buildJsonObject {
     *         put("type", "object")
     *         put("properties", buildJsonObject {
     *             put("path", buildJsonObject {
     *                 put("type", "string")
     *                 put("description", "Path to the file")
     *             })
     *         })
     *         put("required", JsonArray(listOf(JsonPrimitive("path"))))
     *     }
     * )
     * ```
     */
    abstract fun toFunctionSchema(): FunctionSchema
}

/**
 * Result of a tool execution.
 * 
 * Encapsulates the outcome of a tool's run() method, providing either
 * successful output or error information back to the agent.
 * 
 * @property success Whether the tool execution completed successfully
 * @property output The successful result output (null if failed)
 * @property error Error message if execution failed (null if successful)
 * 
 * ## Usage Examples
 * ```kotlin
 * // Successful execution
 * ToolResult(success = true, output = "File contents: Hello World")
 * 
 * // Failed execution
 * ToolResult(success = false, error = "File not found: /path/to/file")
 * ```
 */
@Serializable
data class ToolResult(
    val success: Boolean,
    val output: String? = null,
    val error: String? = null
)

/**
 * OpenAI-compatible function schema.
 * 
 * Defines the structure that OpenAI's function calling feature expects.
 * This schema tells the LLM how to properly call the tool.
 * 
 * @property name The function name (should match Tool.name)
 * @property description What the function does (should match Tool.description)
 * @property parameters JSON Schema defining the function's parameters
 * 
 * ## Parameters Schema Format
 * The parameters should follow JSON Schema specification:
 * - "type": Usually "object" for functions with named parameters
 * - "properties": Object defining each parameter's type and description
 * - "required": Array of required parameter names
 * 
 * @see [OpenAI Function Calling](https://platform.openai.com/docs/guides/function-calling)
 */
@Serializable
data class FunctionSchema(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

/**
 * Function-based tool implementation.
 * 
 * Provides a convenient way to wrap regular Kotlin functions as tools
 * without having to create a full Tool subclass. This is useful for
 * simple functions that don't need complex state management.
 * 
 * ## Example Usage
 * ```kotlin
 * val calculatorTool = FunctionTool(
 *     name = "calculator",
 *     description = "Performs basic arithmetic",
 *     function = { params ->
 *         val a = (params["a"] as Number).toDouble()
 *         val b = (params["b"] as Number).toDouble()
 *         val op = params["operation"] as String
 *         when (op) {
 *             "add" -> a + b
 *             "subtract" -> a - b
 *             else -> throw IllegalArgumentException("Unknown operation")
 *         }
 *     },
 *     parameterSchema = buildJsonObject {
 *         put("type", "object")
 *         put("properties", buildJsonObject {
 *             put("a", buildJsonObject { put("type", "number") })
 *             put("b", buildJsonObject { put("type", "number") })
 *             put("operation", buildJsonObject { 
 *                 put("type", "string")
 *                 put("enum", JsonArray(listOf(
 *                     JsonPrimitive("add"),
 *                     JsonPrimitive("subtract")
 *                 )))
 *             })
 *         })
 *     }
 * )
 * ```
 */
class FunctionTool(
    override val name: String,
    override val description: String,
    private val function: suspend (Map<String, Any?>) -> Any?,
    private val parameterSchema: JsonObject
) : Tool() {
    
    override suspend fun run(parameters: Map<String, Any?>): ToolResult {
        return try {
            // Execute the wrapped function with provided parameters
            val result = function(parameters)
            
            // Convert result to string representation for the LLM
            ToolResult(
                success = true,
                output = result?.toString()
            )
        } catch (e: Exception) {
            // Capture any exceptions and return as error
            ToolResult(
                success = false,
                error = e.message
            )
        }
    }
    
    override fun toFunctionSchema(): FunctionSchema {
        return FunctionSchema(
            name = name,
            description = description,
            parameters = parameterSchema
        )
    }
}

/**
 * Tool call request from LLM.
 * 
 * Represents a request from the language model to execute a specific tool.
 * This is part of OpenAI's function calling mechanism where the LLM
 * can request to use tools during a conversation.
 * 
 * @property id Unique identifier for this tool call (used for response correlation)
 * @property name Name of the tool to execute (must match a registered Tool.name)
 * @property arguments JSON object containing the tool's parameters
 * 
 * ## Flow
 * 1. LLM generates a ToolCall in its response
 * 2. Agent finds the matching tool by name
 * 3. Agent parses arguments and calls tool.run()
 * 4. Agent sends tool result back to LLM with matching id
 * 
 * @see Agent for how tool calls are processed
 */
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JsonElement
)