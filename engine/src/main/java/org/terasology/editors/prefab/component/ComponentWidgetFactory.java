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

import org.terasology.engine.module.ModuleManager;
import org.terasology.entitySystem.Component;
import org.terasology.reflection.TypeInfo;
import org.terasology.reflection.TypeRegistry;
import org.terasology.registry.In;
import org.terasology.rendering.nui.widgets.types.RegisterTypeWidgetFactory;
import org.terasology.rendering.nui.widgets.types.TypeWidgetBuilder;
import org.terasology.rendering.nui.widgets.types.TypeWidgetFactory;
import org.terasology.rendering.nui.widgets.types.TypeWidgetLibrary;

import java.util.Optional;

@RegisterTypeWidgetFactory
public class ComponentWidgetFactory implements TypeWidgetFactory {
    @In
    private TypeRegistry typeRegistry;

    @In
    private ModuleManager moduleManager;

    @Override
    public <T> Optional<TypeWidgetBuilder<T>> create(TypeInfo<T> type, TypeWidgetLibrary library) {
        if (!Component.class.equals(type.getRawType())) {
            return Optional.empty();
        }

        TypeWidgetBuilder<Component> builder = new ComponentLayoutBuilder(moduleManager, typeRegistry, library);

        return Optional.of((TypeWidgetBuilder<T>) builder);
    }
}
