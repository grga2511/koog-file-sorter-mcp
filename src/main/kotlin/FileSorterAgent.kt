import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

object FileSorterAgent {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking<Unit> {
        val projectRoot = Path.of("")
            .toAbsolutePath()
            .normalize()
            .toString()

        val directory = "Moretests"

        val task = "Find the files containing application configuration and return their names."

        val mcpUrl = requiredEnv("MCP_URL")
        val apiKey = requiredEnv("GOOGLE_API_KEY")
        val modelId = requiredEnv("GEMINI_MODEL")
        val proxyUrl = optionalEnv("GEMINI_PROXY_URL")
        val systemPrompt = optionalEnv("AGENT_SYSTEM_PROMPT").orEmpty()

        val mcpHttpClient = HttpClient {
            install(SSE)
        }

        val geminiHttpClient = HttpClient {
            if (proxyUrl != null) {
                engine {
                    proxy = ProxyBuilder.http(Url(proxyUrl))
                }
            }
        }

        try {
            val tools = McpToolRegistryProvider.streamableHttp {
                url = mcpUrl
                httpClient = mcpHttpClient
            }

            val googleClient = GoogleLLMClient(
                apiKey = apiKey,
                httpClientFactory = KtorKoogHttpClient.Factory(
                    baseClient = geminiHttpClient
                )
            )

            val model = when (modelId.removePrefix("models/")) {
                "gemini-2.5-flash" -> GoogleModels.Gemini2_5Flash
                "gemini-2.5-pro" -> GoogleModels.Gemini2_5Pro
                else -> error("Unsupported Gemini model: $modelId")
            }

            val agent = AIAgent(
                promptExecutor = MultiLLMPromptExecutor(googleClient),
                llmModel = model,
                toolRegistry = tools,
                systemPrompt = systemPrompt
            )

            try {
                val request = """
                    Project root: $projectRoot
                    Directory: $directory
                    Task: $task

                    Pass projectRoot and directory as separate arguments
                    when calling list_files and read_text_file.
                    The directory is relative to the project root.
                    Read candidate files before deciding which ones match.
                    """.trimIndent()

                println(agent.run(request))
            } finally {
                agent.close()
            }
        } finally {
            geminiHttpClient.close()
            mcpHttpClient.close()
        }
    }

    private fun optionalEnv(name: String): String? =
        System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

    private fun requiredEnv(name: String): String =
        optionalEnv(name) ?: error("Missing environment variable: $name")
}