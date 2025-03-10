/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang.classfile.attribute;

import java.lang.classfile.Attribute;
import java.lang.classfile.AttributeMapper;
import java.lang.classfile.AttributeMapper.AttributeStability;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.List;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models the {@link Attributes#moduleHashes() ModuleHashes} attribute, which
 * appears on classes that {@linkplain ClassModel#isModuleInfo() represent}
 * module descriptors to capture the hashes of a set of co-delivered modules.
 * <p>
 * The specification of the {@code ModuleHashes} attribute is:
 * <pre> {@code
 *
 * ModuleHashes_attribute {
 *   // index to CONSTANT_utf8_info structure in constant pool representing
 *   // the string "ModuleHashes"
 *   u2 attribute_name_index;
 *   u4 attribute_length;
 *
 *   // index to CONSTANT_utf8_info structure with algorithm name
 *   u2 algorithm_index;
 *
 *   // the number of entries in the hashes table
 *   u2 hashes_count;
 *   {   u2 module_name_index (index to CONSTANT_Module_info structure)
 *       u2 hash_length;
 *       u1 hash[hash_length];
 *   } hashes[hashes_count];
 *
 * }
 * } </pre>
 * <p>
 * This attribute only appears on classes, and does not permit {@linkplain
 * AttributeMapper#allowMultiple multiple instances} in a class.  It has a
 * data dependency on the {@linkplain AttributeStability#CP_REFS constant pool}.
 * <p>
 * This attribute is not predefined in the Java SE Platform.  This is a
 * JDK-specific nonstandard attribute produced by the {@code jdk.jlink} module,
 * which defines the {@code jlink} and {@code jmod} tools.
 *
 * @see Attributes#moduleHashes()
 * @see ModuleResolutionAttribute
 * @see ModuleTargetAttribute
 * @since 24
 */
public sealed interface ModuleHashesAttribute
        extends Attribute<ModuleHashesAttribute>, ClassElement
        permits BoundAttribute.BoundModuleHashesAttribute, UnboundAttribute.UnboundModuleHashesAttribute {

    /**
     * {@return the algorithm name used to compute the hash}
     */
    Utf8Entry algorithm();

    /**
     * {@return the hash information about related modules}
     */
    List<ModuleHashInfo> hashes();

    /**
     * {@return a {@code ModuleHashes} attribute}
     * @param algorithm the hashing algorithm
     * @param hashes the hash descriptions
     */
    static ModuleHashesAttribute of(String algorithm,
                                    List<ModuleHashInfo> hashes) {
        return of(TemporaryConstantPool.INSTANCE.utf8Entry(algorithm), hashes);
    }

    /**
     * {@return a {@code ModuleHashes} attribute}
     * @param algorithm the hashing algorithm
     * @param hashes the hash descriptions
     */
    static ModuleHashesAttribute of(String algorithm,
                                    ModuleHashInfo... hashes) {
        return of(algorithm, List.of(hashes));
    }

    /**
     * {@return a {@code ModuleHashes} attribute}
     * @param algorithm the hashing algorithm
     * @param hashes the hash descriptions
     */
    static ModuleHashesAttribute of(Utf8Entry algorithm,
                                    List<ModuleHashInfo> hashes) {
        return new UnboundAttribute.UnboundModuleHashesAttribute(algorithm, hashes);
    }

    /**
     * {@return a {@code ModuleHashes} attribute}
     * @param algorithm the hashing algorithm
     * @param hashes the hash descriptions
     */
    static ModuleHashesAttribute of(Utf8Entry algorithm,
                                    ModuleHashInfo... hashes) {
        return of(algorithm, List.of(hashes));
    }
}
