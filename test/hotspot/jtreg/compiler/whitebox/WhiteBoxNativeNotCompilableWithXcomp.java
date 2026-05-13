/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * Minimal reproducer: under {@code -Xcomp}, the VM may enqueue compilation of
 * {@code jdk.test.whitebox.WhiteBox} JNI natives before {@link jdk.test.whitebox.WhiteBox}'s
 * static initializer has finished {@code registerNatives()}, so {@code NativeLookup::lookup}
 * fails and the native {@code Method} is marked not compilable
 * (see {@code CompileBroker::compile_method}).
 * <p>
 * {@code CompileBroker::compile_method} now skips {@code set_not_compilable} when the
 * declaring class is {@code InstanceKlass::is_being_initialized()}.
 * <p>
 * Without that guard, {@code WhiteBox.isMethodCompilable(...)} could become false and break
 * IR-style tests that assume ordinary C2 eligibility after warmup.
 * <p>
 * <b>Possible upstream fix points (HotSpot)</b>
 * <ol>
 *   <li><b>compileBroker.cpp</b> ({@code compile_method}): For {@code method->is_native()},
 *       if {@code NativeLookup::lookup} fails with a pending exception, avoid calling
 *       {@code set_not_compilable("NativeLookup::lookup failed")} when the failure is
 *       classified as "transient" (e.g. class still initializing). Instead clear the
 *       exception and return null without poisoning the {@code Method} for all tiers.
 *       Alternatively, defer native wrapper compilation until the holder class is fully
 *       initialized (after {@code jdk.test.whitebox.WhiteBox} has run its static initializer).
 *   </li>
 *   <li><b>compilationPolicy / compile queue</b>: Do not schedule C1/C2 compilation of
 *       {@code jdk.test.whitebox.WhiteBox} natives until {@code InstanceKlass::is_initialized()}
 *       is true for {@code jdk.test.whitebox.WhiteBox} (or until {@code registerNatives} has run).
 *   </li>
 *   <li><b>NativeLookup</b>: If lookup is attempted from a compiler thread while the defining
 *       class is not ready, retry on the requesting Java thread rather than failing the compile
 *       and marking the method not compilable.
 *   </li>
 * </ol>
 *
 * @test
 * @summary Regression: -Xcomp must not mark WhiteBox JNI natives not-compilable during <clinit>
 * @requires vm.flavor == "server"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox jdk.test.whitebox.parser.DiagnosticCommand
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox jdk.test.whitebox.parser.DiagnosticCommand
 * @run main/othervm/timeout=60 -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -Xbootclasspath/a:. -XX:CICompilerCount=2 -XX:-BackgroundCompilation
 *      compiler.whitebox.WhiteBoxNativeNotCompilableWithXcomp
 */

package compiler.whitebox;

import java.lang.reflect.Method;

import jdk.test.whitebox.WhiteBox;

public class WhiteBoxNativeNotCompilableWithXcomp {

    public static void main(String[] args) throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();
        // Exercise JNI path (must succeed).
        wb.getBooleanVMFlag("UseCompiler");

        // Use getMethod (public) so the VM does not walk unrelated declared members;
        // getDeclaredMethod can trigger loading helper types absent from -Xbootclasspath/a:.
        Method getBoolean = WhiteBox.class.getMethod("getBooleanVMFlag", String.class);

        // Encourage parallel compilation work while repeatedly touching WB natives.
        Thread[] helpers = new Thread[Runtime.getRuntime().availableProcessors()];
        for (int t = 0; t < helpers.length; t++) {
            final int id = t;
            helpers[t] = new Thread(() -> {
                for (int i = 0; i < 2000; i++) {
                    wb.getBooleanVMFlag("UseCompiler");
                    wb.getIntVMFlag("CICompilerCount");
                    if ((i + id) % 100 == 0) {
                        Thread.yield();
                    }
                }
            }, "wb-stress-" + id);
            helpers[t].start();
        }
        for (Thread h : helpers) {
            h.join();
        }

        if (!wb.isMethodCompilable(getBoolean, 4, false)) {
            throw new RuntimeException(
                    "REPRO: WhiteBox.getBooleanVMFlag is not C2-compilable after concurrent use under -Xcomp; "
                    + "PrintCompilation / hotspot log may show 'NativeLookup::lookup failed' for WhiteBox natives.");
        }
    }
}
