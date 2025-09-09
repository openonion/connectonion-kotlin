# Contributing to ConnectOnion Kotlin SDK

Thank you for your interest in contributing to the ConnectOnion Kotlin SDK! This guide will help you get started with development.

## Table of Contents
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Coding Standards](#coding-standards)
- [Testing](#testing)
- [Documentation](#documentation)
- [Submitting Changes](#submitting-changes)
- [Creating New Tools](#creating-new-tools)
- [Adding LLM Providers](#adding-llm-providers)

## Getting Started

### Prerequisites
- Java 11 or higher
- Kotlin 1.9.20 or higher
- Gradle 8.0 or higher
- Git
- An IDE (IntelliJ IDEA recommended)

### Fork and Clone
1. Fork the repository on GitHub
2. Clone your fork locally:
```bash
git clone https://github.com/your-username/connectonion-kotlin.git
cd connectonion-kotlin
```

3. Add the upstream remote:
```bash
git remote add upstream https://github.com/connectonion/connectonion-kotlin.git
```

## Development Setup

### 1. Environment Configuration
Create a `.env` file in the project root:
```bash
OPENAI_API_KEY=your-api-key-here
# Add other API keys as needed
```

### 2. Build the Project
```bash
# Build without tests
gradle build -x test

# Full build with tests
gradle build

# Run specific test
gradle test --tests "com.connectonion.core.AgentTest"
```

### 3. Run Examples
```bash
# Run the test runner
gradle run

# Run tests
gradle test

# Generate documentation
gradle dokkaHtml
```

## Project Structure

```
connectonion-kotlin/
├── src/
│   ├── main/kotlin/com/connectonion/
│   │   ├── core/          # Core classes (Agent, Tool)
│   │   ├── llm/           # LLM interfaces and implementations
│   │   ├── tools/         # Built-in tools
│   │   ├── history/       # Behavior tracking
│   │   ├── config/        # Configuration management
│   │   └── examples/      # Example implementations
│   └── test/kotlin/       # Unit and integration tests
├── examples/              # Standalone example files
├── docs/                  # Additional documentation
└── build.gradle.kts       # Build configuration
```

## Coding Standards

### Kotlin Style Guide

We follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) with these additions:

#### Naming Conventions
```kotlin
// Classes: PascalCase
class MyCustomTool

// Functions and variables: camelCase
fun processRequest()
val userName: String

// Constants: UPPER_SNAKE_CASE
const val MAX_RETRIES = 3

// Packages: lowercase
package com.connectonion.tools
```

#### Documentation
Every public API must have KDoc:
```kotlin
/**
 * Processes the user request and returns a response.
 * 
 * @param request The incoming request to process
 * @param timeout Maximum time to wait for response in milliseconds
 * @return Processed response or null if timeout occurred
 * @throws IllegalArgumentException if request is invalid
 * 
 * @sample com.connectonion.samples.processRequestSample
 */
suspend fun processRequest(
    request: String,
    timeout: Long = 5000
): String?
```

#### Code Organization
```kotlin
class MyClass {
    // 1. Companion object
    companion object {
        private const val DEFAULT_TIMEOUT = 5000L
    }
    
    // 2. Properties
    private val logger = KotlinLogging.logger {}
    val publicProperty: String
    
    // 3. Init block
    init {
        // Initialization code
    }
    
    // 4. Constructors
    constructor(value: String) : this(value, DEFAULT_TIMEOUT)
    
    // 5. Public functions
    fun publicMethod() { }
    
    // 6. Private functions
    private fun helperMethod() { }
    
    // 7. Nested classes
    class NestedClass { }
}
```

### Error Handling

Always provide meaningful error messages:
```kotlin
// Good
val path = parameters["path"] as? String
    ?: return ToolResult(false, error = "Missing required parameter 'path'")

// Better - include expected format
val timeout = parameters["timeout"] as? Long
    ?: return ToolResult(false, 
        error = "Missing or invalid 'timeout' parameter. Expected: Long (milliseconds)")
```

### Async/Coroutines

Use structured concurrency:
```kotlin
suspend fun executeMultipleTasks() = coroutineScope {
    val results = tasks.map { task ->
        async {
            executeTask(task)
        }
    }.awaitAll()
}
```

## Testing

### Test Structure
```kotlin
class MyToolTest : FunSpec({
    
    // Group related tests
    context("parameter validation") {
        test("should reject missing required parameters") {
            val tool = MyTool()
            val result = tool.run(emptyMap())
            
            result.success shouldBe false
            result.error shouldContain "Missing required parameter"
        }
        
        test("should accept valid parameters") {
            // Test implementation
        }
    }
    
    context("execution") {
        test("should handle success case") {
            // Test implementation
        }
    }
})
```

### Test Coverage
- Aim for >80% code coverage
- Test edge cases and error conditions
- Include integration tests for LLM interactions
- Mock external dependencies

### Running Tests
```bash
# All tests
gradle test

# With coverage report
gradle test jacocoTestReport

# Specific test file
gradle test --tests "*AgentTest"

# Specific test method
gradle test --tests "*AgentTest.should initialize with correct properties"
```

## Documentation

### Code Documentation
- Add KDoc to all public APIs
- Include usage examples in KDoc
- Document exceptions that can be thrown
- Explain complex algorithms with inline comments

### README Updates
When adding features, update:
- Feature list
- Installation instructions (if dependencies change)
- Usage examples
- API documentation

### Example Files
Create example files for new features:
```kotlin
// examples/YourFeatureExample.kt
package examples

/**
 * Example demonstrating [describe what it shows]
 * 
 * This example shows how to:
 * 1. [First thing]
 * 2. [Second thing]
 */
fun main() {
    // Implementation with detailed comments
}
```

## Submitting Changes

### 1. Create a Feature Branch
```bash
git checkout -b feature/your-feature-name
```

### 2. Make Your Changes
- Write code following the style guide
- Add tests for new functionality
- Update documentation
- Ensure all tests pass

### 3. Commit Your Changes
```bash
# Use descriptive commit messages
git commit -m "feat: Add support for custom timeout in Tool execution"

# Commit message format:
# type: description
# 
# Types:
# - feat: New feature
# - fix: Bug fix
# - docs: Documentation changes
# - test: Test additions/changes
# - refactor: Code refactoring
# - style: Code style changes
# - perf: Performance improvements
```

### 4. Push and Create PR
```bash
git push origin feature/your-feature-name
```

Then create a Pull Request on GitHub with:
- Clear description of changes
- Link to related issues
- Test results
- Screenshots (if UI changes)

## Creating New Tools

### Tool Implementation Template
```kotlin
package com.connectonion.tools

import com.connectonion.core.*
import kotlinx.serialization.json.*

/**
 * [Describe what your tool does]
 * 
 * @see Tool
 */
class YourCustomTool : Tool() {
    override val name = "your_tool_name"
    override val description = "Clear description of what this tool does"
    
    override suspend fun run(parameters: Map<String, Any?>): ToolResult {
        // 1. Parameter validation
        val requiredParam = parameters["param_name"] as? String
            ?: return ToolResult(false, error = "Missing 'param_name' parameter")
        
        // 2. Input validation
        if (!isValid(requiredParam)) {
            return ToolResult(false, error = "Invalid parameter format")
        }
        
        return try {
            // 3. Tool logic
            val result = performOperation(requiredParam)
            
            // 4. Return success
            ToolResult(true, output = result)
        } catch (e: Exception) {
            // 5. Error handling
            logger.error(e) { "Tool execution failed" }
            ToolResult(false, error = "Operation failed: ${e.message}")
        }
    }
    
    override fun toFunctionSchema(): FunctionSchema {
        return FunctionSchema(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("param_name", buildJsonObject {
                        put("type", "string")
                        put("description", "Description of this parameter")
                    })
                })
                put("required", JsonArray(listOf(JsonPrimitive("param_name"))))
            }
        )
    }
}
```

### Tool Testing Template
```kotlin
class YourCustomToolTest : FunSpec({
    
    test("should execute successfully with valid parameters") {
        val tool = YourCustomTool()
        val result = tool.run(mapOf("param_name" to "value"))
        
        result.success shouldBe true
        result.output shouldContain "expected output"
    }
    
    test("should handle missing parameters") {
        val tool = YourCustomTool()
        val result = tool.run(emptyMap())
        
        result.success shouldBe false
        result.error shouldContain "Missing"
    }
})
```

## Adding LLM Providers

### LLM Implementation Template
```kotlin
package com.connectonion.llm

/**
 * [Provider name] implementation of LLM interface
 */
class YourLLM(
    private val apiKey: String,
    private val model: String = "default-model"
) : LLM() {
    
    private val client = // Initialize your HTTP client
    
    override suspend fun complete(
        messages: List<Message>,
        tools: List<FunctionSchema>?,
        temperature: Double
    ): LLMResponse {
        // 1. Convert messages to provider format
        val request = convertToProviderFormat(messages, tools, temperature)
        
        // 2. Make API call
        val response = client.post("api-endpoint") {
            setBody(request)
        }
        
        // 3. Parse response
        val providerResponse = response.body<ProviderResponse>()
        
        // 4. Convert to common format
        return LLMResponse(
            content = providerResponse.text,
            toolCalls = convertToolCalls(providerResponse.functions),
            finishReason = providerResponse.stopReason
        )
    }
    
    fun close() {
        client.close()
    }
}
```

## Code Review Checklist

Before submitting a PR, ensure:

- [ ] Code follows Kotlin coding conventions
- [ ] All public APIs have KDoc documentation
- [ ] Tests are included for new functionality
- [ ] All tests pass (`gradle test`)
- [ ] Code is formatted (`gradle ktlintFormat`)
- [ ] No compiler warnings
- [ ] Documentation is updated
- [ ] Examples are provided for new features
- [ ] Commit messages follow conventions
- [ ] PR description is clear and complete

## Getting Help

- **Discord**: [Join our community](https://discord.gg/4xfD9k8AUF)
- **Issues**: [GitHub Issues](https://github.com/connectonion/connectonion-kotlin/issues)
- **Discussions**: [GitHub Discussions](https://github.com/connectonion/connectonion-kotlin/discussions)

## License

By contributing, you agree that your contributions will be licensed under the MIT License.