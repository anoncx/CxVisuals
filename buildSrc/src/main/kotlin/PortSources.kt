import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class PortSources : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: DirectoryProperty

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val overlay: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rules: DirectoryProperty

    @get:Input
    abstract val kind: Property<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun port() {
        val out = output.get().asFile
        out.deleteRecursively()
        val transform = transform(rules.get().asFile)
        val root = source.get().asFile
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val target = File(out, file.relativeTo(root).path)
            target.parentFile.mkdirs()
            if (file.extension in TEXT) {
                target.writeText(transform(file.extension, file.readText()))
            } else {
                file.copyTo(target, overwrite = true)
            }
        }
        if (overlay.isPresent) {
            overlay.get().asFile.takeIf { it.isDirectory }?.copyRecursively(out, overwrite = true)
        }
    }

    private fun transform(dir: File): (String, String) -> String {
        val imports = lines(File(dir, "imports.txt")).map { it.substringBefore('=') to it.substringAfter('=') }
        val java = lines(File(dir, "java.rules")).map(::rule)
        val shader = lines(File(dir, "shader.rules")).map(::rule)
        return { extension, text ->
            when {
                kind.get() == "java" && extension == "java" ->
                    java.fold(imports.fold(text) { acc, (new, old) ->
                        acc.replace(new, old).replace(new.replace('.', '/'), old.replace('.', '/'))
                    }) { acc, (regex, replacement) -> regex.replace(acc, replacement) }
                kind.get() == "shader" && extension in SHADER ->
                    shader.fold(text) { acc, (regex, replacement) -> regex.replace(acc, replacement) }
                else -> text
            }
        }
    }

    private fun lines(file: File): List<String> =
        if (file.isFile) file.readLines().filter { it.isNotBlank() } else emptyList()

    private fun rule(line: String): Pair<Regex, String> {
        val parts = line.split('\t')
        return Regex(parts[0].replace("\\n", "\n"), RegexOption.MULTILINE) to parts[1].replace("\\n", "\n")
    }

    private companion object {
        val TEXT = setOf("java", "vsh", "fsh", "glsl")
        val SHADER = setOf("vsh", "fsh", "glsl")
    }
}
