/*
 * Copyright 2017 MovingBlocks
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
package org.terasology.persistence.typeHandling;

import org.terasology.reflection.TypeInfo;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Creates type handlers for a set of types. Type handler factories are generally used when a set of types
 * are similar in serialization structure.
 */
public interface TypeHandlerFactory {
    /**
     * Creates a {@link TypeHandler} for the given type {@link T}. If the type is not supported by
     * this {@link TypeHandlerFactory}, {@link Optional#empty()} is returned.
     *
     * This method is usually called only once for a type, so all expensive pre-computations and reflection
     * operations can be performed here so that the generated
     * {@link TypeHandler#serialize(Object, PersistedDataSerializer)} and
     * {@link TypeHandler#deserialize(PersistedData)} implementations are fast.
     *
     * @param <T> The type for which a {@link TypeHandler} must be generated.
     * @param typeInfo The {@link TypeInfo} of the type for which a {@link TypeHandler} must be generated.
     * @param context The {@link TypeHandlerLibrary} for which the {@link TypeHandler}
     *                                 is being created.
     * @return An {@link Optional} wrapping the created {@link TypeHandler}, or {@link Optional#empty()}
     * if the type is not supported by this {@link TypeHandlerFactory}.
     */
    <T> Optional<TypeHandler<T>> create(TypeInfo<T> typeInfo, TypeHandlerContext context);

    /**
     * Creates a {@link TypeHandlerFactory} that only produces type handlers for the given type
     * using the given {@link TypeHandler} producer method.
     * @param type The {@link TypeInfo} describing the type for which the factory generates type handlers
     * @param handlerProducer The {@link TypeHandler} producer method
     * @param <R> The type for which the factory generates type handlers
     * @return The created {@link TypeHandlerFactory}.
     */
    static <R> TypeHandlerFactory of(TypeInfo<R> type,
                                     BiFunction<TypeInfo<R>, TypeHandlerContext, TypeHandler<R>> handlerProducer) {
        return new TypeHandlerFactory() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> Optional<TypeHandler<T>> create(TypeInfo<T> typeInfo, TypeHandlerContext context) {
                if (typeInfo.equals(type)) {
                    return Optional.of((TypeHandler<T>) handlerProducer.apply(type, context));
                }
                return Optional.empty();
            }
        };
    }

    /**
     * Creates a {@link TypeHandlerFactory} that only produces type handlers for the given type
     * using the given {@link TypeHandler} producer method.
     * @param clazz The {@link Class} of the type for which the factory generates type handlers
     * @param handlerProducer The {@link TypeHandler} producer method
     * @param <R> The type for which the factory generates type handlers
     * @return The created {@link TypeHandlerFactory}.
     */
    static <R> TypeHandlerFactory of(Class<R> clazz,
                                     BiFunction<TypeInfo<R>, TypeHandlerContext, TypeHandler<R>> handlerProducer) {
        return of(TypeInfo.of(clazz), handlerProducer);
    }
}
