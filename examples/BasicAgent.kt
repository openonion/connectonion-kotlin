package examples

import com.connectonion.config.Config
import com.connectonion.core.Agent
import com.connectonion.llm.OpenAILLM
import kotlinx.coroutines.runBlocking

/**
 * Basic Agent Example - Simple Q&A without tools
 * 
 * This example demonstrates:
 * 1. Creating a basic agent without any tools
 * 2. Maintaining conversation context across multiple messages
 * 3. Clearing conversation history to start fresh
 * 4. Proper resource cleanup
 * 
 * Prerequisites:
 * - Set OPENAI_API_KEY in environment variables or .env file
 * - Internet connection for OpenAI API calls
 */
fun main() = runBlocking {
    // ===== STEP 1: Configuration =====
    // The Config object automatically loads from environment variables or .env file
    // Priority: System env > .env file > default value
    val apiKey = try {
        Config.getOpenAIKey() // Throws helpful error if not found
    } catch (e: Exception) {
        println("Error: ${e.message}")
        println("\nTo run this example, please set your OpenAI API key:")
        println("1. Create a .env file in the project root")
        println("2. Add: OPENAI_API_KEY=your-api-key-here")
        println("3. Or set environment variable: export OPENAI_API_KEY=your-api-key")
        return@runBlocking // Exit if no API key
    }
    
    // ===== STEP 2: Initialize Language Model =====
    // OpenAILLM handles all communication with OpenAI's API
    val llm = OpenAILLM(
        apiKey = apiKey,
        model = "gpt-4",     // You can also use "gpt-3.5-turbo" for faster/cheaper responses
        maxTokens = 500      // Limit response length to control costs
    )
    
    // ===== STEP 3: Create Agent =====
    // The agent orchestrates the conversation flow
    val agent = Agent(
        name = "basic-assistant",  // Unique identifier for behavior tracking
        llm = llm,                 // The language model to use
        systemPrompt = """
            You are a helpful, concise assistant. 
            Keep your responses brief and to the point.
            Focus on accuracy and clarity.
        """.trimIndent(),          // Sets the agent's behavior and personality
        temperature = 0.7          // Balance between creativity (1.0) and consistency (0.0)
    )
    
    println("=== Basic Agent Example ===")
    println("Demonstrating conversation with context preservation\n")
    
    // ===== STEP 4: Multi-turn Conversation =====
    // The agent maintains context across multiple interactions
    
    // First question - establishing topic
    println("User: What is the capital of Japan?")
    val response1 = agent.run("What is the capital of Japan?")
    println("Agent: $response1\n")
    
    // Follow-up question - uses context from previous message
    // Notice we say "its" - the agent knows we're still talking about Tokyo
    println("User: What is its population?")
    val response2 = agent.run("What is its population?")
    println("Agent: $response2\n")
    
    // Another follow-up - agent maintains full conversation history
    println("User: Tell me three interesting facts about it.")
    val response3 = agent.run("Tell me three interesting facts about it.")
    println("Agent: $response3\n")
    
    // ===== STEP 5: Clearing Context =====
    // Sometimes you want to start a fresh conversation
    agent.clearMessages()  // Removes all messages except system prompt
    
    println("--- Conversation cleared ---")
    println("Starting fresh conversation (context is reset)\n")
    
    // New conversation - agent has no memory of previous discussion
    println("User: Who wrote Romeo and Juliet?")
    val response4 = agent.run("Who wrote Romeo and Juliet?")
    println("Agent: $response4")
    
    // If we asked about "it" now, the agent wouldn't know what we mean
    // since the context about Tokyo/Japan has been cleared
    
    println("\n=== Example Complete ===")
    
    // ===== STEP 6: Cleanup =====
    // Important: Always close the HTTP client to free resources
    llm.close()
    
    // The conversation history is automatically saved to:
    // ~/.connectonion/agents/basic-assistant/behavior.json
    // You can review all interactions there for debugging or analysis
}

/*
 * KEY CONCEPTS DEMONSTRATED:
 * 
 * 1. CONTEXT PRESERVATION:
 *    - The agent remembers previous messages in the conversation
 *    - This allows for natural follow-up questions
 *    - Context is stored in the agent's message history
 * 
 * 2. SYSTEM PROMPTS:
 *    - Define the agent's personality and behavior
 *    - Remain constant throughout the conversation
 *    - Survive message clearing (unlike user/assistant messages)
 * 
 * 3. TEMPERATURE SETTING:
 *    - 0.0 = Very consistent, deterministic responses
 *    - 0.7 = Balanced creativity and consistency (recommended)
 *    - 1.0+ = More creative, varied responses
 * 
 * 4. BEHAVIOR TRACKING:
 *    - All interactions are logged to ~/.connectonion/
 *    - Useful for debugging and understanding agent decisions
 *    - Persists across program runs
 * 
 * NEXT STEPS:
 * - Try modifying the system prompt to change agent behavior
 * - Experiment with different temperature values
 * - Add tools to give the agent capabilities (see AgentWithTools.kt)
 */