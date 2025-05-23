// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Setter;
import com.intellij.ui.dsl.builder.Panel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import kotlin.reflect.KMutableProperty0;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @deprecated Use identical {@link BeanConfigurable} for replacement
 */
@Deprecated(forRemoval = true)
public abstract class ConfigurableBuilder extends UiDslUnnamedConfigurable.Simple
  implements UiDslUnnamedConfigurable, ConfigurableWithOptionDescriptors {

  private @Nullable @NlsContexts.BorderTitle String myTitle;

  private interface PropertyAccessor<T> {
    T getValue();

    void setValue(@NotNull T value);
  }

  private static final class CallbackAccessor<T> implements PropertyAccessor<T> {
    private final @NotNull Supplier<? extends T> myGetter;
    private final @NotNull Setter<? super T> mySetter;

    private CallbackAccessor(@NotNull Supplier<? extends T> getter, @NotNull Setter<? super T> setter) {
      myGetter = getter;
      mySetter = setter;
    }

    @Override
    public T getValue() {
      return myGetter.get();
    }

    @Override
    public void setValue(@NotNull T value) {
      mySetter.set(value);
    }
  }

  private static final class KPropertyAccessor<T> implements PropertyAccessor<T> {

    private final @NotNull KMutableProperty0<T> myProperty;

    private KPropertyAccessor(@NotNull KMutableProperty0<T> property) {
      myProperty = property;
    }

    @Override
    public T getValue() {
      return myProperty.get();
    }

    @Override
    public void setValue(@NotNull T value) {
      myProperty.set(value);
    }
  }

  abstract static class BeanField<C extends JComponent, T> {

    @ApiStatus.Internal
    final @NotNull PropertyAccessor<T> myAccessor;

    private @Nullable C myComponent;

    private BeanField(@NotNull PropertyAccessor<T> accessor) {
      myAccessor = accessor;
    }

    @NotNull
    C getComponent() {
      if (myComponent == null) {
        myComponent = createComponent();
      }
      return myComponent;
    }

    protected abstract @NotNull C createComponent();

    boolean isModified() {
      final Object componentValue = getComponentValue();
      final Object beanValue = myAccessor.getValue();
      return !Comparing.equal(componentValue, beanValue);
    }

    void apply() {
      myAccessor.setValue(getComponentValue());
    }

    void reset() {
      setComponentValue(myAccessor.getValue());
    }

    protected abstract T getComponentValue();

    protected abstract void setComponentValue(T value);
  }

  private static final class CheckboxField extends BeanField<JCheckBox, @NotNull Boolean> {
    private final @NotNull @NlsContexts.Checkbox String myTitle;

    private CheckboxField(@NotNull PropertyAccessor<Boolean> accessor, @NotNull @NlsContexts.Checkbox String title) {
      super(accessor);
      myTitle = title;
    }

    private @NotNull String getTitle() {
      return myTitle;
    }

    private void setAccessorValue(boolean value) {
      myAccessor.setValue(value);
    }

    private boolean getAccessorValue() {
      return myAccessor.getValue();
    }

    @Override
    protected @NotNull JCheckBox createComponent() {
      return new JCheckBox(myTitle);
    }

    @Override
    protected Boolean getComponentValue() {
      return getComponent().isSelected();
    }

    @Override
    protected void setComponentValue(@NotNull Boolean value) {
      getComponent().setSelected(value);
    }
  }

  private final List<BeanField<?, ?>> myFields = new ArrayList<>();

  protected ConfigurableBuilder() {
  }

  protected ConfigurableBuilder(@Nullable @NlsContexts.BorderTitle String title) {
    myTitle = title;
  }

  @ApiStatus.Internal
  public @Nullable String getTitle() {
    return myTitle;
  }

  /**
   * Adds check box with given {@code title}.
   * Initial checkbox value is obtained from {@code getter}.
   * After the apply, the value from the check box is written back to model via {@code setter}.
   */
  protected void checkBox(@NotNull @NlsContexts.Checkbox String title, @NotNull Getter<@NotNull Boolean> getter, @NotNull Setter<? super Boolean> setter) {
    myFields.add(new CheckboxField(new CallbackAccessor<>(getter, setter), title));
  }

  protected void checkBox(@NotNull @NlsContexts.Checkbox String title, @NotNull KMutableProperty0<@NotNull Boolean> prop) {
    myFields.add(new CheckboxField(new KPropertyAccessor<>(prop), title));
  }

  /**
   * Adds custom component (e.g. edit box).
   * Initial value is obtained from {@code beanGetter} and applied to the component via {@code componentSetter}.
   * E.g. text is read from the model and set to the edit box.
   * After the apply, the value from the component is queried via {@code componentGetter} and written back to model via {@code beanSetter}.
   * E.g. text from the edit box is queried and saved back to model bean.
   */
  protected <V> void component(@NotNull JComponent component,
                               @NotNull Supplier<? extends V> beanGetter,
                               @NotNull Setter<? super V> beanSetter,
                               @NotNull Supplier<? extends V> componentGetter,
                               @NotNull Setter<? super V> componentSetter) {
    BeanField<JComponent, V> field = new BeanField<>(new CallbackAccessor<>(beanGetter, beanSetter)) {
      @Override
      protected @NotNull JComponent createComponent() {
        return component;
      }

      @Override
      protected V getComponentValue() {
        return componentGetter.get();
      }

      @Override
      protected void setComponentValue(V value) {
        componentSetter.set(value);
      }
    };
    myFields.add(field);
  }

  @Override
  public @Unmodifiable @NotNull List<OptionDescription> getOptionDescriptors(@NotNull String configurableId,
                                                                             @NotNull Function<? super String, @NlsContexts.Command String> nameConverter) {
    List<ConfigurableBuilder.CheckboxField> boxes = JBIterable.from(myFields).filter(CheckboxField.class).toList();
    return ContainerUtil.map(boxes, box -> new BooleanOptionDescription(nameConverter.apply(box.getTitle()), configurableId) {
      @Override
      public boolean isOptionEnabled() {
        return box.getAccessorValue();
      }

      @Override
      public void setOptionState(boolean enabled) {
        box.setAccessorValue(enabled);
      }
    });
  }

  @Override
  public void createContent(@NotNull Panel builder) {
    ConfigurableBuilderHelper.buildFieldsPanel(builder, myTitle, myFields);
  }

  @ApiStatus.Internal
  public static @Nullable String getConfigurableTitle(@NotNull UnnamedConfigurable configurable) {
    if (configurable instanceof BeanConfigurable<?>) {
      return ((BeanConfigurable<?>)configurable).getTitle();
    }
    if (configurable instanceof ConfigurableBuilder) {
      return ((ConfigurableBuilder)configurable).getTitle();
    }
    return null;
  }
}
