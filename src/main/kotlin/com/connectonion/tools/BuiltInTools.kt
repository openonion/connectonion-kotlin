package com.connectonion.tools

import com.connectonion.core.FunctionSchema
import com.connectonion.core.Tool
import com.connectonion.core.ToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Collection of built-in tools commonly used by agents
 */
object BuiltInTools {
    
    /**
     * Tool for reading files from the filesystem
     */
    class FileReaderTool : Tool() {
        override val name = "read_file"
        override val description = "Read contents of a file from the filesystem"
        
        override suspend fun run(parameters: Map<String, Any?>): ToolResult {
            val path = parameters["path"] as? String
                ?: return ToolResult(false, error = "Missing 'path' parameter")
            
            return try {
                val file = File(path)
                if (!file.exists()) {
                    ToolResult(false, error = "File not found: $path")
                } else if (!file.isFile) {
                    ToolResult(false, error = "Path is not a file: $path")
                } else {
                    val content = file.readText()
                    ToolResult(true, output = content)
                }
            } catch (e: Exception) {
                ToolResult(false, error = "Error reading file: ${e.message}")
            }
        }
        
        override fun toFunctionSchema(): FunctionSchema {
            return FunctionSchema(
                name = name,
                description = description,
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "Path to the file to read")
                        })
                    })
                    put("required", buildJsonObject {
                        put("0", "path")
                    })
                }
            )
        }
    }
    
    /**
     * Tool for writing files to the filesystem
     */
    class FileWriterTool : Tool() {
        override val name = "write_file"
        override val description = "Write content to a file on the filesystem"
        
        override suspend fun run(parameters: Map<String, Any?>): ToolResult {
            val path = parameters["path"] as? String
                ?: return ToolResult(false, error = "Missing 'path' parameter")
            val content = parameters["content"] as? String
                ?: return ToolResult(false, error = "Missing 'content' parameter")
            
            return try {
                val file = File(path)
                file.parentFile?.mkdirs()
                file.writeText(content)
                ToolResult(true, output = "File written successfully to $path")
            } catch (e: Exception) {
                ToolResult(false, error = "Error writing file: ${e.message}")
            }
        }
        
        override fun toFunctionSchema(): FunctionSchema {
            return FunctionSchema(
                name = name,
                description = description,
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "Path where the file should be written")
                        })
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "Content to write to the file")
                        })
                    })
                    put("required", buildJsonObject {
                        put("0", "path")
                        put("1", "content")
                    })
                }
            )
        }
    }
    
    /**
     * Tool for fetching content from URLs
     */
    class WebFetchTool : Tool() {
        override val name = "fetch_url"
        override val description = "Fetch content from a URL"
        
        override suspend fun run(parameters: Map<String, Any?>): ToolResult {
            val url = parameters["url"] as? String
                ?: return ToolResult(false, error = "Missing 'url' parameter")
            
            return try {
                val content = URL(url).readText()
                ToolResult(true, output = content)
            } catch (e: Exception) {
                ToolResult(false, error = "Error fetching URL: ${e.message}")
            }
        }
        
        override fun toFunctionSchema(): FunctionSchema {
            return FunctionSchema(
                name = name,
                description = description,
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("url", buildJsonObject {
                            put("type", "string")
                            put("description", "URL to fetch content from")
                        })
                    })
                    put("required", buildJsonObject {
                        put("0", "url")
                    })
                }
            )
        }
    }
    
    /**
     * Tool for getting current date and time
     */
    class DateTimeTool : Tool() {
        override val name = "get_datetime"
        override val description = "Get the current date and time"
        
        override suspend fun run(parameters: Map<String, Any?>): ToolResult {
            val format = parameters["format"] as? String ?: "yyyy-MM-dd HH:mm:ss"
            
            return try {
                val formatter = DateTimeFormatter.ofPattern(format)
                val now = LocalDateTime.now().format(formatter)
                ToolResult(true, output = now)
            } catch (e: Exception) {
                ToolResult(false, error = "Error formatting date: ${e.message}")
            }
        }
        
        override fun toFunctionSchema(): FunctionSchema {
            return FunctionSchema(
                name = name,
                description = description,
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("format", buildJsonObject {
                            put("type", "string")
                            put("description", "Date format pattern (default: yyyy-MM-dd HH:mm:ss)")
                        })
                    })
                }
            )
        }
    }
    
    /**
     * Tool for executing shell commands
     */
    class ShellTool : Tool() {
        override val name = "execute_shell"
        override val description = "Execute a shell command"
        
        override suspend fun run(parameters: Map<String, Any?>): ToolResult {
            val command = parameters["command"] as? String
                ?: return ToolResult(false, error = "Missing 'command' parameter")
            
            return try {
                val process = ProcessBuilder(*command.split(" ").toTypedArray())
                    .redirectErrorStream(true)
                    .start()
                
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                
                if (exitCode == 0) {
                    ToolResult(true, output = output)
                } else {
                    ToolResult(false, error = "Command failed with exit code $exitCode: $output")
                }
            } catch (e: Exception) {
                ToolResult(false, error = "Error executing command: ${e.message}")
            }
        }
        
        override fun toFunctionSchema(): FunctionSchema {
            return FunctionSchema(
                name = name,
                description = description,
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("command", buildJsonObject {
                            put("type", "string")
                            put("description", "Shell command to execute")
                        })
                    })
                    put("required", buildJsonObject {
                        put("0", "command")
                    })
                }
            )
        }
    }
}