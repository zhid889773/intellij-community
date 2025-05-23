// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;

@ApiStatus.NonExtendable
public abstract class Interner<T> {
  /**
   * Allow reusing structurally equal objects to avoid memory being wasted on them. Objects are cached on weak references
   * and garbage-collected when not needed anymore.
   */
  public static @NotNull <T> Interner<T> createWeakInterner() {
    return new WeakInterner<>();
  }

  public static @NotNull Interner<String> createStringInterner() {
    return createInterner();
  }

  public static <T> @NotNull Interner<T> createInterner() {
    return new HashSetInterner<>();
  }

  public abstract @NotNull T intern(@NotNull T name);

  public abstract void clear();

  public abstract @NotNull @Unmodifiable Set<T> getValues();
}
