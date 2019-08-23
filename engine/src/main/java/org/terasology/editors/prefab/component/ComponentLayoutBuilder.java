/*
 * Copyright 2019 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.editors.prefab.component;

import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.engine.module.ModuleContext;
import org.terasology.engine.module.ModuleManager;
import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.module.Module;
import org.terasology.module.ModuleEnvironment;
import org.terasology.naming.Name;
import org.terasology.reflection.TypeInfo;
import org.terasology.reflection.TypeRegistry;
import org.terasology.rendering.nui.databinding.Binding;
import org.terasology.rendering.nui.databinding.DefaultBinding;
import org.terasology.rendering.nui.itemRendering.StringTextRenderer;
import org.terasology.rendering.nui.layouts.ColumnLayout;
import org.terasology.rendering.nui.widgets.UIDropdownScrollable;
import org.terasology.rendering.nui.widgets.UILabel;
import org.terasology.rendering.nui.widgets.types.TypeWidgetFactory;
import org.terasology.rendering.nui.widgets.types.TypeWidgetLibrary;
import org.terasology.rendering.nui.widgets.types.builtin.util.ExpandableLayoutBuilder;
import org.terasology.rendering.nui.widgets.types.builtin.util.FieldsWidgetBuilder;
import org.terasology.utilities.ReflectionUtil;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ComponentLayoutBuilder extends ExpandableLayoutBuilder<Component> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentLayoutBuilder.class);

    private final ModuleManager moduleManager;
    private final List<TypeInfo<Component>> allowedComponentTypes;
    private final TypeWidgetLibrary library;

    public ComponentLayoutBuilder(ModuleManager moduleManager,
                                  TypeRegistry typeRegistry,
                                  TypeWidgetLibrary library) {
        this.moduleManager = moduleManager;
        this.library = library;

        Module contextModule = ModuleContext.getContext();
        ModuleEnvironment environment = this.moduleManager.getEnvironment();

        Set<Name> allowedProvidingModules =
            ImmutableSet.<Name>builder()
                .add(contextModule.getId())
                .addAll(environment.getDependencyNamesOf(contextModule.getId()))
                .build();

        allowedComponentTypes =
            typeRegistry.getSubtypesOf(Component.class).stream()
                .filter(clazz -> allowedProvidingModules.contains(environment.getModuleProviding(clazz)))
                .map(type -> (TypeInfo<Component>) TypeInfo.of(type))
                .sorted(Comparator.comparing(this::getTypeUri))
                .collect(Collectors.toList());
    }

    private static boolean fieldFilter(Field field) {
        return !EntityRef.class.isAssignableFrom(field.getType());
    }

    private String getTypeUri(TypeInfo<?> typeInfo) {
        return ReflectionUtil.getTypeUri(typeInfo.getType(), moduleManager.getEnvironment());
    }

    @Override
    protected void populate(Binding<Component> binding, ColumnLayout layout, ColumnLayout mainLayout) {
        UILabel nameWidget = mainLayout.find(TypeWidgetFactory.LABEL_WIDGET_ID, UILabel.class);
        assert nameWidget != null;

        Binding<TypeInfo<Component>> selectedComponentType =
            new DefaultBinding<TypeInfo<Component>>() {
                @Override
                public void set(TypeInfo<Component> value) {
                    if (get() != null &&
                            ("".equals(nameWidget.getText()) || getTypeUri(get()).equals(nameWidget.getText()))) {
                        nameWidget.setText(getTypeUri(value));
                    }

                    super.set(value);

                    try {
                        if (!value.getRawType().isInstance(binding.get())) {
                            binding.set(value.getRawType().newInstance());
                        }
                    } catch (InstantiationException | IllegalAccessException e) {
                        LOGGER.error("Could not instantiate component of type {}", value, e);
                    }
                }
            };

        if (binding.get() != null &&
                allowedComponentTypes.stream()
                    .anyMatch(allowed -> allowed.getRawType().equals(binding.get().getClass()))) {
            selectedComponentType.set((TypeInfo<Component>) TypeInfo.of(binding.get().getClass()));
        } else {
            selectedComponentType.set(allowedComponentTypes.get(0));
        }

        UIDropdownScrollable<TypeInfo<Component>> componentTypeDropdown = new UIDropdownScrollable<>();

        componentTypeDropdown.setOptions(allowedComponentTypes);
        componentTypeDropdown.bindSelection(selectedComponentType);

        componentTypeDropdown.setOptionRenderer(new StringTextRenderer<TypeInfo<Component>>() {
            @Override
            public String getString(TypeInfo<Component> value) {
                return getTypeUri(value);
            }
        });

        // TODO: Translate
        componentTypeDropdown.setTooltip("Select the component type");

        layout.addWidget(componentTypeDropdown);

        FieldsWidgetBuilder<Component> fieldsWidgetBuilder =
            new FieldsWidgetBuilder<>(selectedComponentType.get(), this.library, ComponentLayoutBuilder::fieldFilter);
        fieldsWidgetBuilder.getFieldWidgets(binding).forEach(layout::addWidget);
    }
}
