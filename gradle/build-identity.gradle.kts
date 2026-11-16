import groovy.json.JsonOutput
import java.security.MessageDigest

// Build-time only. No Git process, source path or APK hashing is added to Android hot paths.
fun cllGit(vararg arguments: String): Pair<Boolean, String> = try {
    val output = providers.exec {
        commandLine(listOf("git", "-C", rootProject.projectDir.absolutePath) + arguments)
        isIgnoreExitValue = true
    }
    (output.result.get().exitValue == 0) to output.standardOutput.asText.get().trim()
} catch (_: Exception) {
    false to ""
}
fun cllSha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

val revision = cllGit("rev-parse", "HEAD")
val status = cllGit("status", "--porcelain=v1", "--untracked-files=all")
val repositoryBound = rootProject.file(".git").exists() // Archives must not inherit an unrelated parent repository.
val commit = revision.second.takeIf { repositoryBound && revision.first && it.matches(Regex("[0-9a-f]{40}|[0-9a-f]{64}")) } ?: "unknown"
val state = if (commit == "unknown" || !status.first) "unknown" else if (status.second.isEmpty()) "clean" else "dirty"
// Hash actual production/build INPUT bytes, not a diff or a list of untracked names.
// Test fixtures, personal artifacts, credentials, caches and generated output are not inputs.
val sourceFiles = rootProject.fileTree(rootProject.projectDir) {
    include("**/src/main/**", "**/src/debug/**", "**/src/release/**", "**/*.gradle.kts",
        "**/*.gradle", "gradle/**", "gradle.properties", "release/*.json", "scripts/*.ps1", "scripts/*.cmd")
    exclude("**/build/**", "**/.gradle/**", "**/.gradle-user-home/**", "**/.git/**", "**/node_modules/**",
        "**/artifacts/**", "**/device-testing/**", "**/provider-scope-probe/**", "**/src/test/**",
        "**/*.jks", "**/*.keystore", "**/*.apk", "**/*.apks", "**/local.properties")
}.files.filter { it.isFile }.associateBy {
    it.relativeTo(rootProject.projectDir).invariantSeparatorsPath
}.toSortedMap()
val inputs = sourceFiles.mapValues { (path, _) ->
    cllSha(providers.fileContents(rootProject.layout.projectDirectory.file(path)).asBytes.get())
}
val parameters = sortedMapOf("releaseTag" to providers.gradleProperty("releaseTag")
    .orElse(providers.environmentVariable("RELEASE_TAG")).orElse("").get())
val canonical = "cll-source-v1\n" + inputs.entries.joinToString("") { it.key + "\u0000" + it.value + "\n" } +
    "parameters\u0000" + JsonOutput.toJson(parameters) + "\n"
val digest = cllSha(canonical.toByteArray(Charsets.UTF_8))
val resolver = providers.fileContents(rootProject.layout.projectDirectory.file("gradle/resolver-revision.txt"))
    .asText.get().trim()
check(resolver.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "Invalid resolver revision" }
rootProject.extra["cllSourceCommit"] = commit
rootProject.extra["cllSourceState"] = state
rootProject.extra["cllSourceDigest"] = digest
rootProject.extra["cllResolverRevision"] = resolver

val report = JsonOutput.prettyPrint(JsonOutput.toJson(linkedMapOf(
    "schema" to 1, "algorithm" to "cll-source-v1", "commit" to commit, "state" to state,
    "sourceDigest" to digest, "resolverRevision" to resolver, "parameters" to parameters, "inputs" to inputs
))) + "\n"
tasks.register("writeSourceIdentityReport") {
    val destination = rootProject.layout.buildDirectory.file("reports/cll-source-identity.json")
    val contents = report
    inputs.property("identity", contents)
    outputs.file(destination)
    doLast {
        destination.get().asFile.apply { parentFile.mkdirs(); writeText(contents, Charsets.UTF_8) }
    }
}
subprojects {
    plugins.withId("com.android.application") {
        apply(from = rootProject.file("gradle/build-identity-app.gradle.kts"))
    }
}
