/*
 * Copyright (c) 2021 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.prismwork.asbestos.utils.mappingio

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

/**
 * From [this closed PR](https://github.com/FabricMC/mapping-io/pull/28) of mapping-io.
 */
class MappingDstNsRemover(next: MappingVisitor, namespacesToRemove: List<String>) :
    ForwardingMappingVisitor(next) {

    private val namespaceNamesToRemove: List<String>
    private var filteredNamespaceIndices: MutableList<Int> = ArrayList()
    private var filteredNamespaceNames: MutableList<String> = ArrayList()

    init {
        Objects.requireNonNull(namespacesToRemove, "null namespacesToRemove list")
        namespaceNamesToRemove = namespacesToRemove
    }

    @Throws(IOException::class)
    override fun visitNamespaces(srcNamespace: String, dstNamespaces: List<String>) {
        val listSize = dstNamespaces.size - namespaceNamesToRemove.size
        filteredNamespaceIndices = ArrayList(listSize)
        filteredNamespaceNames = ArrayList(listSize)
        for (i in dstNamespaces.indices) {
            val dstName = dstNamespaces[i]
            if (namespaceNamesToRemove.contains(dstName)) {
                continue
            }
            filteredNamespaceIndices.add(i)
            filteredNamespaceNames.add(dstName)
        }
        super.visitNamespaces(srcNamespace, filteredNamespaceNames)
    }

    @Throws(IOException::class)
    override fun visitDstName(targetKind: MappedElementKind, namespace: Int, name: String?) {
        if (!filteredNamespaceIndices.contains(namespace)) {
            return
        }
        super.visitDstName(targetKind, filteredNamespaceIndices.indexOf(namespace), name)
    }

    @Throws(IOException::class)
    override fun visitDstDesc(targetKind: MappedElementKind, namespace: Int, desc: String?) {
        if (!filteredNamespaceIndices.contains(namespace)) {
            return
        }
        super.visitDstDesc(targetKind, filteredNamespaceIndices.indexOf(namespace), desc)
    }
}