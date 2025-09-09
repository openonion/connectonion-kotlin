package com.connectonion.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ToolTest : FunSpec({
    
    test("FunctionTool should execute function successfully") {
        runTest {
        val tool = FunctionTool(
            name = "add",
            description = "Add two numbers",
            function = { params ->
                val a = (params["a"] as? Number)?.toDouble() ?: 0.0
                val b = (params["b"] as? Number)?.toDouble() ?: 0.0
                a + b
            },
            parameterSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("a", buildJsonObject { put("type", "number") })
                    put("b", buildJsonObject { put("type", "number") })
                })
            }
        )
        
        val result = tool.run(mapOf("a" to 5, "b" to 3))
        
            result.success shouldBe true
            result.output shouldBe "8.0"
            result.error shouldBe null
        }
    }
    
    test("FunctionTool should handle errors gracefully") {
        runTest {
        val tool = FunctionTool(
            name = "divide",
            description = "Divide two numbers",
            function = { params ->
                val a = (params["a"] as? Number)?.toDouble() ?: 0.0
                val b = (params["b"] as? Number)?.toDouble() ?: 0.0
                if (b == 0.0) throw IllegalArgumentException("Division by zero")
                a / b
            },
            parameterSchema = buildJsonObject {
                put("type", "object")
            }
        )
        
        val result = tool.run(mapOf("a" to 10, "b" to 0))
        
            result.success shouldBe false
            result.output shouldBe null
            result.error shouldBe "Division by zero"
        }
    }
    
    test("FunctionTool should generate correct schema") {
        val schema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("message", buildJsonObject {
                    put("type", "string")
                    put("description", "The message to echo")
                })
            })
            put("required", buildJsonObject {
                put("0", "message")
            })
        }
        
        val tool = FunctionTool(
            name = "echo",
            description = "Echo a message",
            function = { params -> params["message"] },
            parameterSchema = schema
        )
        
        val functionSchema = tool.toFunctionSchema()
        
        functionSchema.name shouldBe "echo"
        functionSchema.description shouldBe "Echo a message"
        functionSchema.parameters shouldBe schema
    }
    
    test("Custom Tool implementation") {
        runTest {
        class CustomTool : Tool() {
            override val name = "custom"
            override val description = "A custom tool"
            
            override suspend fun run(parameters: Map<String, Any?>): ToolResult {
                val input = parameters["input"] as? String ?: ""
                return ToolResult(
                    success = true,
                    output = input.uppercase()
                )
            }
            
            override fun toFunctionSchema(): FunctionSchema {
                return FunctionSchema(
                    name = name,
                    description = description,
                    parameters = buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("input", buildJsonObject {
                                put("type", "string")
                            })
                        })
                    }
                )
            }
        }
        
        val tool = CustomTool()
        val result = tool.run(mapOf("input" to "hello"))
        
            tool.name shouldBe "custom"
            tool.description shouldBe "A custom tool"
            result.success shouldBe true
            result.output shouldBe "HELLO"
        }
    }
})