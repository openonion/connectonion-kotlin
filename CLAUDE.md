# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ConnectOnion Kotlin SDK - A JVM implementation of the ConnectOnion AI agent framework. This SDK follows the same design philosophy as other ConnectOnion SDKs: "Keep simple things simple, make complicated things possible".

Key features:
- Simple 2-line agent creation and execution
- Class-based Tool system with OpenAI function calling compatibility
- Automatic behavior tracking to `~/.connectonion/`
- Full Kotlin coroutines support for async operations
- Built-in tools for common operations (file I/O, web fetch, shell, datetime)

## Development Commands

### Build and Test
```bash
# Full build with tests
./gradlew build

# Build without tests
./gradlew build -x test

# Run tests only
./gradlew test

# Run specific test class
./gradlew test --tests "com.connectonion.core.AgentTest"

# Run specific test method
./gradlew test --tests "*AgentTest.should initialize with correct properties"

# Run with test coverage report
./gradlew test jacocoTestReport

# Clean build artifacts
./gradlew clean
```

### Running Examples
```bash
# Run the TestRunner (default main class)
./gradlew run

# Run standalone example files
kotlin -cp build/libs/*.jar examples/BasicAgent.kt
```

### Code Quality
```bash
# Format code with ktlint (if configured)
./gradlew ktlintFormat

# Check code style
./gradlew ktlintCheck

# Generate documentation
./gradlew dokkaHtml
```

### Publishing (Maintainers)
```bash
# Build JAR artifacts
./gradlew jar

# Publish to local Maven repository
./gradlew publishToMavenLocal

# Publish to Sonatype (requires credentials)
./gradlew publish
```

## Architecture Overview

### Core Agent Execution Loop

The most important architectural pattern is in `Agent.kt:175-309` - the ReAct (Reasoning + Acting) loop:

1. **User Input** → Added to conversation messages
2. **LLM Call** → Sends full context + available tools
3. **Response Processing**:
   - If text content: add to messages, potentially final response
   - If tool calls: execute all in parallel
4. **Tool Results** → Added back to messages as TOOL role
5. **Iteration** → Repeat until no more tool calls or max iterations reached

Key implementation details:
- **Parallel tool execution** (Agent.kt:269-275): All tool calls in one LLM response execute concurrently via coroutineScope + async/awaitAll
- **Max iterations default**: 10 (configurable to prevent infinite loops)
- **Tool lookup**: O(1) via pre-built toolMap (Agent.kt:101)

### Message Flow Structure

Messages follow OpenAI's function calling protocol:
1. **SYSTEM** → Initial system prompt (if provided)
2. **USER** → User input
3. **ASSISTANT** → Can have content and/or toolCalls
4. **TOOL** → Results linked to toolCallId
5. **ASSISTANT** → Final response after processing tool results

### Tool System Architecture

Two ways to create tools:

**1. Class-based (recommended for complex tools)**:
```kotlin
class MyTool : Tool() {
    override val name = "tool_name"
    override val description = "What it does"
    override suspend fun run(parameters: Map<String, Any?>): ToolResult { }
    override fun toFunctionSchema(): FunctionSchema { }
}
```

**2. Function-based (simpler, for basic tools)**:
```kotlin
val tool = FunctionTool(name, description, { params -> ... }, schema)
```

### JSON Parameter Parsing

Agent.kt:456-522 handles complex JSON-to-Kotlin type conversion:
- Recursive parsing for nested objects/arrays
- Auto-detection: Boolean → Integer → Double → String
- Preserves null values
- Converts to Map<String, Any?> for tool consumption

### Behavior Tracking System

Every agent interaction persists to `~/.connectonion/agents/{agent-name}/behavior.json`:
- Messages (role, content, timestamp)
- Tool calls (id, name, parameters, success/failure, results)
- Managed by History class (history/History.kt)

## Key Files and Their Responsibilities

### Core Components (src/main/kotlin/com/connectonion/core/)

**Agent.kt** - Main orchestrator
- Line 92-119: Initialization and setup
- Line 175-309: Main execution loop (ReAct pattern)
- Line 342-401: Tool execution with error handling
- Line 456-522: JSON parameter parsing

**Tool.kt** - Tool base classes and interfaces
- Line 36-114: Abstract Tool base class
- Line 135-140: ToolResult data class
- Line 160-165: FunctionSchema (OpenAI-compatible)
- Line 206-239: FunctionTool wrapper class
- Line 260-265: ToolCall data class

### LLM Components (src/main/kotlin/com/connectonion/llm/)

**LLM.kt** - Abstract LLM interface and data classes
- Line 11-24: LLM abstract class
- Line 29-36: Message data class
- Line 42-47: MessageRole enum
- Line 52-58: LLMResponse data class

**OpenAILLM.kt** - OpenAI API implementation
- Uses Ktor HTTP client for API calls
- Converts between internal format and OpenAI API format
- Handles function calling protocol

### Built-in Tools (src/main/kotlin/com/connectonion/tools/)

