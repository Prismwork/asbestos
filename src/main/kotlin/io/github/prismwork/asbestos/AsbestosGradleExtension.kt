package io.github.prismwork.asbestos

import io.github.prismwork.asbestos.utils.MappingsBuilder
import net.fabricmc.loom.LoomGradleExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Property
import java.nio.file.Path

@Suppress("unused")
val Project.asbestos: AsbestosGradleExtension
    get() = extensions.getByType(AsbestosGradleExtension::class.java)

@Suppress("unused")
val Project.loom: LoomGradleExtension
    get() = LoomGradleExtension.get(project)

class AsbestosGradleExtension(private val project: Project) {
    fun newMappingsBuilder(): MappingsBuilder {
        return MappingsBuilder(project)
    }

    fun newMappingsBuilder(action: Action<MappingsBuilder>):
            MappingsBuilder {
        val ret = MappingsBuilder(project)
        action.execute(ret)
        return ret
    }

    fun getCacheDir(): Path {
        return project.loom.files.projectBuildCache.toPath().resolve("asbestos-cache")
    }
}