package com.connectonion.core

import com.connectonion.llm.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive

class AgentTest : FunSpec({
    
    test("Agent should initialize with correct properties") {
        val mockLLM = mockk<LLM>()
        val agent = Agent(
            name = "test-agent",
            llm = mockLLM,
            systemPrompt = "Test prompt",
            temperature = 0.5
        )
        
        agent.name shouldBe "test-agent"
        agent.getMessages().size shouldBe 1
        agent.getMessages()[0].role shouldBe MessageRole.SYSTEM
        agent.getMessages()[0].content shouldBe "Test prompt"
    }
    
    test("Agent should execute simple prompt without tools") {
        runTest {
        val mockLLM = mockk<LLM>()
        
        coEvery { 
            mockLLM.complete(any(), any(), any()) 
        } returns LLMResponse(
            content = "Paris is the capital of France",
            finishReason = "stop"
        )
        
        val agent = Agent(
            name = "test-agent",
            llm = mockLLM
        )
        
        val response = agent.run("What is the capital of France?")
        
            response shouldBe "Paris is the capital of France"
            agent.getMessages().size shouldBe 2 // user + assistant
        }
    }
    
    test("Agent should execute tool calls") {
        runTest {
        val mockLLM = mockk<LLM>()
        val mockTool = mockk<Tool>()
        
        every { mockTool.name } returns "test_tool"
        every { mockTool.description } returns "A test tool"
        every { mockTool.toFunctionSchema() } returns FunctionSchema(
            name = "test_tool",
            description = "A test tool",
            parameters = kotlinx.serialization.json.buildJsonObject {}
        )
        
        // First call: LLM requests tool use
        coEvery { 
            mockLLM.complete(any(), any(), any()) 
        } returnsMany listOf(
            LLMResponse(
                content = null,
                toolCalls = listOf(
                    ToolCall(
                        id = "call_123",
                        name = "test_tool",
                        arguments = JsonPrimitive("{}")
                    )
                ),
                finishReason = "tool_calls"
            ),
            LLMResponse(
                content = "Tool executed successfully",
                finishReason = "stop"
            )
        )
        
        coEvery { 
            mockTool.run(any()) 
        } returns ToolResult(
            success = true,
            output = "Tool output"
        )
        
        val agent = Agent(
            name = "test-agent",
            llm = mockLLM,
            tools = listOf(mockTool)
        )
        
        val response = agent.run("Use the test tool")
        
            response shouldBe "Tool executed successfully"
            coVerify { mockTool.run(any()) }
        }
    }
    
    test("Agent should handle tool execution errors") {
        runTest {
        val mockLLM = mockk<LLM>()
        val mockTool = mockk<Tool>()
        
        every { mockTool.name } returns "failing_tool"
        every { mockTool.description } returns "A failing tool"
        every { mockTool.toFunctionSchema() } returns FunctionSchema(
            name = "failing_tool",
            description = "A failing tool",
            parameters = kotlinx.serialization.json.buildJsonObject {}
        )
        
        coEvery { 
            mockLLM.complete(any(), any(), any()) 
        } returnsMany listOf(
            LLMResponse(
                toolCalls = listOf(
                    ToolCall(
                        id = "call_456",
                        name = "failing_tool",
                        arguments = JsonPrimitive("{}")
                    )
                )
            ),
            LLMResponse(
                content = "I encountered an error",
                finishReason = "stop"
            )
        )
        
        coEvery { 
            mockTool.run(any()) 
        } returns ToolResult(
            success = false,
            error = "Tool failed"
        )
        
        val agent = Agent(
            name = "test-agent",
            llm = mockLLM,
            tools = listOf(mockTool)
        )
        
        val response = agent.run("Use the failing tool")
        
            response shouldBe "I encountered an error"
            coVerify { mockTool.run(any()) }
        }
    }
    
    test("Agent should respect max iterations") {
        runTest {
        val mockLLM = mockk<LLM>()
        val mockTool = mockk<Tool>()
        
        every { mockTool.name } returns "loop_tool"
        every { mockTool.toFunctionSchema() } returns FunctionSchema(
            name = "loop_tool",
            description = "A looping tool",
            parameters = kotlinx.serialization.json.buildJsonObject {}
        )
        
        // Always return tool calls to simulate infinite loop
        coEvery { 
            mockLLM.complete(any(), any(), any()) 
        } returns LLMResponse(
            toolCalls = listOf(
                ToolCall(
                    id = "call_loop",
                    name = "loop_tool",
                    arguments = JsonPrimitive("{}")
                )
            )
        )
        
        coEvery { 
            mockTool.run(any()) 
        } returns ToolResult(success = true, output = "Loop")
        
        val agent = Agent(
            name = "test-agent",
            llm = mockLLM,
            tools = listOf(mockTool),
            maxIterations = 3
        )
        
        agent.run("Start looping")
        
            // Verify tool was called exactly 3 times (max iterations)
            coVerify(exactly = 3) { mockTool.run(any()) }
        }
    }
    
    test("Agent should clear messages") {
        val mockLLM = mockk<LLM>()
        
        coEvery { 
            mockLLM.complete(any(), any(), any()) 
        } returns LLMResponse(content = "Response")
        
        val agent = Agent(
            name = "test-agent",
            llm = mockLLM,
            systemPrompt = "System"
        )
        
        runTest {
            agent.run("First message")
            agent.getMessages().size shouldBe 3 // system + user + assistant
            
            agent.clearMessages()
            agent.getMessages().size shouldBe 1 // only system prompt
            agent.getMessages()[0].role shouldBe MessageRole.SYSTEM
        }
    }
})