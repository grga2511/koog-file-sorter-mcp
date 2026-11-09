import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.fromProcess
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import ai.koog.http.client.ktor.KtorKoogHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.http.Url

object FileSorterAgent {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking<Unit> {
        val apiKey = System.getenv("GOOGLE_API_KEY")
            ?.takeIf { it.isNotBlank() }
            ?: error("Set GOOGLE_API_KEY before running the agent.")

        val server = startMcpServer()

        try {
            val tools = McpToolRegistryProvider.fromProcess(
                process = server,
                clientInfo = Implementation("koog-file-sorter-mcp", "1.0.0")
            )

            val httpClient = HttpClient {
                engine {
                    proxy = ProxyBuilder.http(Url("http://192.168.255.60:8080"))
                }
            }

            val agent = AIAgent(
                promptExecutor = simpleGoogleAIExecutor(
                    apiKey = apiKey,
                    httpClientFactory = KtorKoogHttpClient.Factory(baseClient = httpClient)
                ),
                llmModel = GoogleModels.Gemini2_5Flash,
                toolRegistry = tools,
                systemPrompt = """
                    Inspect the test project using the MCP tools.
                    First call list_files. Then use read_text_file to verify
                    which file contains application configuration.
                    Do not infer the answer from the file name alone.
                    Answer in the user's language.Answer only the question asked. 
                    
                """.trimIndent()
            )

            try {
                println(agent.run(
                    "Find file that has pdf extension of the app and tell me how it's named."
                ))
                println(agent.run(
                    """
                Pronađi datoteku koja sadrži konfiguracijske parametre aplikacije i reci mi kako se zove.
                Najprije pozovi list_files, zatim pročitaj kandidata alatom read_text_file.
                Kad potvrdiš da sadržaj uključuje konfiguracijske parametre, vrati samo naziv datoteke.
                """
                ))
            } finally {
                agent.close()
                httpClient.close()
            }
        } finally {
            server.destroy()
        }
    }

    private fun startMcpServer(): Process {
        val javaExecutable = Path.of(
            System.getProperty("java.home"), "bin", "java.exe"
        )

        return ProcessBuilder(
            javaExecutable.toString(),
            "-cp",
            System.getProperty("java.class.path"),
            "FileMcpServerKt"
        )
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
    }
}