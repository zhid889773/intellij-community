// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.gist;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.NullableFunction;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.indexing.FileContentImpl;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PsiFileGistImpl<Data> implements PsiFileGist<Data> {
  private static final ModificationTracker ourReindexTracker = () -> ((GistManagerImpl)GistManager.getInstance()).getReindexCount();
  private final VirtualFileGist<Data> myPersistence;
  private final VirtualFileGist.GistCalculator<Data> myCalculator;
  private final Key<CachedValue<Data>> myCacheKey;

  PsiFileGistImpl(@NotNull String id,
                  int version,
                  @NotNull DataExternalizer<Data> externalizer,
                  @NotNull NullableFunction<? super PsiFile, ? extends Data> calculator) {
    myCalculator = (project, file) -> {
      PsiFile psiFile = getPsiFile(project, file);
      return psiFile == null ? null : calculator.fun(psiFile);
    };
    myPersistence = GistManager.getInstance().newVirtualFileGist(id, version, externalizer, myCalculator);
    myCacheKey = Key.create("PsiFileGist " + id);
  }

  @Override
  public Data getFileData(@NotNull PsiFile file) {
    ThreadingAssertions.assertReadAccess();

    if (shouldUseMemoryStorage(file)) {
      return CachedValuesManager.getManager(file.getProject()).getCachedValue(
        file, myCacheKey, () -> {
          Data data = myCalculator.calcData(file.getProject(), getVirtualFile(file));
          return CachedValueProvider.Result.create(data, file, ourReindexTracker);
        }, false);
    }

    file.putUserData(myCacheKey, null);
    return myPersistence.getFileData(file.getProject(), getVirtualFile(file));
  }

  private static @NotNull VirtualFile getVirtualFile(@NotNull PsiFile file) {
    return file.getViewProvider().getVirtualFile();
  }

  private static boolean shouldUseMemoryStorage(@NotNull PsiFile file) {
    if (!(getVirtualFile(file) instanceof NewVirtualFile)) return true;

    PsiDocumentManager pdm = PsiDocumentManager.getInstance(file.getProject());
    Document document = pdm.getCachedDocument(file);
    return document != null && (pdm.isUncommited(document) || FileDocumentManager.getInstance().isDocumentUnsaved(document));
  }

  private static @Nullable PsiFile getPsiFile(@NotNull Project project, @NotNull VirtualFile file) {
    PsiFile psi = PsiManager.getInstance(project).findFile(file);
    if (!(psi instanceof PsiFileImpl) || ((PsiFileImpl)psi).isContentsLoaded()) {
      return psi;
    }

    FileType fileType = file.getFileType();
    if (!(fileType instanceof LanguageFileType)) return null;

    PsiFile recreatedFile =
      FileContentImpl.createFileFromText(project, psi.getViewProvider().getContents(), (LanguageFileType)fileType, file, file.getName());

    if (recreatedFile instanceof PsiFileImpl psiFile) {
      psiFile.setOriginalFile(psi); // let clients know that this file is recreated from another
    }

    return recreatedFile;
  }
}