**BuiltInTools.kt** - Pre-built tool implementations
- FileReaderTool: Read files from filesystem
- FileWriterTool: Write content to files
- WebFetchTool: HTTP GET requests
- DateTimeTool: Current date/time formatting
- ShellTool: Execute shell commands

### Configuration (src/main/kotlin/com/connectonion/config/)

**Config.kt** - Environment variable and .env file management
- Searches for .env in current and parent directories
- Falls back to system environment variables
- Provides type-safe getters with defaults

### Testing (src/test/kotlin/)

All tests use:
- **Kotest** for test structure (FunSpec style)
- **MockK** for mocking LLM and tools
- **kotlinx-coroutines-test** for suspend function testing

Test pattern:
```kotlin
class MyTest : FunSpec({
    test("description") {
        runTest {
            // Test implementation with coroutines
        }
    }
})
```

## Common Development Patterns

### Creating a New Tool

1. Create class extending `Tool()` in `tools/` package
2. Implement required properties and methods:
   - `name`: Lowercase with underscores
   - `description`: Clear explanation for LLM
   - `run()`: Parameter validation + logic + error handling
   - `toFunctionSchema()`: JSON Schema for parameters
3. Add tests in `src/test/kotlin/com/connectonion/tools/`
4. Add to BuiltInTools or use directly

### Error Handling Philosophy

From the global CLAUDE.md principles:
- **Don't over-engineer**: Avoid unnecessary try-catch blocks
- **Let programs crash**: Errors should bubble up for visibility
- **Descriptive errors**: Include context in error messages
- **Tool-level handling**: Tools return ToolResult with success/error

Exception: Agent.kt:369-400 catches exceptions during tool execution to prevent agent crashes and allow LLM to handle errors gracefully.

### Testing Strategy

**Unit tests**: Mock LLM responses for deterministic testing
```kotlin
val mockLLM = mockk<LLM>()
coEvery { mockLLM.complete(any(), any(), any()) } returns LLMResponse(...)
```

**Integration tests**: Would require actual API keys and network calls (currently limited)

**Test coverage priorities**:
1. Agent execution flow (tool calling, iterations, message management)
2. Tool parameter validation and execution
3. JSON parameter parsing edge cases
4. Error handling and recovery

### Async/Coroutines Usage

All agent operations are suspend functions:
- Use `runBlocking` for main function entry points
- Use `coroutineScope` for structured concurrency
- Use `async/await` for parallel operations
- Test with `runTest` from kotlinx-coroutines-test

## Project Dependencies

Key libraries (from build.gradle.kts):
- **Kotlin 1.9.20**: Language and stdlib
- **kotlinx-coroutines-core 1.7.3**: Async operations
- **kotlinx-serialization-json 1.6.0**: JSON handling
- **Ktor 2.3.5**: HTTP client for LLM APIs
- **kotlin-logging-jvm 3.0.5**: Structured logging
- **Kotest 5.7.2**: Testing framework
- **MockK 1.13.8**: Mocking library

Target: **Java 11+** (JVM target 11)

## Environment Configuration

Required for running agents:
```bash
# .env file or environment variables
OPENAI_API_KEY=sk-...

# Optional
CONNECTONION_HOME=/custom/path  # Default: ~/.connectonion
```

## Cross-SDK Compatibility

This Kotlin SDK follows the same patterns as:
- **Python SDK** (connectonion/): Reference implementation
- **TypeScript SDK** (connectonion-ts/)
- **Rust SDK** (connectonion-rust/)
- **Swift SDK** (connectonion-swift/)

Shared patterns:
1. Simple agent creation: `Agent(name, llm, tools)`
2. Execution: `agent.run(prompt)` returns String
3. Tool interface: name, description, run(), schema
4. Behavior tracking to `~/.connectonion/`
5. Max iterations to prevent infinite loops

## Common Issues

### Gradle Wrapper Issues
If `./gradlew` fails, ensure it's executable:
```bash
chmod +x gradlew
```

### API Key Not Found
Error: "OpenAI API key not found!"
Solution: Create `.env` file or set `OPENAI_API_KEY` environment variable

### Coroutine Context Issues
Ensure all agent calls are within coroutine context:
```kotlin
// Main function
fun main() = runBlocking {
    agent.run(prompt)  // OK
}

// Or
suspend fun example() {
    agent.run(prompt)  // OK
}
```

### Test Failures
If tests fail, check:
1. Gradle is using Java 11+
2. Dependencies are up to date: `./gradlew build --refresh-dependencies`
3. Clean build: `./gradlew clean build`

## Code Style Guidelines

From CONTRIBUTING.md:
- Classes: PascalCase
- Functions/variables: camelCase
- Constants: UPPER_SNAKE_CASE
- Packages: lowercase
- KDoc required for all public APIs
- Use structured concurrency for async operations
- Meaningful error messages with context

## Philosophy Alignment

This SDK follows ConnectOnion's core principles:
1. **Simplicity first**: Simple agent should be 2 lines of code
2. **Functions as primitives**: Tools are functions, agents are functions
3. **Behavior over configuration**: Track what agents do, not what they're configured to do
4. **No over-engineering**: Throw errors instead of silent try-catch; let programs crash and fix root cause