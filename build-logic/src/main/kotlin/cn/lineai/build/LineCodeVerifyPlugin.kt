package cn.lineai.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest
import java.util.regex.Pattern

/**
 * Mechanical architecture guards for the rewritten modules.
 *
 * These run on every `check` of a module that applies `linecode.verify`. Legacy modules are
 * intentionally excluded: they are frozen and carry the technical debt the rewrite removes.
 */
class LineCodeVerifyPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val sources = files(
                layout.projectDirectory.dir("src/main/java"),
                layout.projectDirectory.dir("src/local/java")
            )
            val guards = mutableListOf(
                tasks.register("verifyNoCjkLiterals", VerifyNoCjkLiteralsTask::class.java) {
                    this.sources.from(sources)
                },
                tasks.register("verifyNoRawThreads", VerifyNoRawThreadsTask::class.java) {
                    this.sources.from(sources)
                },
                tasks.register("verifySizeBudget", VerifySizeBudgetTask::class.java) {
                    this.sources.from(sources)
                },
                tasks.register("verifyNoUiConcat", VerifyNoUiConcatTask::class.java) {
                    this.sources.from(sources)
                }
            )
            if (layout.projectDirectory.file("src/main/res/values/strings.xml").asFile.isFile) {
                guards.add(tasks.register("verifyStringParity", VerifyStringParityTask::class.java) {
                    this.resDir.set(layout.projectDirectory.dir("src/main/res"))
                })
            }
            if (layout.projectDirectory.file("src/main/assets/prompts/manifest.json").asFile.isFile) {
                guards.add(tasks.register("verifyPromptCatalog", VerifyPromptCatalogTask::class.java) {
                    this.manifest.set(layout.projectDirectory.file("src/main/assets/prompts/manifest.json"))
                    this.promptDir.set(layout.projectDirectory.dir("src/main/assets/prompts"))
                })
            }
            tasks.register("verifyAll") {
                group = "verification"
                description = "Runs every mechanical architecture guard for this module."
                dependsOn(guards)
            }
            tasks.named("check") { dependsOn(guards) }
        }
    }
}

internal object JavaSourceScanner {
    /** Removes comments while keeping string/char literals intact. */
    fun stripComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var state = 0 // 0 code, 1 line comment, 2 block comment, 3 string, 4 char
        while (i < source.length) {
            val c = source[i]
            val next = if (i + 1 < source.length) source[i + 1] else '\u0000'
            when (state) {
                0 -> when {
                    c == '/' && next == '/' -> { state = 1; i += 2 }
                    c == '/' && next == '*' -> { state = 2; i += 2 }
                    c == '"' -> { state = 3; out.append(c); i++ }
                    c == '\'' -> { state = 4; out.append(c); i++ }
                    else -> { out.append(c); i++ }
                }
                1 -> if (c == '\n') { state = 0; out.append(c); i++ } else i++
                2 -> if (c == '*' && next == '/') { state = 0; i += 2 } else i++
                3 -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < source.length) { out.append(source[i + 1]); i += 2 }
                    else { if (c == '"') state = 0; i++ }
                }
                else -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < source.length) { out.append(source[i + 1]); i += 2 }
                    else { if (c == '\'') state = 0; i++ }
                }
            }
        }
        return out.toString()
    }

    /** All string literal bodies (without the quotes) found in comment-free code. */
    fun literals(code: String): List<String> {
        val result = ArrayList<String>()
        var i = 0
        while (i < code.length) {
            if (code[i] == '"') {
                val sb = StringBuilder()
                i++
                while (i < code.length && code[i] != '"') {
                    if (code[i] == '\\' && i + 1 < code.length) {
                        val e = code[i + 1]
                        if (e == 'u' && i + 5 < code.length) {
                            val hex = code.substring(i + 2, i + 6)
                            hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                            i += 6
                            continue
                        }
                        when (e) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            else -> sb.append(e)
                        }
                        i += 2
                    } else {
                        sb.append(code[i])
                        i++
                    }
                }
                result.add(sb.toString())
                i++
            } else {
                i++
            }
        }
        return result
    }

    fun decodeEscapes(value: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            if (value[i] == '\\' && i + 5 < value.length && value[i + 1] == 'u') {
                val hex = value.substring(i + 2, i + 6)
                val code = hex.toIntOrNull(16)
                if (code != null) { sb.append(code.toChar()); i += 6; continue }
            }
            sb.append(value[i])
            i++
        }
        return sb.toString()
    }

    fun looksLikeCjk(value: String): Boolean {
        for (ch in value) {
            val block = Character.UnicodeBlock.of(ch)
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
                block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
            ) {
                return true
            }
        }
        return false
    }

    fun javaFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "java" }.toList()

    fun relative(projectDir: File, file: File): String =
        file.absolutePath.removePrefix(projectDir.absolutePath + File.separator)
}

abstract class VerifyNoCjkLiteralsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val projectDir = project.projectDir
        val violations = ArrayList<String>()
        for (root in sources.files) {
            if (!root.exists()) continue
            for (file in JavaSourceScanner.javaFiles(root)) {
                val code = JavaSourceScanner.stripComments(file.readText())
                for (literal in JavaSourceScanner.literals(code)) {
                    if (JavaSourceScanner.looksLikeCjk(literal)) {
                        violations.add("${JavaSourceScanner.relative(projectDir, file)}: \"${literal.take(40)}\"")
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "CJK literals are forbidden in modules (move text to R.string):\n" +
                    violations.joinToString("\n")
            )
        }
    }
}

abstract class VerifyStringParityTask : DefaultTask() {
    @get:org.gradle.api.tasks.InputDirectory
    @get:org.gradle.api.tasks.Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resDir: org.gradle.api.file.DirectoryProperty

    @TaskAction
    fun verify() {
        val root = resDir.orNull?.asFile ?: return
        val base = stringKeys(File(root, "values/strings.xml"))
            ?: throw GradleException("missing values/strings.xml")
        verifyContent(File(root, "values/strings.xml"))
        for (locale in listOf("values-zh", "values-ru")) {
            val file = File(root, "$locale/strings.xml")
            if (!file.isFile) continue
            val keys = stringKeys(file)
                ?: throw GradleException("cannot read ${file.relativeTo(root)}")
            val missing = base - keys
            val extra = keys - base
            if (missing.isNotEmpty() || extra.isNotEmpty()) {
                throw GradleException(
                    "String key parity failed for ${file.relativeTo(root)}: " +
                        "missing=$missing extra=$extra"
                )
            }
            verifyContent(file)
        }
    }

    private fun verifyContent(file: File) {
        val matcher = Pattern.compile(
            "<string\\s+name=\"([^\"]+)\"[^>]*>(.*?)</string>", Pattern.DOTALL
        ).matcher(file.readText())
        while (matcher.find()) {
            val key = matcher.group(1)
            val value = matcher.group(2)
            if (value.contains("{{")) {
                throw GradleException("prompt placeholder in ${file.name}: $key")
            }
            if (value.length > 300 && !LONG_WHITELIST.contains(key)) {
                throw GradleException("string too long (${value.length}) in ${file.name}: $key")
            }
        }
    }

    private fun stringKeys(file: File): Set<String>? {
        if (!file.isFile) return null
        return Pattern.compile("<string\\s+name=\"([^\"]+)\"")
            .matcher(file.readText())
            .let { matcher ->
                val keys = LinkedHashSet<String>()
                while (matcher.find()) keys.add(matcher.group(1))
                keys
            }
    }

    companion object {
        private val LONG_WHITELIST = setOf(
            "user_agreement_text",
            "screen_phone_control_disclaimer_text",
            "screen_output_preview_markdown",
            "agent_fallback_role_explore_remote",
            "agent_fallback_role_coding_remote",
            "agent_fallback_role_explore_local",
            "agent_fallback_role_coding_local"
        )
    }
}

