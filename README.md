# ConnectOnion Kotlin SDK

[![GitHub](https://img.shields.io/badge/GitHub-openonion%2Fconnectonion--kotlin-blue?logo=github)](https://github.com/openonion/connectonion-kotlin)

A Kotlin SDK for creating AI agents with behavior tracking, compatible with the ConnectOnion framework.

## Features

- 🤖 **Agent Creation**: Build intelligent agents that can use tools and maintain conversation context
- 🔧 **Tool System**: Extensible tool framework for giving agents capabilities
- 📝 **Behavior Tracking**: Automatic logging and persistence of agent interactions
- 🔌 **OpenAI Integration**: Built-in support for OpenAI's GPT models
- ⚡ **Async/Coroutines**: Full Kotlin coroutines support for concurrent operations
- 🛠️ **Built-in Tools**: Common tools like file operations, web fetching, and shell execution

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.connectonion:connectonion-kotlin:0.0.1")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.connectonion:connectonion-kotlin:0.0.1'
}
```

### Maven

```xml
<dependency>
    <groupId>com.connectonion</groupId>
    <artifactId>connectonion-kotlin</artifactId>
    <version>0.0.1</version>
</dependency>
```

## Quick Start

### Basic Agent

```kotlin
import com.connectonion.core.Agent
import com.connectonion.llm.OpenAILLM
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Initialize OpenAI LLM
    val llm = OpenAILLM(
        apiKey = System.getenv("OPENAI_API_KEY"),
        model = "gpt-4"
    )
    
    // Create an agent
    val agent = Agent(
        name = "assistant",
        llm = llm,
        systemPrompt = "You are a helpful assistant."
    )
    
    // Run the agent
    val response = agent.run("What is the capital of France?")
    println(response)
}
```

### Agent with Tools

```kotlin
import com.connectonion.core.Agent
import com.connectonion.llm.OpenAILLM
import com.connectonion.tools.BuiltInTools
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val llm = OpenAILLM(apiKey = System.getenv("OPENAI_API_KEY"))
    
    // Create agent with tools
    val agent = Agent(
        name = "file-assistant",
        llm = llm,
        tools = listOf(
            BuiltInTools.FileReaderTool(),
            BuiltInTools.FileWriterTool(),
            BuiltInTools.DateTimeTool()
        ),
        systemPrompt = "You are a helpful file management assistant."
    )
    
    // Agent can now use tools
    val response = agent.run("What's in the README.md file?")
    println(response)
}
```

### Custom Tool Creation

```kotlin
import com.connectonion.core.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CalculatorTool : Tool() {
    override val name = "calculator"
    override val description = "Perform basic math calculations"
    
    override suspend fun run(parameters: Map<String, Any?>): ToolResult {
        val operation = parameters["operation"] as? String ?: return ToolResult(false, error = "Missing operation")
        val a = (parameters["a"] as? Number)?.toDouble() ?: return ToolResult(false, error = "Missing operand a")
        val b = (parameters["b"] as? Number)?.toDouble() ?: return ToolResult(false, error = "Missing operand b")
        
        val result = when (operation) {
            "add" -> a + b
            "subtract" -> a - b
            "multiply" -> a * b
            "divide" -> if (b != 0.0) a / b else return ToolResult(false, error = "Division by zero")
            else -> return ToolResult(false, error = "Unknown operation: $operation")
        }
        
        return ToolResult(true, output = result.toString())
    }
    
    override fun toFunctionSchema(): FunctionSchema {
        return FunctionSchema(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("operation", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonObject {
                            put("0", "add")
                            put("1", "subtract")
                            put("2", "multiply")
                            put("3", "divide")
                        })
                    })
                    put("a", buildJsonObject {
                        put("type", "number")
                        put("description", "First operand")
                    })
                    put("b", buildJsonObject {
                        put("type", "number")
                        put("description", "Second operand")
                    })
                })
                put("required", buildJsonObject {
                    put("0", "operation")
                    put("1", "a")
                    put("2", "b")
                })
            }
        )
    }
}
```

## Architecture

### Core Components

- **Agent**: Main orchestrator that manages LLM interactions and tool execution
- **Tool**: Abstract base class for creating agent capabilities
- **LLM**: Abstract interface for language model implementations
- **History**: Behavior tracking and persistence system

### Built-in Tools

- `FileReaderTool`: Read files from the filesystem
- `FileWriterTool`: Write content to files
- `WebFetchTool`: Fetch content from URLs
- `DateTimeTool`: Get current date/time in various formats
- `ShellTool`: Execute shell commands

## Configuration

### Environment Variables

- `OPENAI_API_KEY`: Your OpenAI API key
- `CONNECTONION_HOME`: Directory for storing agent behavior (default: `~/.connectonion`)

### Agent Parameters

```kotlin
val agent = Agent(
    name = "my-agent",           // Unique identifier
    llm = llm,                   // LLM implementation
    tools = listOf(...),         // Available tools
    systemPrompt = "...",        // System instructions
    temperature = 0.7,           // LLM temperature (0-2)
    maxIterations = 10          // Max tool-calling iterations
)
```

## Behavior Tracking

All agent interactions are automatically tracked and saved to `~/.connectonion/agents/{agent-name}/behavior.json`.

```kotlin
// Access behavior history
val history = History("my-agent")
val behaviors = history.getBehaviors()

// Clear history
history.clear()
```

## Testing

Run tests with:

```bash
./gradlew test
```

## Building

Build the project:

```bash
./gradlew build
```

Generate JAR:

```bash
./gradlew jar
```

---

## 💬 Join the Community

[![Discord](https://img.shields.io/discord/1234567890?color=7289da&label=Join%20Discord&logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/4xfD9k8AUF)

Get help, share agents, and discuss with 1000+ builders in our active community.

---

## ⭐ Show Your Support

If ConnectOnion helps you build better agents, **give it a star!** ⭐

It helps others discover the framework and motivates us to keep improving it.

[⭐ Star on GitHub](https://github.com/openonion/connectonion-kotlin)

---

## Contributing

Contributions are welcome! Please read our [Contributing Guide](https://github.com/openonion/connectonion-kotlin/blob/main/CONTRIBUTING.md) for details.

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Links

- [ConnectOnion Python](https://github.com/connectonion/connectonion)
- [ConnectOnion Swift](https://github.com/connectonion/connectonion-swift)
- [Documentation](https://github.com/connectonion/connectonion#readme)
- [Discord Community](https://discord.gg/4xfD9k8AUF)