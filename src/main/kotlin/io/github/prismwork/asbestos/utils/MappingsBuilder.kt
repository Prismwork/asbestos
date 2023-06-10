package io.github.prismwork.asbestos.utils

import com.google.common.base.Stopwatch
import com.google.common.collect.Iterables
import groovy.lang.Closure
import groovy.lang.ExpandoMetaClass
import io.github.prismwork.asbestos.asbestos
import io.github.prismwork.asbestos.loom
import io.github.prismwork.asbestos.utils.mappingio.MappingDstNsRemover
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace
import net.fabricmc.loom.util.FileSystemUtil
import net.fabricmc.mapping.util.EntryTriple
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.Tiny2Writer
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*
import java.util.regex.Pattern


class MappingsBuilder(private val project: Project) {
    private var baseMappingsDependency: Any? = null
    private var sourceIntermediaryDependency: Any? = null
    private var targetIntermediaryDependency: Any? = null
    private var additionalMappings: MemoryMappingTree = MemoryMappingTree()

    init {
        additionalMappings.srcNamespace = MappingsNamespace.INTERMEDIARY.toString()
        additionalMappings.dstNamespaces.add(MappingsNamespace.NAMED.toString())
    }

    fun base(dep: Any): MappingsBuilder {
        baseMappingsDependency = dep
        return this
    }

    fun sourceIntermediary(dep: Any): MappingsBuilder {
        sourceIntermediaryDependency = dep
        return this
    }

    fun targetIntermediary(dep: Any): MappingsBuilder {
        targetIntermediaryDependency = dep
        return this
    }

    fun extraMappings(configure: Action<MappingContainer>): MappingsBuilder {
        val mappings = MappingContainer()
        configure.execute(mappings)
        applyExtraMappings(mappings, additionalMappings)
        return this
    }

    fun build(): Dependency {
        val loom = project.loom

        // If the base mappings are null, return an empty set of mappings
        if (baseMappingsDependency == null) return loom.layered {}

        val stopwatch = Stopwatch.createStarted()
        project.logger.lifecycle("[Asbestos] Building mappings...")

        val targetMappings = MemoryMappingTree()

        // original intermediary -> named
        val baseMappingsDep = baseMappingsDependency?.let { project.dependencies.module(it) }
        val baseMappings = getMappings(
            Iterables.getOnlyElement(
                project.configurations.detachedConfiguration(baseMappingsDep).resolve()
            )
        )
        baseMappings.accept(MappingSourceNsSwitch(targetMappings, MappingsNamespace.INTERMEDIARY.toString()))

        val sourceIntermediary = if (sourceIntermediaryDependency != null) getMappings(
            Iterables.getOnlyElement(
                project.configurations.detachedConfiguration(
                    sourceIntermediaryDependency?.let { project.dependencies.module(it) }
                ).resolve()
            )
        ) else {
            val defaultIntermediary = loom.intermediateMappingsProvider
            val intermediaryPath = project.asbestos.getCacheDir()
                .resolve("${defaultIntermediary.name}-default-source.tiny")
            defaultIntermediary.provide(intermediaryPath)
            getMappings(intermediaryPath.toFile())
        }

        // attach official to output
        sourceIntermediary.accept(MappingSourceNsSwitch(targetMappings, MappingsNamespace.INTERMEDIARY.toString()))

        val resultMappings: MemoryMappingTree = if (targetIntermediaryDependency != null) getMappings(
            Iterables.getOnlyElement(
                project.configurations.detachedConfiguration(
                    targetIntermediaryDependency?.let { project.dependencies.module(it) }
                ).resolve()
            )
        ) else {
            val defaultIntermediary = loom.intermediateMappingsProvider
            val intermediaryPath = project.asbestos.getCacheDir()
                .resolve("${defaultIntermediary.name}-default-target.tiny")
            defaultIntermediary.provide(intermediaryPath)
            getMappings(intermediaryPath.toFile())
        }
        val nsRemover = MappingDstNsRemover(resultMappings, listOf(MappingsNamespace.INTERMEDIARY.toString()))
        targetMappings.accept(MappingSourceNsSwitch(nsRemover, MappingsNamespace.OFFICIAL.toString()))

        project.logger.lifecycle("[Asbestos] Merged mappings")

        val out = project.asbestos.getCacheDir()
            .resolve("asbestos-mappings+${baseMappingsDep?.name}-${baseMappingsDep?.version}.tiny")
        Files.deleteIfExists(out)

        val outputMappings = MemoryMappingTree()
        val nsMojRemover = MappingDstNsRemover(outputMappings, listOf(MappingsNamespace.OFFICIAL.toString()))
        resultMappings.accept(MappingSourceNsSwitch(nsMojRemover, MappingsNamespace.INTERMEDIARY.toString()))

        inheritMappedNamesOfEnclosingClasses(outputMappings)

        project.logger.lifecycle("[Asbestos] Built mappings in ${stopwatch.stop()}")

        if (!project.asbestos.getCacheDir().toFile().exists())
            project.asbestos.getCacheDir().toFile().mkdirs()

        Tiny2Writer(Files.newBufferedWriter(out, StandardCharsets.UTF_8), false)
            .use(outputMappings::accept)

        return loom.layered { it.mappings(out) }
    }

