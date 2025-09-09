package com.connectonion.tools

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BuiltInToolsTest : FunSpec({
    
    test("FileReaderTool should read existing file") {
        runTest {
            // Create a temporary test file
            val tempFile = File.createTempFile("test", ".txt")
            tempFile.writeText("Hello, World!")
            tempFile.deleteOnExit()
            
            val tool = BuiltInTools.FileReaderTool()
            val result = tool.run(mapOf("path" to tempFile.absolutePath))
        
            result.success shouldBe true
            result.output shouldBe "Hello, World!"
            result.error shouldBe null
        }
    }
    
    test("FileReaderTool should handle non-existent file") {
        runTest {
            val tool = BuiltInTools.FileReaderTool()
            val result = tool.run(mapOf("path" to "/non/existent/file.txt"))
        
            result.success shouldBe false
            result.output shouldBe null
            result.error shouldContain "File not found"
        }
    }
    
    test("FileReaderTool should handle missing parameter") {
        runTest {
            val tool = BuiltInTools.FileReaderTool()
            val result = tool.run(emptyMap())
        
            result.success shouldBe false
            result.error shouldContain "Missing 'path' parameter"
        }
    }
    
    test("FileWriterTool should write to file") {
        runTest {
            val tempDir = File.createTempFile("test", "").parentFile
            val testFile = File(tempDir, "test_write_${System.currentTimeMillis()}.txt")
            testFile.deleteOnExit()
            
            val tool = BuiltInTools.FileWriterTool()
            val result = tool.run(mapOf(
                "path" to testFile.absolutePath,
                "content" to "Test content"
            ))
        
            result.success shouldBe true
            result.output shouldContain "File written successfully"
            testFile.exists() shouldBe true
            testFile.readText() shouldBe "Test content"
        }
    }
    
    test("FileWriterTool should create parent directories") {
        runTest {
            val tempDir = File.createTempFile("test", "").parentFile
            val testDir = File(tempDir, "nested/dirs/test_${System.currentTimeMillis()}")
            val testFile = File(testDir, "file.txt")
            testFile.deleteOnExit()
            testDir.deleteOnExit()
            
            val tool = BuiltInTools.FileWriterTool()
            val result = tool.run(mapOf(
                "path" to testFile.absolutePath,
                "content" to "Nested content"
            ))
        
            result.success shouldBe true
            testFile.exists() shouldBe true
            testFile.readText() shouldBe "Nested content"
        }
    }
    
    test("DateTimeTool should return current datetime") {
        runTest {
            val tool = BuiltInTools.DateTimeTool()
            val result = tool.run(emptyMap())
        
            result.success shouldBe true
            result.output shouldNotBe null
            
            // Verify format by trying to parse it
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val parsed = LocalDateTime.parse(result.output, formatter)
            parsed shouldNotBe null
        }
    }
    
    test("DateTimeTool should use custom format") {
        runTest {
            val tool = BuiltInTools.DateTimeTool()
            val result = tool.run(mapOf("format" to "yyyy-MM-dd"))
        
            result.success shouldBe true
            result.output shouldNotBe null
            
            // Verify the output matches the expected format (date only)
            result.output!!.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) shouldBe true
        }
    }
    
    test("Tool schemas should be properly formatted") {
        val readerTool = BuiltInTools.FileReaderTool()
        val schema = readerTool.toFunctionSchema()
        
        schema.name shouldBe "read_file"
        schema.description shouldBe "Read contents of a file from the filesystem"
        schema.parameters.toString() shouldContain "path"
    }
})