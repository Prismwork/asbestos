/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Chocohead
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.prismwork.asbestos.utils.mappings

/**
 * From [here](https://github.com/Chocohead/Fabric-Loom/blob/jekan't/src/main/java/net/fabricmc/loom/providers/mappings/IMappingAcceptor.java) in Chocoloom.
 */
interface IMappingAcceptor {
    fun acceptClass(srcName: String?, dstName: String?)

    fun acceptMethod(
        srcClsName: String?,
        srcName: String?,
        srcDesc: String?,
        dstClsName: String?,
        dstName: String?,
        dstDesc: String?
    )

    fun acceptMethodArg(
        srcClsName: String?,
        srcMethodName: String?,
        srcMethodDesc: String?,
        lvIndex: Int,
        dstArgName: String?
    )

    fun acceptField(
        srcClsName: String?,
        srcName: String?,
        srcDesc: String?,
        dstClsName: String?,
        dstName: String?,
        dstDesc: String?
    )

    fun acceptClassComment(className: String?, comment: String?)

    fun acceptMethodComment(className: String?, methodName: String?, desc: String?, comment: String?)

    fun acceptMethodArgComment(className: String?, methodName: String?, desc: String?, lvIndex: Int, comment: String?)

    fun acceptFieldComment(className: String?, fieldName: String?, desc: String?, comment: String?)
}