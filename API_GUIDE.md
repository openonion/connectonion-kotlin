# ConnectOnion Kotlin SDK - API Guide

This guide provides detailed documentation for all public APIs in the ConnectOnion Kotlin SDK.

## Table of Contents
- [Core Components](#core-components)
  - [Agent](#agent)
  - [Tool](#tool)
  - [LLM](#llm)
- [Built-in Tools](#built-in-tools)
- [Configuration](#configuration)
- [History & Behavior Tracking](#history--behavior-tracking)
- [Examples](#examples)

## Core Components

### Agent

The `Agent` class is the main orchestrator that combines LLM interactions with tool execution.

```kotlin
class Agent(
    name: String,                    // Unique identifier for the agent
    llm: LLM,                        // Language model implementation
    tools: List<Tool> = emptyList(), // Available tools
    systemPrompt: String? = null,    // System instructions
    temperature: Double = 0.7,       // LLM temperature (0-2)
    maxIterations: Int = 10         // Max tool-calling iterations
)
```

#### Key Methods

##### `suspend fun run(prompt: String): String`
Executes the agent with a user prompt and returns the final response.

```kotlin
val agent = Agent("assistant", llm)
val response = agent.run("What's the weather?")
```

##### `fun clearMessages()`
Clears the conversation history while preserving the system prompt.

```kotlin
agent.clearMessages() // Start fresh conversation
```

##### `fun getMessages(): List<Message>`
Returns the current conversation history.

```kotlin
val history = agent.getMessages()
history.forEach { message ->
    println("${message.role}: ${message.content}")
}
```

#### Agent Execution Flow

1. **User Input** → Agent receives prompt
2. **LLM Call** → Send messages + tools to LLM
3. **Response Analysis** → Check for content or tool calls
4. **Tool Execution** → Run requested tools in parallel
5. **Result Integration** → Add tool results to context
6. **Iteration** → Repeat until complete or max iterations

### Tool

Abstract base class for creating agent tools.

```kotlin
abstract class Tool {
    abstract val name: String
    abstract val description: String
    abstract suspend fun run(parameters: Map<String, Any?>): ToolResult
    abstract fun toFunctionSchema(): FunctionSchema
}
```

#### Creating Custom Tools

##### Method 1: Extend Tool Class

```kotlin
class WeatherTool : Tool() {
    override val name = "get_weather"
    override val description = "Get current weather for a location"
    
    override suspend fun run(parameters: Map<String, Any?>): ToolResult {
        val location = parameters["location"] as? String
            ?: return ToolResult(false, error = "Missing location")
        
        // Fetch weather data
        val weather = fetchWeather(location)
        return ToolResult(true, output = weather)
    }
    
    override fun toFunctionSchema(): FunctionSchema {
        return FunctionSchema(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("location", buildJsonObject {
                        put("type", "string")
                        put("description", "City name or coordinates")
                    })
                })
                put("required", JsonArray(listOf(JsonPrimitive("location"))))
            }
        )
    }
}
```

##### Method 2: Use FunctionTool

```kotlin
val weatherTool = FunctionTool(
    name = "get_weather",
    description = "Get current weather",
    function = { params ->
        val location = params["location"] as String
        fetchWeather(location)
    },
    parameterSchema = buildJsonObject {
        // Schema definition
    }
)
```

#### ToolResult

```kotlin
data class ToolResult(
    val success: Boolean,      // Execution status
    val output: String? = null, // Success output
    val error: String? = null   // Error message
)
```

### LLM

Abstract interface for language model implementations.

```kotlin
abstract class LLM {
    abstract suspend fun complete(
        messages: List<Message>,
        tools: List<FunctionSchema>? = null,
        temperature: Double = 0.7
    ): LLMResponse
}
```

#### OpenAILLM Implementation

```kotlin
val llm = OpenAILLM(
    apiKey = "your-api-key",
    model = "gpt-4",              // Model name
    baseUrl = "https://api.openai.com/v1", // API endpoint
    maxTokens = 500               // Max response tokens
)

// Don't forget to clean up
llm.close()
```

#### Message Types

```kotlin
data class Message(
    val role: MessageRole,         // SYSTEM, USER, ASSISTANT, TOOL
    val content: String? = null,   // Text content
    val name: String? = null,       // Optional name
    val toolCalls: List<ToolCall>? = null, // Tool requests
    val toolCallId: String? = null // Tool response ID
)
```

## Built-in Tools

The SDK includes several pre-built tools:

### FileReaderTool
Reads files from the filesystem.

```kotlin
val tool = BuiltInTools.FileReaderTool()
// Parameters: path (String)
```

### FileWriterTool
Writes content to files.

```kotlin
val tool = BuiltInTools.FileWriterTool()
// Parameters: path (String), content (String)
```

### WebFetchTool
Fetches content from URLs.

```kotlin
val tool = BuiltInTools.WebFetchTool()
// Parameters: url (String)
```

### DateTimeTool
Gets current date/time in various formats.

```kotlin
val tool = BuiltInTools.DateTimeTool()
// Parameters: format (String, optional)
```

### ShellTool
Executes shell commands.

```kotlin
val tool = BuiltInTools.ShellTool()
// Parameters: command (String)
```

## Configuration

The `Config` object manages environment variables and .env files.

### Loading Configuration

```kotlin
// Automatically loads from .env file
val apiKey = Config.get("OPENAI_API_KEY")

// With default value
val model = Config.get("MODEL", "gpt-4")

// Required value (throws if missing)
val required = Config.getRequired("API_KEY")

// Check existence
if (Config.has("DEBUG")) {
    // Enable debug mode
}

// Specialized getter
val openAIKey = Config.getOpenAIKey() // Helpful error if missing
```

### Configuration Priority

1. System environment variables
2. .env file in current directory
3. .env file in parent directories
4. Default value (if provided)

## History & Behavior Tracking

The `History` class tracks all agent interactions.

```kotlin
val history = History("agent-name")

// Recording happens automatically in Agent
history.recordMessage(MessageRole.USER, "Hello")
history.recordToolCall(
    callId = "123",
    toolName = "calculator",
    parameters = mapOf("a" to 5, "b" to 3),
    success = true,
    result = "8"
)

// Save to disk
history.save() // Saves to ~/.connectonion/agents/{name}/behavior.json

// Retrieve behaviors
val behaviors = history.getBehaviors()

// Clear history
history.clear()
```

### Behavior Storage

Behaviors are stored in JSON format at:
```
~/.connectonion/agents/{agent-name}/behavior.json
```

Structure:
```json
{
  "agentName": "assistant",
  "entries": [
    {
      "id": "uuid",
      "timestamp": "2024-01-01T12:00:00Z",
      "type": "MESSAGE",
      "role": "USER",
      "content": "Hello"
    },
    {
      "id": "call_123",
      "timestamp": "2024-01-01T12:00:01Z",
      "type": "TOOL_CALL",
      "toolName": "calculator",
      "parameters": {"a": "5", "b": "3"},
      "success": true,
      "result": "8"
    }
  ]
}
```

## Examples

### Basic Q&A Agent

```kotlin
val agent = Agent(
    name = "qa-bot",
    llm = OpenAILLM(apiKey),
    systemPrompt = "You are a helpful Q&A assistant."
)

val answer = agent.run("What is the capital of France?")
println(answer) // "The capital of France is Paris."
```

### Agent with Tools

```kotlin
val agent = Agent(
    name = "file-manager",
    llm = llm,
    tools = listOf(
        BuiltInTools.FileReaderTool(),
        BuiltInTools.FileWriterTool()
    )
)

agent.run("Read config.json and create a backup")
// Agent will use tools to read and write files
```

### Multi-turn Conversation

```kotlin
val agent = Agent("chat-bot", llm)

// First turn
agent.run("My name is Alice")

// Second turn (remembers context)
agent.run("What's my name?")
// Response: "Your name is Alice."

// Clear and start fresh
agent.clearMessages()
agent.run("What's my name?")
// Response: "I don't know your name..."
```

### Custom Tool Integration

```kotlin
// Define custom tool
class DatabaseTool : Tool() {
    override val name = "query_db"
    override val description = "Query the database"
    
    override suspend fun run(parameters: Map<String, Any?>): ToolResult {
        val query = parameters["query"] as? String
            ?: return ToolResult(false, error = "Missing query")
        
        val results = database.execute(query)
        return ToolResult(true, output = results.toString())
    }
    
    // ... schema implementation
}

// Use with agent
val agent = Agent(
    name = "data-analyst",
    llm = llm,
    tools = listOf(DatabaseTool())
)

agent.run("How many users signed up this month?")
// Agent will use DatabaseTool to query and analyze data
```

### Error Handling

```kotlin
try {
    val response = agent.run("Process this request")
} catch (e: Exception) {
    when (e) {
        is ConfigurationException -> {
            // Handle missing configuration
            println("Missing config: ${e.message}")
        }
        is HttpRequestTimeoutException -> {
            // Handle timeout
            println("Request timed out")
        }
        else -> {
            // Handle other errors
            println("Error: ${e.message}")
        }
    }
}
```

## Best Practices

### 1. Tool Design
- Keep tools focused on a single responsibility
- Validate parameters thoroughly
- Return descriptive error messages
- Use suspend functions for I/O operations

### 2. Agent Configuration
- Set appropriate temperature for your use case
  - 0.0-0.3: Deterministic, factual responses
  - 0.4-0.7: Balanced creativity and accuracy
  - 0.8-2.0: Creative, varied responses
- Limit maxIterations to prevent infinite loops
- Provide clear system prompts

### 3. Error Handling
- Always handle tool execution errors gracefully
- Log errors for debugging
- Provide fallback behaviors
- Clean up resources (close LLM clients)

### 4. Performance
- Tools execute in parallel for better performance
- Use coroutines for async operations
- Cache frequently used data
- Monitor token usage for cost control

### 5. Security
- Never expose API keys in code
- Validate and sanitize tool inputs
- Limit shell command execution
- Use read-only tools when possible

## Troubleshooting

### Common Issues

#### Missing API Key
```
Error: OpenAI API key not found!
```
**Solution**: Set OPENAI_API_KEY in environment or .env file

#### Tool Not Found
```
Error: Tool not found: tool_name
```
**Solution**: Ensure tool is registered in agent's tools list

#### Timeout Errors
```
HttpRequestTimeoutException
```
**Solution**: Increase timeout or check network connection

#### Maximum Iterations Reached
```
Agent reached maximum iterations (10)
```
**Solution**: Increase maxIterations or simplify the task

## Version Compatibility

- Kotlin: 1.9.20+
- Java: 11+
- Coroutines: 1.7.3+
- Ktor: 2.3.5+
- Serialization: 1.6.0+

## Support

- GitHub Issues: [Report bugs](https://github.com/connectonion/connectonion-kotlin/issues)
- Discord: [Join community](https://discord.gg/4xfD9k8AUF)
- Documentation: [Full docs](https://github.com/connectonion/connectonion-kotlin)