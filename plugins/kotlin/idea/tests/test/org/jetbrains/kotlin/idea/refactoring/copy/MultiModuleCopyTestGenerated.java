// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.copy;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.base.test.TestRoot;
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

/**
 * This class is generated by {@link org.jetbrains.kotlin.testGenerator.generator.TestGenerator}.
 * DO NOT MODIFY MANUALLY.
 */
@SuppressWarnings("all")
@TestRoot("idea/tests")
@TestDataPath("$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
@TestMetadata("testData/refactoring/copyMultiModule")
public class MultiModuleCopyTestGenerated extends AbstractMultiModuleCopyTest {
    @java.lang.Override
    @org.jetbrains.annotations.NotNull
    public final KotlinPluginMode getPluginMode() {
        return KotlinPluginMode.K1;
    }

    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
    }

    @TestMetadata("fileNotUnderSourceRoot/fileNotUnderSourceRoot.test")
    public void testFileNotUnderSourceRoot_FileNotUnderSourceRoot() throws Exception {
        runTest("testData/refactoring/copyMultiModule/fileNotUnderSourceRoot/fileNotUnderSourceRoot.test");
    }

    @TestMetadata("internalReferencesToAnotherModule2/internalReferencesToAnotherModule.test")
    public void testInternalReferencesToAnotherModule2_InternalReferencesToAnotherModule() throws Exception {
        runTest("testData/refactoring/copyMultiModule/internalReferencesToAnotherModule2/internalReferencesToAnotherModule.test");
    }

    @TestMetadata("referencesToUnrelatedModule/referencesToUnrelatedModule.test")
    public void testReferencesToUnrelatedModule_ReferencesToUnrelatedModule() throws Exception {
        runTest("testData/refactoring/copyMultiModule/referencesToUnrelatedModule/referencesToUnrelatedModule.test");
    }
}
