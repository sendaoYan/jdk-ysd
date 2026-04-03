/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8059624 8064669 8153265 8345760
 * @summary Code cache usage drops after deoptimization when unreachable nmethods are unloaded (uses Serial GC).
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:-TieredCompilation -XX:+WhiteBoxAPI
 *                   -XX:CompileCommand=compileonly,compiler.whitebox.SimpleTestCaseHelper::*
 *                   -XX:-BackgroundCompilation
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockExperimentalVMOptions -XX:+EagerJVMCI
 *                   -XX:+UseSerialGC
 *                   compiler.whitebox.ForceNMethodSweepTest
 */

package compiler.whitebox;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.code.BlobType;

import java.util.EnumSet;

public class ForceNMethodSweepTest extends CompilerWhiteBoxTest {
    public static void main(String[] args) throws Exception {
        CompilerWhiteBoxTest.main(ForceNMethodSweepTest::new, args);
    }
    private final EnumSet<BlobType> blobTypes;
    private ForceNMethodSweepTest(TestCase testCase) {
        super(testCase);
        // to prevent inlining of #method
        WHITE_BOX.testSetDontInlineMethod(method, true);
        blobTypes = BlobType.getAvailable();
    }

    @Override
    protected void test() throws Exception {
        // prime the asserts: get their bytecodes loaded, any lazy computation
        // resolved, and executed once
        Asserts.assertGT(1, 0, "message");
        Asserts.assertLTE(0, 0, "message");
        Asserts.assertLT(-1, 0, "message");

        checkNotCompiled();
        WHITE_BOX.fullGC();
        int usage = getTotalUsage();

        compile();
        checkCompiled();
        int afterCompilation = getTotalUsage();
        Asserts.assertGT(afterCompilation, usage,
                "compilation should increase usage");

        WHITE_BOX.fullGC();
        int afterSweep = getTotalUsage();
        long[] poolUsedAfterSweep = snapshotPoolsUsed();
        Asserts.assertLTE(afterSweep, afterCompilation,
                "sweep shouldn't increase usage");

        deoptimize();
        // The compiler may otherwise recompile this method before the full GC
        // runs, refilling the code cache and masking unloading (JDK-8345760).
        makeNotCompilable();
        if (testCase.isOsr()) {
            WHITE_BOX.makeMethodNotCompilable(method, COMP_LEVEL_ANY, false);
        }
        int afterDeoptAndSweep = afterSweep;
        for (int i = 0; i < 10 && afterDeoptAndSweep >= afterSweep; i++) {
            WHITE_BOX.fullGC();
            afterDeoptAndSweep = getTotalUsage();
        }
        if (afterDeoptAndSweep >= afterSweep) {
            System.err.printf("ForceNMethodSweepTest [%s]: code cache usage did not decrease after deoptimize + full GC%n",
                    testCase.name());
            System.err.printf("  usage=%d afterCompilation=%d afterSweep=%d afterDeoptAndSweep=%d%n",
                    usage, afterCompilation, afterSweep, afterDeoptAndSweep);
            printCodeCacheUsageCompare(poolUsedAfterSweep, afterSweep, afterDeoptAndSweep);
        }
        Asserts.assertLT(afterDeoptAndSweep, afterSweep,
                "sweep after deoptimization should decrease usage");
     }

    private int getTotalUsage() {
        int usage = 0;
        for (BlobType type : blobTypes) {
           usage += type.getMemoryPool().getUsage().getUsed();
        }
        return usage;
    }

    private long[] snapshotPoolsUsed() {
        long[] snap = new long[blobTypes.size()];
        int i = 0;
        for (BlobType type : blobTypes) {
            snap[i++] = type.getMemoryPool().getUsage().getUsed();
        }
        return snap;
    }

    /** For failure diagnosis (JDK-8345760): per-pool before vs after unloading GC. */
    private void printCodeCacheUsageCompare(long[] poolUsedAfterSweep,
                                            int totalAfterSweep,
                                            int totalAfterDeoptGc) {
        System.err.println("per-pool used (bytes), afterSweep vs after deoptimize + full GC:");
        int i = 0;
        for (BlobType type : blobTypes) {
            long afterDeopt = type.getMemoryPool().getUsage().getUsed();
            long atSweep = poolUsedAfterSweep[i++];
            System.err.printf("  %s (%s): afterSweep=%d afterDeoptGC=%d (delta %+d)%n",
                    type.name(), type.getMemoryPool().getName(), atSweep, afterDeopt,
                    afterDeopt - atSweep);
        }
        System.err.printf("  total: afterSweep=%d afterDeoptGC=%d (delta %+d)%n",
                (long) totalAfterSweep, (long) totalAfterDeoptGc,
                (long) totalAfterDeoptGc - totalAfterSweep);
    }
}