abstract class VerifyPromptCatalogTask : DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    @get:org.gradle.api.tasks.Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifest: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.InputDirectory
    @get:org.gradle.api.tasks.Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val promptDir: org.gradle.api.file.DirectoryProperty

    @TaskAction
    fun verify() {
        val manifestFile = manifest.orNull?.asFile ?: return
        if (!manifestFile.isFile) return
        val dir = promptDir.orNull?.asFile ?: return
        val text = manifestFile.readText()
        val entries = ArrayList<PromptEntry>()
        val entryMatcher = Pattern.compile("\\{[^{}]*\\}").matcher(text)
        while (entryMatcher.find()) {
            val entry = entryMatcher.group()
            val id = field(entry, "id") ?: continue
            val file = field(entry, "file")
                ?: throw GradleException("prompt entry without file: $entry")
            entries.add(PromptEntry(id, file, stringArray(entry, "variables")))
        }
        if (entries.isEmpty()) throw GradleException("prompt manifest declares no templates")
        if (entries.map { it.id }.distinct().size != entries.size) {
            throw GradleException("duplicate prompt ids")
        }
        val registry = registry(dir)
        val ids = entries.map { it.id }.toSet()
        if (ids != registry.keys) {
            throw GradleException("prompt manifest/registry mismatch: manifest-only=" +
                (ids - registry.keys) + " registry-only=" + (registry.keys - ids))
        }
        val missing = entries.filter { !File(dir, it.file).isFile }.map { it.file }
        if (missing.isNotEmpty()) throw GradleException("missing prompt assets: $missing")
        val declared = entries.map { File(it.file).name }.toSet()
        val orphans = dir.listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            ?.map { it.name }?.filterNot { declared.contains(it) } ?: emptyList()
        if (orphans.isNotEmpty()) throw GradleException("orphan prompt assets: $orphans")
        val digests = HashMap<String, String>()
        for (entry in entries) {
            val file = File(dir, entry.file)
            val content = file.readText()
            val expected = registry.getValue(entry.id)
            if (expected.first != entry.file) {
                throw GradleException("prompt file mismatch for ${entry.id}: " +
                    "manifest=${entry.file} registry=${expected.first}")
            }
            if (expected.second.toSet() != entry.variables.toSet()) {
                throw GradleException("prompt variable mismatch for ${entry.id}: " +
                    "manifest=${entry.variables} registry=${expected.second}")
            }
            val placeholders = PLACEHOLDER.findAll(content).map { it.groupValues[1] }.toSet()
            if (placeholders != entry.variables.toSet()) {
                throw GradleException("prompt placeholder mismatch for ${entry.id}: " +
                    "declared=${entry.variables} template=$placeholders")
            }
            val dummy = PLACEHOLDER.replace(content) { "x" }
            if (dummy.contains("{{")) {
                throw GradleException("unresolved placeholder after dummy render: ${entry.file}")
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(content.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val duplicate = digests.put(digest, entry.file)
            if (duplicate != null) {
                throw GradleException("duplicate prompt content: $duplicate and ${entry.file}")
            }
        }
    }

    private data class PromptEntry(val id: String, val file: String, val variables: List<String>)

    private fun field(entry: String, name: String): String? =
        Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(entry)?.groupValues?.get(1)

    private fun stringArray(entry: String, name: String): List<String> {
        val array = Regex("\"$name\"\\s*:\\s*\\[([^\\]]*)\\]").find(entry)
            ?: return emptyList()
        return Regex("\"([^\"]+)\"").findAll(array.groupValues[1])
            .map { it.groupValues[1] }.toList()
    }

    private fun registry(dir: File): Map<String, Pair<String, List<String>>> {
        val source = File(dir, "../../../main/java/cn/lineai/engine/prompt/PromptId.java")
            .canonicalFile
        if (!source.isFile) return emptyMap()
        val result = LinkedHashMap<String, Pair<String, List<String>>>()
        val matcher = Pattern.compile(
            "(?m)^\\s{4}[A-Z0-9_]+\\(\\s*\"([^\"]+)\",\\s*\"([^\"]+)\"([^)]*)\\)"
        ).matcher(source.readText())
        while (matcher.find()) {
            val variables = Regex("\"([^\"]+)\"").findAll(matcher.group(3))
                .map { it.groupValues[1] }.toList()
            result[matcher.group(1)] = matcher.group(2) to variables
        }
        return result
    }

    companion object {
        private val PLACEHOLDER = Regex("\\{\\{\\s*([A-Z0-9_]+)\\s*\\}\\}")
    }
}

abstract class VerifyNoRawThreadsTask : DefaultTask() {
    private val patterns = listOf(
        Regex("""new\s+Thread\s*\("""),
        Regex("""Executors\."""),
        Regex("""new\s+Handler\s*\("""),
        Regex("""Thread\.sleep\s*\("""),
        Regex("""CountDownLatch"""),
        Regex("""while\s*\(\s*true\s*\)"""),
        Regex("""notifyDataSetChanged\s*\(""")
    )

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val projectDir = project.projectDir
        val violations = ArrayList<String>()
        for (root in sources.files) {
            if (!root.exists()) continue
            for (file in JavaSourceScanner.javaFiles(root)) {
                val relative = JavaSourceScanner.relative(projectDir, file)
                // The nested Agent tool is synchronous by contract; this bridge waits on a
                // worker thread while the tool loop streams. It is the only sanctioned wait.
                if (project.path == ":app" &&
                    relative == "src/main/java/cn/lineai/app/NestedAgentRunner.java"
                ) {
                    continue
                }
                val code = JavaSourceScanner.stripComments(file.readText())
                for (pattern in patterns) {
                    if (pattern.containsMatchIn(code)) {
                        violations.add("$relative: ${pattern.pattern}")
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Raw threads / blocking waits / adapter resets are forbidden:\n" +
                    violations.joinToString("\n")
            )
        }
    }
}

abstract class VerifySizeBudgetTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val projectDir = project.projectDir
        val violations = ArrayList<String>()
        for (root in sources.files) {
            if (!root.exists()) continue
            for (file in JavaSourceScanner.javaFiles(root)) {
                val lines = file.readLines()
                val path = JavaSourceScanner.relative(projectDir, file)
                if (lines.size > 600) {
                    violations.add("$path: ${lines.size} lines (max 600)")
                }
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("public ") || trimmed.startsWith("private ") ||
                        trimmed.startsWith("protected ")
                    ) {
                        val open = trimmed.indexOf('(')
                        val close = trimmed.indexOf(')')
                        if (open > 0 && close > open && trimmed.endsWith("{")) {
                            val params = trimmed.substring(open + 1, close).trim()
                            if (params.isNotEmpty()) {
                                val arity = params.count { it == ',' } + 1
                                if (arity > 8) {
                                    violations.add("$path:${index + 1}: constructor/method arity $arity (max 8)")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Size budget violated:\n" + violations.joinToString("\n"))
        }
    }
}

abstract class VerifyNoUiConcatTask : DefaultTask() {
    private val patterns = listOf(
        Regex("""setText\s*\([^;]*\+"""),
        Regex("""getString\s*\([^)]*\)\s*\+""")
    )

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val projectDir = project.projectDir
        val violations = ArrayList<String>()
        for (root in sources.files) {
            if (!root.exists()) continue
            for (file in JavaSourceScanner.javaFiles(root)) {
                val code = JavaSourceScanner.stripComments(file.readText())
                for (pattern in patterns) {
                    if (pattern.containsMatchIn(code)) {
                        violations.add("${JavaSourceScanner.relative(projectDir, file)}: ${pattern.pattern}")
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("User-visible text must not be concatenated in UI code:\n" + violations.joinToString("\n"))
        }
    }
}
