package com.connectonion.config

import java.io.File
import java.io.FileInputStream
import java.util.Properties

/**
 * @purpose Configuration loader for API keys and environment variables - searches .env files and system env
 * @llm-note
 *   Dependencies: imports from [java.io.*, java.util.Properties] | imported by [examples/BasicAgent.kt, examples/AgentWithTools.kt, examples/TestRunner.kt] | no direct tests
 *   Data flow: init → loadEnvFile() searches [.env, ../.env, ../../.env] → loads to Properties | get(key) checks System.getenv() → properties → defaultValue
 *   State/Effects: mutates internal properties: Properties on init | reads filesystem for .env files | prints to console on successful load
 *   Integration: exposes get(key, default), getRequired(key), has(key), getOpenAIKey() | used by examples for Config.getOpenAIKey() | Singleton object pattern
 *   Performance: loads .env once during init | O(1) lookups via Properties | searches up to 3 parent directories
 *   Errors: throws ConfigurationException if required key missing | prints warning if .env file unreadable | helpful error message in getOpenAIKey()
 */
object Config {
    private val properties = Properties()
    private val envLoaded: Boolean
    
    init {
        envLoaded = loadEnvFile()
    }
    
    /**
     * Load .env file from current directory or parent directories
     */
    private fun loadEnvFile(): Boolean {
        val envPaths = listOf(
            ".env",
            "../.env",
            "../../.env"
        )
        
        for (path in envPaths) {
            val file = File(path)
            if (file.exists() && file.isFile) {
                try {
                    FileInputStream(file).use { input ->
                        properties.load(input)
                    }
                    println("Loaded environment from: ${file.absolutePath}")
                    return true
                } catch (e: Exception) {
                    println("Warning: Could not load .env file from $path: ${e.message}")
                }
            }
        }
        return false
    }
    
    /**
     * Get configuration value from environment variable or .env file
     * Priority: System environment > .env file > default value
     */
    fun get(key: String, defaultValue: String? = null): String? {
        // First check system environment
        System.getenv(key)?.let { return it }
        
        // Then check loaded .env properties
        properties.getProperty(key)?.let { return it }
        
        // Return default value
        return defaultValue
    }
    
    /**
     * Get required configuration value (throws exception if not found)
     */
    fun getRequired(key: String): String {
        return get(key) ?: throw ConfigurationException("Required configuration key not found: $key")
    }
    
    /**
     * Check if a configuration key exists
     */
    fun has(key: String): Boolean {
        return System.getenv(key) != null || properties.containsKey(key)
    }
    
    /**
     * Get OpenAI API key with helpful error message
     */
    fun getOpenAIKey(): String {
        return get("OPENAI_API_KEY") ?: throw ConfigurationException(
            """
            |OpenAI API key not found!
            |Please set OPENAI_API_KEY in one of these ways:
            |1. Set environment variable: export OPENAI_API_KEY=your-key
            |2. Create .env file in project root with: OPENAI_API_KEY=your-key
            |3. Pass it directly to OpenAILLM constructor
            """.trimMargin()
        )
    }
}

/**
 * Exception thrown when required configuration is missing
 */
class ConfigurationException(message: String) : RuntimeException(message)