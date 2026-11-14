import groovy.json.JsonOutput
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class WriteCllBuildIdentity : DefaultTask() {
    @get:Input abstract val fields: MapProperty<String, String>
    @get:OutputDirectory abstract val destination: DirectoryProperty
    @TaskAction fun writeIdentity() {
        val file = destination.get().file("META-INF/cll/build-identity.properties").asFile
        file.parentFile.mkdirs()
        file.writeText(fields.get().toSortedMap().entries.joinToString("") { it.key + "=" + it.value + "\n" }, Charsets.UTF_8)
    }
}
val commit = rootProject.extra["cllSourceCommit"].toString()
val sourceState = rootProject.extra["cllSourceState"].toString()
val sourceDigest = rootProject.extra["cllSourceDigest"].toString()
val resolver = rootProject.extra["cllResolverRevision"].toString()
val module = project.name
val repository = rootProject.name
extensions.getByName("android").withGroovyBuilder {
    "buildFeatures" { setProperty("buildConfig", true) }
    val types = getProperty("buildTypes") as NamedDomainObjectContainer<Any>
    types.configureEach {
        val type = this
        type.withGroovyBuilder {
            val variant = getProperty("name").toString()
            val id = repository + ":" + module + ":" + variant + ":" + commit + ":" + sourceState + ":" + sourceDigest + ":" + resolver
            "buildConfigField"("String", "SOURCE_COMMIT", JsonOutput.toJson(commit))
            "buildConfigField"("String", "SOURCE_STATE", JsonOutput.toJson(sourceState))
            "buildConfigField"("boolean", "SOURCE_DIRTY", (sourceState == "dirty").toString())
            "buildConfigField"("String", "SOURCE_WORKTREE", JsonOutput.toJson(sourceDigest))
            "buildConfigField"("String", "RESOLVER_REVISION", JsonOutput.toJson(resolver))
            "buildConfigField"("String", "BUILD_ID", JsonOutput.toJson(id))
            val output = layout.buildDirectory.dir("generated/cllIdentity/" + variant)
            val task = tasks.register("write" + variant.replaceFirstChar { it.uppercase() } + "CllIdentity", WriteCllBuildIdentity::class.java) {
                fields.set(mapOf("schema" to "1", "buildId" to id, "sourceCommit" to commit,
                    "sourceState" to sourceState, "sourceDigest" to sourceDigest, "module" to module,
                    "variant" to variant, "resolverRevision" to resolver))
                destination.set(output)
            }
            // File, not Provider: compatible with AGP 8 and AGP 9 SourceSet admission.
            // The explicit preBuild edge carries the producer dependency.
            extensions.getByName("android").withGroovyBuilder {
                "sourceSets" { variant { "resources" { "srcDir"(output.get().asFile) } } }
            }
            tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(task) }
        }
    }
}
