package io.github.prismwork.asbestos

import org.gradle.api.Plugin
import org.gradle.api.Project

class AsbestosGradlePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create(
            EXTENSION_NAME,
            AsbestosGradleExtension::class.java
        )
    }
}