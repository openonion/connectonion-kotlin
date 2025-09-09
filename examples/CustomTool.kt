package examples

import com.connectonion.config.Config
import com.connectonion.core.*
import com.connectonion.llm.OpenAILLM
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.*

/**
 * Custom tool example - Creating specialized tools for agents
 */

// Custom calculator tool
class AdvancedCalculatorTool : Tool() {
    override val name = "advanced_calculator"
    override val description = "Perform advanced mathematical calculations including trigonometry and logarithms"
    
    override suspend fun run(parameters: Map<String, Any?>): ToolResult {
        val operation = parameters["operation"] as? String 
            ?: return ToolResult(false, error = "Missing operation")
        val value = (parameters["value"] as? Number)?.toDouble()
        val value2 = (parameters["value2"] as? Number)?.toDouble()
        
        val result = try {
            when (operation) {
                "sqrt" -> sqrt(value ?: return ToolResult(false, error = "Missing value"))
                "pow" -> value?.pow(value2 ?: 2.0) ?: return ToolResult(false, error = "Missing value")
                "sin" -> sin(Math.toRadians(value ?: return ToolResult(false, error = "Missing value")))
                "cos" -> cos(Math.toRadians(value ?: return ToolResult(false, error = "Missing value")))
                "tan" -> tan(Math.toRadians(value ?: return ToolResult(false, error = "Missing value")))
                "log" -> ln(value ?: return ToolResult(false, error = "Missing value"))
                "log10" -> log10(value ?: return ToolResult(false, error = "Missing value"))
                "abs" -> abs(value ?: return ToolResult(false, error = "Missing value"))
                else -> return ToolResult(false, error = "Unknown operation: $operation")
            }
        } catch (e: Exception) {
            return ToolResult(false, error = "Calculation error: ${e.message}")
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
                            put("0", "sqrt")
                            put("1", "pow")
                            put("2", "sin")
                            put("3", "cos")
                            put("4", "tan")
                            put("5", "log")
                            put("6", "log10")
                            put("7", "abs")
                        })
                        put("description", "Mathematical operation to perform")
                    })
                    put("value", buildJsonObject {
                        put("type", "number")
                        put("description", "Primary value for calculation")
                    })
                    put("value2", buildJsonObject {
                        put("type", "number")
                        put("description", "Secondary value (for pow operation)")
                    })
                })
                put("required", buildJsonObject {
                    put("0", "operation")
                    put("1", "value")
                })
            }
        )
    }
}

// Custom text processing tool
class TextAnalyzerTool : Tool() {
    override val name = "text_analyzer"
    override val description = "Analyze text for various metrics like word count, character count, etc."
    
    override suspend fun run(parameters: Map<String, Any?>): ToolResult {
        val text = parameters["text"] as? String 
            ?: return ToolResult(false, error = "Missing text parameter")
        val metric = parameters["metric"] as? String 
            ?: return ToolResult(false, error = "Missing metric parameter")
        
        val result = when (metric) {
            "word_count" -> text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
            "char_count" -> text.length
            "char_count_no_spaces" -> text.replace(" ", "").length
            "line_count" -> text.lines().size
            "uppercase" -> text.uppercase()
            "lowercase" -> text.lowercase()
            "reverse" -> text.reversed()
            "unique_words" -> text.split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .map { it.lowercase() }
                .distinct()
                .size
            else -> return ToolResult(false, error = "Unknown metric: $metric")
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
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "Text to analyze")
                    })
                    put("metric", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonObject {
                            put("0", "word_count")
                            put("1", "char_count")
                            put("2", "char_count_no_spaces")
                            put("3", "line_count")
                            put("4", "uppercase")
                            put("5", "lowercase")
                            put("6", "reverse")
                            put("7", "unique_words")
                        })
                        put("description", "Metric to calculate")
                    })
                })
                put("required", buildJsonObject {
                    put("0", "text")
                    put("1", "metric")
                })
            }
        )
    }
}

fun main() = runBlocking {
    // Get API key from environment or .env file
    val apiKey = try {
        Config.getOpenAIKey()
    } catch (e: Exception) {
        println("Error: ${e.message}")
        println("\nTo run this example, please set your OpenAI API key.")
        return@runBlocking
    }
    
    // Initialize OpenAI LLM
    val llm = OpenAILLM(
        apiKey = apiKey,
        model = "gpt-4"
    )
    
    // Create agent with custom tools
    val agent = Agent(
        name = "custom-tool-agent",
        llm = llm,
        tools = listOf(
            AdvancedCalculatorTool(),
            TextAnalyzerTool()
        ),
        systemPrompt = """You are a helpful assistant with access to advanced calculator and text analysis tools.
            |Use these tools to help users with mathematical calculations and text analysis tasks.""".trimMargin(),
        temperature = 0.7
    )
    
    println("=== Custom Tools Example ===\n")
    
    // Math examples
    println("User: What's the square root of 144?")
    val response1 = agent.run("What's the square root of 144?")
    println("Agent: $response1\n")
    
    println("User: Calculate 2 to the power of 10")
    val response2 = agent.run("Calculate 2 to the power of 10")
    println("Agent: $response2\n")
    
    println("User: What's the sine of 30 degrees?")
    val response3 = agent.run("What's the sine of 30 degrees?")
    println("Agent: $response3\n")
    
    // Text analysis examples
    println("User: How many words are in 'The quick brown fox jumps over the lazy dog'?")
    val response4 = agent.run("How many words are in 'The quick brown fox jumps over the lazy dog'?")
    println("Agent: $response4\n")
    
    println("User: Reverse the text 'ConnectOnion Kotlin SDK'")
    val response5 = agent.run("Reverse the text 'ConnectOnion Kotlin SDK'")
    println("Agent: $response5\n")
    
    // Complex example using multiple tools
    println("User: Calculate the square root of the number of unique words in 'to be or not to be that is the question'")
    val response6 = agent.run(
        "Calculate the square root of the number of unique words in 'to be or not to be that is the question'"
    )
    println("Agent: $response6\n")
    
    // Clean up
    llm.close()
    
    println("=== Example Complete ===")
}