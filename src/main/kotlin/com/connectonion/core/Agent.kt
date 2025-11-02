package com.connectonion.core

import com.connectonion.history.History
import com.connectonion.llm.LLM
import com.connectonion.llm.Message
import com.connectonion.llm.MessageRole
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * @purpose Main orchestrator for AI agents - implements ReAct loop (Reasoning + Acting) with LLM and tools
 * @llm-note
 *   Dependencies: imports from [history/History.kt, llm/LLM.kt, core/Tool.kt] | imported by [examples/*] | tested by [core/AgentTest.kt]
 *   Data flow: run(prompt) → adds to messages → iterative loop: LLM.complete() → parse toolCalls → executeToolCall() in parallel → add results to messages → repeat until done or maxIterations
 *   State/Effects: mutates messages: MutableList<Message> (conversation context) | calls History.recordMessage(), History.recordToolCall(), History.save() | LLM makes HTTP calls
 *   Integration: exposes run(prompt), clearMessages(), getMessages() | instantiated with (name, llm, tools, systemPrompt, temperature, maxIterations) | used in all examples
 *   Performance: parallel tool execution via coroutineScope + async/awaitAll (line 269-275) | O(1) tool lookup via toolMap (line 101) | max 10 iterations default to prevent infinite loops
 *   Errors: catches exceptions in executeToolCall() → converts to ToolResult(false, error) → sends to LLM for recovery | warns if maxIterations reached
 *
 * A sophisticated AI agent that orchestrates conversations between users and language models,
 * enhanced with tool execution capabilities for performing real-world actions.
 * 
 * ## Core Concept
 * 
 * An Agent acts as an intelligent coordinator that:
 * - Receives user prompts and maintains conversation context
 * - Communicates with Large Language Models (LLMs) to generate responses
 * - Executes tools when the LLM determines they're needed
 * - Manages the iterative loop of reasoning and action until completion
 * 
 * ## Use Cases
 * 
 * **Code Assistant**: An agent with file reading/writing tools to help with programming tasks
 * ```kotlin
 * val codeAgent = Agent(
 *     name = "code_helper",
 *     llm = openAiLLM,
 *     tools = listOf(ReadFileTool(), WriteFileTool(), ExecuteCommandTool()),
 *     systemPrompt = "You are a helpful coding assistant. Always explain your code changes."
 * )
 * ```
 * 
 * **Research Assistant**: An agent with web search and document analysis tools
 * ```kotlin
 * val researchAgent = Agent(
 *     name = "researcher", 
 *     llm = claudeLLM,
 *     tools = listOf(WebSearchTool(), SummarizeTool()),
 *     systemPrompt = "You are a thorough researcher. Always cite your sources.",
 *     temperature = 0.3 // Lower temperature for more focused responses
 * )
 * ```
 * 
 * **Data Analyst**: An agent with database and calculation tools
 * ```kotlin
 * val analystAgent = Agent(
 *     name = "data_analyst",
 *     llm = gpt4LLM,
 *     tools = listOf(DatabaseQueryTool(), ChartGeneratorTool(), CalculatorTool()),
 *     maxIterations = 15 // Allow more iterations for complex analysis
 * )
 * ```
 * 
 * ## Execution Flow
 * 
 * 1. **Input Processing**: User prompt is added to conversation history
 * 2. **LLM Reasoning**: Language model analyzes context and decides next action
 * 3. **Tool Execution**: If tools are needed, they execute in parallel for efficiency
 * 4. **Result Integration**: Tool outputs are fed back to the LLM for further reasoning
 * 5. **Iteration**: Steps 2-4 repeat until the LLM provides a final response
 * 6. **Response Return**: Final answer is returned to the user
 * 
 * ## Thread Safety
 * 
 * Each Agent instance maintains its own conversation state and is not thread-safe.
 * Use separate Agent instances for concurrent conversations.
 * 
 * @param name Unique identifier for this agent instance. Used for logging and history tracking.
 *             Should be descriptive (e.g., "code_helper", "research_bot")
 * @param llm Language model implementation that provides the agent's reasoning capabilities.
 *            Must implement the LLM interface for generating responses and handling tool calls
 * @param tools List of available tools the agent can execute. Tools extend agent capabilities
 *              beyond text generation (e.g., file operations, API calls, calculations).
 *              Empty list means text-only conversations
 * @param systemPrompt Optional system-level instructions that define the agent's personality,
 *                     role, and behavior patterns. This prompt persists throughout the conversation.
 *                     Example: "You are a helpful coding assistant who explains complex concepts simply."
 * @param temperature Sampling temperature for LLM responses (0.0-2.0).
 *                    - 0.0: Deterministic, focused responses
 *                    - 0.7: Balanced creativity and coherence (default)
 *                    - 1.5+: More creative but potentially less coherent
 * @param maxIterations Maximum number of reasoning-action cycles to prevent infinite loops.
 *                      Each iteration includes LLM response + tool execution.
 *                      Typical values: 5-15 depending on task complexity
 */
class Agent(
    val name: String,
    private val llm: LLM,
    private val tools: List<Tool> = emptyList(),
    private val systemPrompt: String? = null,
    private val temperature: Double = 0.7,
    private val maxIterations: Int = 10
) {
    // Create a lookup map for O(1) tool retrieval by name during execution
    private val toolMap = tools.associateBy { it.name }
    
    // Persistent history storage for debugging and conversation replay
    private val history = History(name)
    
    // In-memory conversation context that gets sent to the LLM on each request
    // This maintains the full conversation thread including system prompts,
    // user messages, assistant responses, and tool call results
    private val messages = mutableListOf<Message>()
    
    init {
        // Add system prompt as the first message if provided
        // This ensures the agent's personality/instructions persist throughout the conversation
        systemPrompt?.let {
            messages.add(Message(MessageRole.SYSTEM, it))
        }
        logger.info { "Agent '$name' initialized with ${tools.size} tools" }
    }
    
    /**
     * Executes the agent's main reasoning and action loop for a user prompt.
     * 
     * This is the primary entry point for agent interactions. The method implements
     * a sophisticated conversational AI pattern where the LLM can reason about problems
     * and request tool execution to gather information or perform actions.
     * 
     * ## Execution Process
     * 
     * 1. **Context Setup**: Adds user prompt to conversation history
     * 2. **Iterative Loop**: Continues until LLM provides final response or max iterations reached
     *    - **LLM Call**: Sends full conversation context to language model
     *    - **Response Processing**: Handles both text responses and tool call requests
     *    - **Tool Execution**: Runs requested tools in parallel for efficiency
     *    - **Context Update**: Integrates tool results back into conversation
     * 3. **Completion**: Returns final response and saves conversation history
     * 
     * ## Example Usage
     * 
     * **Simple Q&A**:
     * ```kotlin
     * val response = agent.run("What is the capital of France?")
     * // Returns: "The capital of France is Paris."
     * ```
     * 
     * **Tool-assisted Task**:
     * ```kotlin
     * val response = agent.run("Read the README.md file and summarize it")
     * // Agent will:
     * // 1. Call ReadFileTool to get file contents  
     * // 2. Process the content with LLM
     * // 3. Return a summary
     * ```
     * 
     * **Multi-step Problem Solving**:
     * ```kotlin
     * val response = agent.run("Find all TODO comments in my project and create a summary report")
     * // Agent might:
     * // 1. Use SearchTool to find files with TODO comments
     * // 2. Use ReadFileTool to examine each file
     * // 3. Use AnalysisTool to categorize and prioritize TODOs
     * // 4. Generate comprehensive summary report
     * ```
     * 
     * @param prompt User input that describes the desired task or question.
     *               Can be simple questions or complex multi-step requests.
     *               The LLM will analyze this prompt to determine the appropriate course of action.
     * 
     * @return Final text response from the agent after completing all necessary reasoning
     *         and tool executions. This represents the agent's complete answer to the user's request.
     * 
     * @throws Exception If critical errors occur during LLM communication or tool execution
     *                   that prevent the agent from continuing. Individual tool failures are
     *                   handled gracefully and reported to the LLM for recovery.
     */
    suspend fun run(prompt: String): String {
        // Initialize conversation with user input
        // Both messages (for LLM context) and history (for persistence) are updated
        messages.add(Message(MessageRole.USER, prompt))
        history.recordMessage(MessageRole.USER, prompt)
        
        // Track iterations to prevent infinite loops in complex reasoning chains
        var iterations = 0
        var finalResponse = ""
        
        /*
         * CORE AGENT REASONING LOOP
         * 
         * This loop implements the ReAct (Reasoning + Acting) pattern:
         * - The LLM reasons about the problem and decides what actions to take
         * - Tools are executed to gather information or perform actions  
         * - Results are fed back to the LLM for further reasoning
         * - This continues until the LLM has enough information to provide a final answer
         * 
         * Why the loop exists:
         * - Single LLM calls are limited to generating text responses
         * - Real-world tasks often require multiple information-gathering steps
         * - Tools extend the agent's capabilities beyond text generation
         * - The loop allows the LLM to build up context incrementally
         */
        while (iterations < maxIterations) {
            iterations++
            
            /*
             * REQUEST LLM RESPONSE
             * 
             * Send the full conversation context to the language model.
             * - messages: Complete conversation history (system prompt, user messages, tool results)
             * - tools: Available function schemas (null if no tools available)  
             * - temperature: Controls response creativity vs. determinism
             * 
             * The LLM can respond with:
             * 1. Text content (when ready to provide final answer)
             * 2. Tool calls (when it needs to gather information or take action)
             * 3. Both (explanation + tool requests)
             */
            val response = llm.complete(
                messages = messages,
                tools = if (tools.isNotEmpty()) tools.map { it.toFunctionSchema() } else null,
                temperature = temperature
            )
            
            /*
             * PROCESS TEXT RESPONSE
             * 
             * If the LLM generated text content, add it to the conversation context.
             * This could be reasoning, partial answers, or the final response.
             * The content is preserved as it provides valuable context for subsequent iterations.
             */
            response.content?.let { content ->
                messages.add(Message(MessageRole.ASSISTANT, content))
                history.recordMessage(MessageRole.ASSISTANT, content)
                finalResponse = content  // This becomes the return value if no more tool calls
            }
            
            /*
             * CHECK FOR TOOL EXECUTION REQUESTS
             * 
             * The LLM can request tool execution through structured tool calls.
             * If no tool calls are present, the agent has completed its reasoning
             * and is ready to return the final response.
             */
            val toolCalls = response.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                break // No more tool calls needed - agent has completed its task
            }
            
            /*
             * RECORD TOOL CALL INTENT
             * 
             * Add an assistant message containing the tool calls to maintain proper
             * conversation structure. This follows OpenAI's function calling protocol
             * where tool calls are part of assistant messages.
             */
            messages.add(Message(
                role = MessageRole.ASSISTANT,
                toolCalls = toolCalls
            ))
            
            /*
             * PARALLEL TOOL EXECUTION
             * 
             * Execute all requested tools concurrently for efficiency.
             * Multiple tools can often run independently (e.g., reading different files,
             * making separate API calls). Parallel execution reduces total response time.
             * 
             * Each tool runs in its own coroutine within a coroutineScope to ensure
             * structured concurrency and proper error handling.
             */
            val toolResults = coroutineScope {
                toolCalls.map { toolCall ->
                    async {
                        executeToolCall(toolCall)
                    }
                }.awaitAll()  // Wait for all tool executions to complete
            }
            
            /*
             * INTEGRATE TOOL RESULTS
             * 
             * Add tool execution results back into the conversation as TOOL role messages.
             * Each result is linked to its corresponding tool call via toolCallId.
             * 
             * This provides the LLM with the information it requested, allowing it to:
             * - Continue reasoning with new data
             * - Request additional tool executions if needed
             * - Provide a final answer based on gathered information
             * 
             * Tool results include both successful outputs and error messages,
             * allowing the LLM to handle failures gracefully and potentially retry
             * with different approaches.
             */
            toolResults.forEachIndexed { index, result ->
                messages.add(Message(
                    role = MessageRole.TOOL,
                    content = result.output ?: result.error ?: "Tool execution completed",
                    toolCallId = toolCalls[index].id
                ))
            }
        }
        
        // Log if maximum iterations reached (potential infinite loop or very complex task)
        if (iterations >= maxIterations) {
            logger.warn { "Agent reached maximum iterations ($maxIterations)" }
        }
        
        // Persist conversation history for debugging and replay capabilities
        history.save()
        return finalResponse
    }
    
    /**
     * Executes a single tool call request from the language model.
     * 
     * This method handles the complete lifecycle of tool execution including:
     * - Tool lookup and validation
     * - Parameter parsing and conversion
     * - Actual tool execution with error handling
     * - Result recording for history and debugging
     * 
     * ## Error Handling Strategy
     * 
     * The method implements graceful error handling to ensure the agent can continue
     * functioning even when individual tools fail:
     * 
     * - **Missing Tool**: Returns error result that informs the LLM the tool doesn't exist
     * - **Parameter Errors**: Caught during parsing, allowing the LLM to retry with correct parameters  
     * - **Execution Failures**: Tool-specific errors are captured and reported to the LLM
     * - **Unexpected Exceptions**: System-level errors are caught to prevent agent crashes
     * 
     * All errors are logged for debugging while returning structured error information
     * that the LLM can understand and potentially work around.
     * 
     * @param toolCall The tool call request containing:
     *                 - id: Unique identifier for tracking this specific call
     *                 - name: The tool name to execute (must match a registered tool)
     *                 - arguments: JSON parameters to pass to the tool
     * 
     * @return ToolResult containing either:
     *         - Success: result.success = true, result.output = tool's response data
     *         - Failure: result.success = false, result.error = descriptive error message
     */
    private suspend fun executeToolCall(toolCall: ToolCall): ToolResult {
        logger.debug { "Executing tool: ${toolCall.name}" }
        
        /*
         * TOOL LOOKUP
         * 
         * Use the pre-built toolMap for O(1) tool retrieval by name.
         * If the LLM requests a non-existent tool, return a structured error
         * that allows it to understand the problem and potentially retry.
         */
        val tool = toolMap[toolCall.name]
        if (tool == null) {
            val error = "Tool not found: ${toolCall.name}"
            logger.error { error }
            // Record the failure in history for debugging and audit purposes
            history.recordToolCall(toolCall.id, toolCall.name, emptyMap(), false, error)
            return ToolResult(false, error = error)
        }
        
        /*
         * TOOL EXECUTION WITH ERROR HANDLING
         * 
         * Execute the tool with comprehensive error handling to ensure:
         * 1. The agent doesn't crash from tool failures
         * 2. The LLM gets meaningful error information
         * 3. All executions are properly logged and recorded
         */
        return try {
            // Parse JSON arguments into a Map that tools can easily consume
            val parameters = parseToolArguments(toolCall.arguments)
            
            // Execute the actual tool logic
            val result = tool.run(parameters)
            
            // Record successful or failed tool execution in persistent history
            history.recordToolCall(
                toolCall.id,
                toolCall.name,
                parameters,
                result.success,
                result.output ?: result.error
            )
            result
        } catch (e: Exception) {
            /*
             * EXCEPTION RECOVERY
             * 
             * Catch any unexpected exceptions during tool execution to prevent
             * agent crashes. This includes parameter parsing errors, tool implementation
             * bugs, network failures, file system errors, etc.
             * 
             * The error is logged for debugging while returning a structured error
             * that allows the conversation to continue.
             */
            val error = "Tool execution failed: ${e.message}"
            logger.error(e) { error }
            history.recordToolCall(toolCall.id, toolCall.name, emptyMap(), false, error)
            ToolResult(false, error = error)
        }
    }
    
    /**
     * Converts JSON arguments from LLM tool calls into a Map that tools can easily consume.
     * 
     * This method handles the complex task of converting between JSON representation
     * (as provided by LLMs) and Kotlin types (as expected by tool implementations).
     * 
     * ## JSON Type Conversion Logic
     * 
     * The parser handles nested structures and automatically converts types:
     * - **Strings**: Direct content extraction (`"hello"` → `"hello"`)  
     * - **Numbers**: Auto-detection of integers vs doubles (`42` → `42`, `3.14` → `3.14`)
     * - **Booleans**: Direct conversion (`true` → `true`)
     * - **Arrays**: Recursive parsing to List (`[1, "a"]` → `listOf(1, "a")`)
     * - **Objects**: Recursive parsing to nested Maps (`{"key": "value"}` → `mapOf("key" to "value")`)
     * - **null**: Preserved as Kotlin null
     * 
     * ## Example Transformations
     * 
     * **Simple parameters**:
     * ```json
     * {"filename": "test.txt", "mode": "read", "lines": 10}
     * ```
     * becomes:
     * ```kotlin  
     * mapOf("filename" to "test.txt", "mode" to "read", "lines" to 10)
     * ```
     * 
     * **Complex nested structure**:
     * ```json
     * {
     *   "config": {
     *     "enabled": true,
     *     "servers": ["host1", "host2"],
     *     "timeout": 30.5
     *   }
     * }
     * ```
     * becomes:
     * ```kotlin
     * mapOf("config" to mapOf(
     *   "enabled" to true,
     *   "servers" to listOf("host1", "host2"),
     *   "timeout" to 30.5
     * ))
     * ```
     * 
     * @param arguments JsonElement containing the tool arguments as provided by the LLM.
     *                  Expected to be a JsonObject for valid tool calls, but handles
     *                  other types gracefully by returning empty map.
     * 
     * @return Map<String, Any?> where keys are parameter names and values are converted
     *         to appropriate Kotlin types. Tools can safely cast these values to expected types.
     */
    private fun parseToolArguments(arguments: JsonElement): Map<String, Any?> {
        return when (arguments) {
            is JsonObject -> arguments.mapValues { (_, value) ->
                /*
                 * RECURSIVE VALUE PARSING
                 * 
                 * Each JSON value is processed based on its type, with recursive
                 * handling for nested structures (arrays and objects).
                 * The parsing preserves the original structure while converting
                 * to appropriate Kotlin types.
                 */
                when (value) {
                    is JsonPrimitive -> {
                        /*
                         * PRIMITIVE TYPE DETECTION
                         * 
                         * JsonPrimitive can represent strings, numbers, or booleans.
                         * We attempt type detection in order of specificity:
                         * 1. Boolean (most specific)
                         * 2. Integer (more specific than double)  
                         * 3. Double (numeric fallback)
                         * 4. String (default fallback)
                         */
                        when {
                            value.isString -> value.content
                            value.booleanOrNull != null -> value.boolean
                            value.intOrNull != null -> value.int
                            value.doubleOrNull != null -> value.double
                            else -> value.content  // Fallback to string representation
                        }
                    }
                    is JsonArray -> value.map { parseJsonValue(it) }  // Recursive array parsing
                    is JsonObject -> parseToolArguments(value)        // Recursive object parsing
                    is JsonNull -> null
                }
            }
            else -> emptyMap()  // Gracefully handle non-object arguments
        }
    }
    
    /**
     * Recursively parses individual JSON values within arrays and nested objects.
     * 
     * This helper method is used by `parseToolArguments` to handle the recursive
     * parsing of complex nested structures. It applies the same type conversion
     * logic as the main parsing method but is optimized for individual value processing.
     * 
     * @param value JsonElement to parse (can be primitive, array, object, or null)
     * @return Appropriately typed Kotlin value (String, Int, Double, Boolean, List, Map, or null)
     */
    private fun parseJsonValue(value: JsonElement): Any? {
        return when (value) {
            is JsonPrimitive -> {
                // Apply the same type detection logic as the main parser
                when {
                    value.isString -> value.content
                    value.booleanOrNull != null -> value.boolean
                    value.intOrNull != null -> value.int
                    value.doubleOrNull != null -> value.double
                    else -> value.content  // Fallback to string
                }
            }
            is JsonArray -> value.map { parseJsonValue(it) }      // Recursive array parsing
            is JsonObject -> parseToolArguments(value)            // Recursive object parsing
            is JsonNull -> null
        }
    }
    
    /**
     * Clears the entire conversation history while preserving the system prompt.
     * 
     * This method resets the agent to its initial state, removing all user messages,
     * assistant responses, and tool call results from the conversation context.
     * The system prompt (if originally provided) is automatically restored to
     * maintain the agent's configured personality and behavior.
     * 
     * ## Use Cases
     * 
     * - **New Conversation**: Starting a fresh conversation with the same agent
     * - **Memory Management**: Clearing long conversations to reduce token usage
     * - **Context Reset**: Removing irrelevant history before tackling a new task
     * - **Testing**: Resetting agent state between test cases
     * 
     * ## Example Usage
     * 
     * ```kotlin
     * // After a long troubleshooting session
     * agent.run("Help me debug this complex issue...")
     * // ... many iterations of back-and-forth ...
     * 
     * // Now switch to a different task with clean context
     * agent.clearMessages()
     * agent.run("Now help me write documentation...")
     * ```
     * 
     * Note: This only clears the in-memory conversation context. Persistent history
     * (managed by the History class) is preserved for debugging and audit purposes.
     */
    fun clearMessages() {
        messages.clear()
        // Restore system prompt to maintain agent personality/instructions
        systemPrompt?.let {
            messages.add(Message(MessageRole.SYSTEM, it))
        }
    }
    
    /**
     * Returns a read-only copy of the current conversation messages.
     * 
     * This method provides access to the complete conversation context that gets
     * sent to the language model on each request. The returned list includes:
     * - System prompt message (if configured)
     * - All user input messages
     * - All assistant response messages (including those with tool calls)
     * - All tool execution result messages
     * 
     * ## Use Cases
     * 
     * - **Debugging**: Inspecting the conversation flow and message history
     * - **Analytics**: Analyzing conversation patterns and tool usage
     * - **Integration**: Passing conversation context to other systems
     * - **UI Display**: Showing conversation history in chat interfaces
     * - **Testing**: Verifying correct message sequencing and content
     * 
     * ## Message Structure
     * 
     * Each Message contains:
     * - **role**: MessageRole (SYSTEM, USER, ASSISTANT, TOOL)
     * - **content**: Text content (null for tool-call-only assistant messages)
     * - **toolCalls**: List of tool calls (only for ASSISTANT role)
     * - **toolCallId**: Reference to tool call (only for TOOL role)
     * 
     * ## Example Usage
     * 
     * ```kotlin
     * val conversation = agent.getMessages()
     * conversation.forEach { message ->
     *     println("${message.role}: ${message.content}")
     *     message.toolCalls?.forEach { toolCall ->
     *         println("  Tool Call: ${toolCall.name}")
     *     }
     * }
     * ```
     * 
     * @return Immutable list of Message objects representing the complete conversation history.
     *         The list is a defensive copy, so modifications won't affect the agent's internal state.
     */
    fun getMessages(): List<Message> = messages.toList()
}