import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path


fun main() = runBlocking<Unit> {
    val folder = Path.of("Testing").toAbsolutePath().normalize()
    check(Files.isDirectory(folder, NOFOLLOW_LINKS)) { //NOFOLLOW_LINKS u prijevodu kaze: Ako je file obična datoteka, provjera vraća true. Ako je simbolička poveznica na neku drugu datoteku, vraća false
        "Expected the Testing folder at $folder."
    }

    val server = Server(
        serverInfo = Implementation("Testing-files", "1.0.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false)
            )
        )
    )
    System.err.println("MCP: list_files")
    server.addTool(

        name = "list_files",
        description = "List the file names in the test project folder."
    ) {
        val names = Files.list(folder).use { entries ->
            entries
                .filter { Files.isRegularFile(it, NOFOLLOW_LINKS) } //NOFOLLOW_LINKS
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }
        toolResult(names.joinToString("\n").ifEmpty { "No files found." })
    }

    server.addTool(
        name = "read_text_file",
        description = "Read a UTF-8 text file from the test project folder. Supports .txt and .conf.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("fileName", buildJsonObject {
                    put("type", "string")
                    put("description", "File name from list_files, without a path.")
                })
            },
            required = listOf("fileName")
        )
    ) { request ->
        val fileName = (request.arguments?.get("fileName") as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content

        if (fileName.isNullOrBlank() || fileName.any { it in "/\\:" }) {
            return@addTool toolResult("Provide a file name without a path.", isError = true)
        }

        if (fileName.substringAfterLast('.', "").lowercase() !in setOf("txt", "conf")) {
            return@addTool toolResult("Only .txt and .conf files can be read.", isError = true)
        }

        val file = folder.resolve(fileName)
        if (!Files.isRegularFile(file, NOFOLLOW_LINKS)) {
            return@addTool toolResult("File not found in the test folder.", isError = true)
        }

        try {
            val content = Files.readString(file)
            System.err.println("MCP: read_text_file ($fileName)")
            toolResult(content)
        } catch (_: IOException) {
            toolResult("Could not read the file as UTF-8 text.", isError = true)
        }
    }

    server.createSession(
        StdioServerTransport(
            inputStream = System.`in`.asSource().buffered(),
            outputStream = System.out.asSink().buffered()
        )
    )
    awaitCancellation()
}

private fun toolResult(text: String, isError: Boolean = false) =
    CallToolResult(content = listOf(TextContent(text)), isError = isError)