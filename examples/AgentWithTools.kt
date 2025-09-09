package examples

import com.connectonion.config.Config
import com.connectonion.core.Agent
import com.connectonion.llm.OpenAILLM
import com.connectonion.tools.BuiltInTools
import kotlinx.coroutines.runBlocking

/**
 * Agent with tools example - File operations and data fetching
 */
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
    
    // Create agent with built-in tools
    val agent = Agent(
        name = "tool-assistant",
        llm = llm,
        tools = listOf(
            BuiltInTools.FileReaderTool(),
            BuiltInTools.FileWriterTool(),
            BuiltInTools.DateTimeTool(),
            BuiltInTools.WebFetchTool()
        ),
        systemPrompt = """You are a helpful assistant with access to various tools.
            |You can read and write files, get the current date/time, and fetch web content.
            |Use these tools when appropriate to help the user.""".trimMargin(),
        temperature = 0.7
    )
    
    println("=== Agent with Tools Example ===\n")
    
    // Example 1: Get current date/time
    println("User: What's the current date and time?")
    val response1 = agent.run("What's the current date and time?")
    println("Agent: $response1\n")
    
    // Example 2: Write a file
    println("User: Create a file called 'notes.txt' with a reminder to buy groceries.")
    val response2 = agent.run(
        "Create a file called 'notes.txt' in the current directory with the content: 'Remember to buy groceries: milk, eggs, bread'"
    )
    println("Agent: $response2\n")
    
    // Example 3: Read the file back
    println("User: What's in the notes.txt file?")
    val response3 = agent.run("Read the contents of the notes.txt file")
    println("Agent: $response3\n")
    
    // Example 4: Complex task with multiple tools
    println("User: Create a summary file with today's date")
    val response4 = agent.run(
        """Create a file called 'daily_summary.txt' that contains:
        |1. Today's date
        |2. A brief note saying 'Daily summary created by ConnectOnion Agent'
        |3. The contents of notes.txt if it exists""".trimMargin()
    )
    println("Agent: $response4\n")
    
    // Clean up
    llm.close()
    
    println("\n=== Example Complete ===")
    println("Check the current directory for created files: notes.txt and daily_summary.txt")
}