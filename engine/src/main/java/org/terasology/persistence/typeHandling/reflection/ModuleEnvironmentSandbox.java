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
package org.terasology.persistence.typeHandling.reflection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;
import org.terasology.engine.SimpleUri;
import org.terasology.module.ModuleEnvironment;
import org.terasology.naming.Name;
import org.terasology.persistence.typeHandling.TypeHandler;
import org.terasology.reflection.TypeInfo;
import org.terasology.utilities.ReflectionUtil;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

public class ModuleEnvironmentSandbox implements SerializationSandbox {
    private final ModuleEnvironment moduleEnvironment;

    public ModuleEnvironmentSandbox(ModuleEnvironment moduleEnvironment) {
        this.moduleEnvironment = moduleEnvironment;
    }

    @Override
    public <T> Optional<Class<? extends T>> findSubTypeOf(String subTypeIdentifier, Class<T> clazz) {

        Iterator<Class<? extends T>> possibilities =
            moduleEnvironment
                .getSubtypesOf(clazz, subclass -> doesSubclassMatch(subclass, subTypeIdentifier))
                .iterator();

        if (possibilities.hasNext()) {
            Class<? extends T> possibility = possibilities.next();

            // Multiple possibilities
            if (possibilities.hasNext()) {
                return Optional.empty();
            }

            return Optional.of(possibility);
        }

        // No possibility
        return Optional.empty();
    }

    private boolean doesSubclassMatch(Class<?> subclass, String subTypeIdentifier) {
        if (subclass == null) {
            return false;
        }

        SimpleUri subTypeUri = new SimpleUri(subTypeIdentifier);
        Name subTypeName = subTypeUri.isValid() ? subTypeUri.getObjectName() : new Name(subTypeIdentifier);

        Name providingModule = moduleEnvironment.getModuleProviding(subclass);

        // TODO: Use ModuleContext if invalid uri
        if (subTypeUri.isValid()) {
            if (!subTypeUri.getModuleName().equals(providingModule)) {
                return false;
            }
        }

        return subTypeName.toString().equals((subclass.getName())) || subTypeName.toString().equals((subclass.getSimpleName()));
    }

    @Override
    public <T> String getSubTypeIdentifier(Class<? extends T> subType, Class<T> baseType) {
        SimpleUri subTypeUri = ReflectionUtil.simpleUriOfType(subType, moduleEnvironment);

        long subTypesWithSameUri = Streams.stream(moduleEnvironment.getSubtypesOf(baseType))
                                       .map(type -> ReflectionUtil.simpleUriOfType(type, moduleEnvironment))
                                       .filter(subTypeUri::equals)
                                       .count();

        Preconditions.checkArgument(subTypesWithSameUri > 0,
            "Subtype was not found in the module environment");

        if (subTypesWithSameUri > 1) {
            // More than one subType with same SimpleUri, use fully qualified name
            return subType.getName();
        }

        return subTypeUri.toString();
    }

    @Override
    public <T> boolean isValidTypeHandlerDeclaration(TypeInfo<T> type, TypeHandler<T> typeHandler) {
        Name moduleDeclaringHandler = moduleEnvironment.getModuleProviding(typeHandler.getClass());

        // If handler was declared outside of a module (engine or somewhere else), we allow it
        // TODO: Possibly find better way to refer to engine module
        if (moduleDeclaringHandler == null || moduleDeclaringHandler.equals(new Name("engine"))) {
            return true;
        }

        // Handler has been declared in a module, proceed accordingly

        if (type.getRawType().getClassLoader() == null) {
            // Modules cannot specify handlers for builtin classes
            return false;
        }

        Name moduleDeclaringType = moduleEnvironment.getModuleProviding(type.getRawType());

        // Both the type and the handler must come from the same module
        return Objects.equals(moduleDeclaringType, moduleDeclaringHandler);
    }

}
