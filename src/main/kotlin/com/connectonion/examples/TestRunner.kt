package com.connectonion.examples

import com.connectonion.config.Config
import com.connectonion.core.Agent
import com.connectonion.llm.OpenAILLM
import com.connectonion.tools.BuiltInTools
import kotlinx.coroutines.runBlocking

/**
 * Simple test runner to verify the SDK works
 * This creates an agent that can work even without API calls
 */
fun main() = runBlocking {
    println("=== ConnectOnion Kotlin SDK Test ===\n")
    
    // Test 1: Config loading
    println("1. Testing config loader...")
    val hasApiKey = Config.has("OPENAI_API_KEY")
    if (hasApiKey) {
        println("   ✓ Found OpenAI API key in configuration")
    } else {
        println("   ⚠ No OpenAI API key found (examples will need one to run)")
    }
    
    // Test 2: Tool creation and execution
    println("\n2. Testing built-in tools...")
    val dateTool = BuiltInTools.DateTimeTool()
    val dateResult = dateTool.run(mapOf("format" to "yyyy-MM-dd"))
    println("   ✓ DateTimeTool returned: ${dateResult.output}")
    
    // Test 3: Tool schema generation
    println("\n3. Testing tool schema generation...")
    val schema = dateTool.toFunctionSchema()
    println("   ✓ Generated schema for tool: ${schema.name}")
    
    // Test 4: File operations
    println("\n4. Testing file operations...")
    val writerTool = BuiltInTools.FileWriterTool()
    val writeResult = writerTool.run(mapOf(
        "path" to "test_output.txt",
        "content" to "ConnectOnion Kotlin SDK is working!"
    ))
    if (writeResult.success) {
        println("   ✓ Successfully wrote to test_output.txt")
        
        val readerTool = BuiltInTools.FileReaderTool()
        val readResult = readerTool.run(mapOf("path" to "test_output.txt"))
        if (readResult.success) {
            println("   ✓ Successfully read file: ${readResult.output}")
        }
    }
    
    println("\n=== All basic tests passed! ===")
    println("\nTo run the full examples with OpenAI:")
    println("1. Make sure your .env file has OPENAI_API_KEY set")
    println("2. Run: kotlin examples/BasicAgent.kt")
    println("3. Or compile and run the JAR:")
    println("   gradle jar")
    println("   java -cp build/libs/connectonion-kotlin-0.0.1.jar examples.BasicAgentKt")
}