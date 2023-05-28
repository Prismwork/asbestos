package io.github.prismwork.asbestos.utils

import com.google.common.base.Stopwatch
import com.google.common.collect.Iterables
import io.github.prismwork.asbestos.ORIGINAL_INTERMEIDARY_NAMESPACE
import io.github.prismwork.asbestos.asbestos
import io.github.prismwork.asbestos.loom
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace
import net.fabricmc.loom.util.FileSystemUtil
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.adapter.MappingNsCompleter
import net.fabricmc.mappingio.adapter.MappingNsRenamer
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.Tiny2Writer
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*
import java.util.regex.Pattern

class MappingsBuilder(private val project: Project) {
    private var baseMappingsDep: Any? = null
    private var sourceIntermediaryDep: Any? = null
    private var targetIntermediaryDep: Any? = null

    fun base(dep: Any): MappingsBuilder {
        baseMappingsDep = dep
        return this
    }

    fun sourceIntermediary(dep: Any): MappingsBuilder {
        sourceIntermediaryDep = dep
        return this
    }

    fun targetIntermediary(dep: Any): MappingsBuilder {
        targetIntermediaryDep = dep
        return this
    }

    fun build(): Dependency {
        val loom = project.loom

        // If the base mappings are null, return an empty set of mappings
        if (baseMappingsDep == null) return loom.layered {}

        val stopwatch = Stopwatch.createStarted()
        project.logger.lifecycle("[Asbestos] Building mappings...")

        val targetMappings = MemoryMappingTree()

        // original intermediary -> named
        val baseMappings = getMappings(
            Iterables.getOnlyElement(project.configurations.detachedConfiguration(
                baseMappingsDep?.let { project.dependencies.module(it) }).resolve()
            )
        )
        baseMappings.accept(MappingSourceNsSwitch(targetMappings, MappingsNamespace.INTERMEDIARY.toString()))

        // official -> original intermediary, if source intermediary mappings are presented
        sourceIntermediaryDep?.let { source ->
            project.logger.lifecycle("[Asbestos] Source intermediary found")
            val sourceIntermediary = getMappings(
                Iterables.getOnlyElement(
                    project.configurations.detachedConfiguration(
                        project.dependencies.module(source)
                    ).resolve()
                )
            )

            // attach official to output
            /*
            val nsSwitch = MappingSourceNsSwitch(outputMappings, MappingsNamespace.OFFICIAL.toString())
            val nsCompleter = MappingNsCompleter(
                nsSwitch,
                mapOf(Pair(MappingsNamespace.INTERMEDIARY.toString(), MappingsNamespace.OFFICIAL.toString())),
                true
            )
            */
            sourceIntermediary.accept(MappingSourceNsSwitch(targetMappings, MappingsNamespace.INTERMEDIARY.toString()))

            // official -> intermediary (target), if possible
            targetIntermediaryDep?.let { target ->
                project.logger.lifecycle("[Asbestos] Retargeting mappings...")

                val targetIntermediary: MemoryMappingTree = getMappings(
                    Iterables.getOnlyElement(
                        project.configurations.detachedConfiguration(
                            project.dependencies.module(target)
                        ).resolve()
                    )
                )

                // rename the old intermediary to original_intermediary & attach to output
                val nsSwitchComplete = MappingSourceNsSwitch(
                    targetMappings,
                    MappingsNamespace.INTERMEDIARY.toString()
                )
                val nsRenamer2 = MappingNsRenamer(
                    nsSwitchComplete,
                    mapOf(
                        Pair(MappingsNamespace.INTERMEDIARY.toString(), ORIGINAL_INTERMEIDARY_NAMESPACE),
                        Pair(ORIGINAL_INTERMEIDARY_NAMESPACE, MappingsNamespace.INTERMEDIARY.toString())
                    )
                )
                val nsCompleterTarget = MappingNsCompleter(
                    nsRenamer2,
                    mapOf(Pair(MappingsNamespace.OFFICIAL.toString(), MappingsNamespace.INTERMEDIARY.toString())),
                    true
                )
                val nsRenamer1 = MappingNsRenamer(
                    nsCompleterTarget,
                    mapOf(Pair(MappingsNamespace.INTERMEDIARY.toString(), ORIGINAL_INTERMEIDARY_NAMESPACE))
                )
                targetIntermediary.accept(nsRenamer1)

                project.logger.lifecycle("[Asbestos] Mappings retargeted")
            }

            project.logger.lifecycle("[Asbestos] Merged mappings")
        }

        val outputMappings = MemoryMappingTree()
        targetMappings.accept(MappingSourceNsSwitch(outputMappings, MappingsNamespace.OFFICIAL.toString()))

        inheritMappedNamesOfEnclosingClasses(outputMappings)

        project.logger.lifecycle("[Asbestos] Built mappings in ${stopwatch.stop()}")

        if (!project.asbestos.getCacheDir().toFile().exists())
            project.asbestos.getCacheDir().toFile().mkdirs()

        val out = project.asbestos.getCacheDir().resolve("asbestos-mappings.tiny")

        Tiny2Writer(Files.newBufferedWriter(out, StandardCharsets.UTF_8), false)
            .use(outputMappings::accept)

        return loom.layered {
            it.mappings(out)
        }
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
            val intermediaryIdx = tree.getNamespaceId("intermediary")
            val namedIdx = tree.getNamespaceId("named")

            tree.setIndexByDstNames(true)
            for (classEntry in (tree as MappingTree).classes) {
                val intermediaryName = classEntry.getDstName(intermediaryIdx)
                val namedName = classEntry.getDstName(namedIdx)
                if (intermediaryName == namedName && intermediaryName.contains("$")) {
                    val path = intermediaryName
                        .split(Pattern.quote("$").toRegex())
                        .dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    val parts = path.size
                    for (i in parts - 2 downTo 0) {
                        val currentPath = path.copyOfRange(0, i + 1).joinToString(separator = "$")
                        val namedParentClass = tree.mapClassName(currentPath, intermediaryIdx, namedIdx)
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
    }
}