    /**
     * From Chocoloom
     */
    class MappingContainer {
        var from = "intermediary"
        var to = "named"
        private val classes: MutableMap<String, String> = HashMap()
        private val methods: MutableMap<EntryTriple, String> = HashMap()
        private val fields: MutableMap<EntryTriple, String> = HashMap()

        init {
            val meta = ExpandoMetaClass(MappingContainer::class.java, true, false)
            meta.registerInstanceMethod("class", object: Closure<MappingContainer>(this) {
                override fun call(vararg args: Any?): MappingContainer? {
                    classes[args[0] as String] = args[1] as String
                    return null
                }

                override fun getParameterTypes(): Array<Class<*>> {
                    return arrayOf(String::class.java, String::class.java)
                }
            })
            meta.initialize()
        }

        fun getClasses(): Map<String, String> {
            return classes
        }

        fun setClasses(mappings: Map<String, String>) {
            classes.clear()
            classes.putAll(mappings)
        }

        fun method(owner: String, desc: String, from: String, to: String) {
            method(EntryTriple(owner, from, desc), to)
        }

        fun method(from: EntryTriple, to: String) {
            methods[from] = to
        }

        fun getMethods(): Map<EntryTriple, String> {
            return methods
        }

        fun setMethods(mappings: Map<EntryTriple, String>) {
            methods.clear()
            methods.putAll(mappings)
        }

        fun field(owner: String, desc: String, from: String, to: String) {
            field(EntryTriple(owner, from, desc), to)
        }

        fun field(from: EntryTriple, to: String) {
            fields[from] = to
        }

        fun getFields(): Map<EntryTriple, String> {
            return fields
        }

        fun setFields(mappings: Map<EntryTriple, String>) {
            fields.clear()
            fields.putAll(mappings)
        }

        val isEmpty: Boolean
            get() = classes.isEmpty() && methods.isEmpty() && fields.isEmpty()
    }

    private companion object {
        @Throws(IOException::class)
        private fun getMappings(mappings: File): MemoryMappingTree {
            val mappingTree = MemoryMappingTree()
            FileSystemUtil.getJarFileSystem(mappings.toPath()).use { delegate ->
                MappingReader.read(
                    delegate.fs().getPath("mappings/mappings.tiny"),
                    mappingTree
                )
            }
            return mappingTree
        }

        private fun inheritMappedNamesOfEnclosingClasses(tree: MemoryMappingTree) {
            val namedIdx = tree.getNamespaceId("named")

            tree.setIndexByDstNames(true)
            for (classEntry in (tree as MappingTree).classes) {
                val intermediaryName = classEntry.srcName
                val namedName = classEntry.getDstName(namedIdx)
                if (intermediaryName == namedName && intermediaryName.contains("$")) {
                    val path = intermediaryName
                        .split(Pattern.quote("$").toRegex())
                        .dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    val parts = path.size
                    for (i in parts - 2 downTo 0) {
                        val currentPath = path.copyOfRange(0, i + 1).joinToString(separator = "$")
                        val namedParentClass = tree.mapClassName(currentPath, namedIdx)
                        if (namedParentClass != currentPath) {
                            classEntry.setDstName(
                                "$namedParentClass$" +
                                        path.copyOfRange(i + 1, path.size)
                                            .joinToString(separator = "$"),
                                namedIdx
                            )
                            break
                        }
                    }
                }
            }
        }

        private fun applyExtraMappings(mappings: MappingContainer, target: MemoryMappingTree) {
            TODO("ill do this later")
        }
    }
}