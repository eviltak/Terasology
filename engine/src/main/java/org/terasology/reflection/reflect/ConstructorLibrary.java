/*
 * Copyright 2018 MovingBlocks
 * Copyright (C) 2011 Google Inc.
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
 *
 * Based on Gson v2.6.2 com.google.gson.internal.ConstructorConstructor
 */
package org.terasology.reflection.reflect;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Multiset;
import org.terasology.persistence.typeHandling.InstanceCreator;
import org.terasology.reflection.TypeInfo;
import org.terasology.reflection.reflect.internal.UnsafeAllocator;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class ConstructorLibrary {
    private final Map<Type, InstanceCreator<?>> instanceCreators;

    public ConstructorLibrary() {
        this(new HashMap<>());
    }

    public ConstructorLibrary(Map<Type, InstanceCreator<?>> instanceCreators) {
        this.instanceCreators = instanceCreators;
    }

    public static <T> ObjectConstructor<T> newUnsafeAllocator(TypeInfo<T> typeInfo) {
        return new ObjectConstructor<T>() {
            private final UnsafeAllocator unsafeAllocator = UnsafeAllocator.create();

            @Override
            public T construct() {
                try {
                    return unsafeAllocator.newInstance(typeInfo.getRawType());
                } catch (Exception e) {
                    throw new RuntimeException("Unable to create an instance of " + typeInfo.getType() + ". " +
                                                   "Register an InstanceCreator for this type to fix this problem.", e);
                }
            }
        };
    }

    public static <T> ObjectConstructor<T> newNoArgConstructor(Class<? super T> rawType) {
        @SuppressWarnings("unchecked") // T is the same raw type as is requested
            Constructor<T> constructor = (Constructor<T>) Arrays.stream(rawType.getDeclaredConstructors())
                                                              .min(Comparator.comparingInt(c -> c.getParameterTypes().length))
                                                              .orElse(null);

        if (constructor == null || constructor.getParameterTypes().length != 0) {
            return null;
        }

        if (!constructor.isAccessible()) {
            constructor.setAccessible(true);
        }


        try {
            constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }

        return () -> {
            try {
                return constructor.newInstance();
            } catch (InstantiationException e) {
                throw new RuntimeException("Failed to invoke " + constructor + " with no args", e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException("Failed to invoke " + constructor + " with no args",
                    e.getTargetException());
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        };
    }

    /**
     * Constructors for common interface types like Map and List and their
     * subtypes.
     */
    @SuppressWarnings("unchecked") // use runtime checks to guarantee that 'T' is what it is
    public static <T> ObjectConstructor<T> newDefaultConstructor(TypeInfo<T> typeInfo) {
        Class<T> rawType = typeInfo.getRawType();
        Type type = typeInfo.getType();

        if (rawType.isArray()) {
            return () -> (T) Array.newInstance(rawType.getComponentType(), 0);
        }

        if (Collection.class.isAssignableFrom(rawType)) {
            return (ObjectConstructor<T>) getCollectionConstructor((TypeInfo<? extends Collection<?>>) typeInfo);
        }

        if (Map.class.isAssignableFrom(rawType)) {
            return (ObjectConstructor<T>) getMapConstructor((Class<? extends Map<?, ?>>) rawType);
        }

        return null;
    }

    public static ObjectConstructor<? extends Map<?, ?>> getMapConstructor(Class<? extends Map<?, ?>> rawType) {
        // TODO: Support Guava types?

        if (ConcurrentNavigableMap.class.isAssignableFrom(rawType)) {
            return ConcurrentSkipListMap::new;
        }

        if (ConcurrentMap.class.isAssignableFrom(rawType)) {
            return ConcurrentHashMap::new;
        }

        if (SortedMap.class.isAssignableFrom(rawType)) {
            return TreeMap::new;
        }

        return LinkedHashMap::new;
    }

    public static ObjectConstructor<? extends Collection<?>> getCollectionConstructor(TypeInfo<? extends Collection<?>> typeInfo) {
        Class<? extends Collection<?>> rawType = typeInfo.getRawType();
        Type type = typeInfo.getType();

        // TODO: Support all Guava types?

        if (Multiset.class.isAssignableFrom(rawType)) {
            if (ImmutableMultiset.class.isAssignableFrom(rawType)) {
                return ImmutableMultiset::of;
            }

            return HashMultiset::create;
        }

        if (SortedSet.class.isAssignableFrom(rawType)) {
            if (ImmutableSortedSet.class.isAssignableFrom(rawType)) {
                return ImmutableSortedSet::of;
            }

            return TreeSet::new;
        }

        if (EnumSet.class.isAssignableFrom(rawType)) {
            return () -> {
                if (!(type instanceof ParameterizedType)) {
                    throw new IllegalArgumentException("Invalid EnumSet type: " + type.toString());
                }

                Type elementType = ((ParameterizedType) type).getActualTypeArguments()[0];

                if (!(elementType instanceof Class)) {
                    throw new IllegalArgumentException("Invalid EnumSet type: " + type.toString());
                }

                return EnumSet.noneOf((Class) elementType);
            };
        }

        if (Set.class.isAssignableFrom(rawType)) {
            if (ImmutableSet.class.isAssignableFrom(rawType)) {
                return ImmutableSet::of;
            }

            return LinkedHashSet::new;
        }

        if (Queue.class.isAssignableFrom(rawType)) {
            return ArrayDeque::new;
        }

        if (ImmutableList.class.isAssignableFrom(rawType)) {
            return ImmutableList::of;
        }

        return ArrayList::new;
    }

    public <T> ObjectConstructor<T> get(TypeInfo<T> typeInfo) {
        final Type type = typeInfo.getType();
        final Class<T> rawType = typeInfo.getRawType();

        // first try an instance creator

        @SuppressWarnings("unchecked") // types must agree
        final InstanceCreator<T> typeCreator = (InstanceCreator<T>) instanceCreators.get(type);
        if (typeCreator != null) {
            return () -> typeCreator.createInstance(type);
        }

        // Next try raw type match for instance creators
        @SuppressWarnings("unchecked") // types must agree
        final InstanceCreator<T> rawTypeCreator =
            (InstanceCreator<T>) instanceCreators.get(rawType);
        if (rawTypeCreator != null) {
            return () -> rawTypeCreator.createInstance(type);
        }

        ObjectConstructor<T> defaultConstructor = newNoArgConstructor(rawType);
        if (defaultConstructor != null) {
            return defaultConstructor;
        }

        ObjectConstructor<T> defaultImplementation = newDefaultConstructor(typeInfo);
        if (defaultImplementation != null) {
            return defaultImplementation;
        }

        return newUnsafeAllocator(typeInfo);
    }

    @Override
    public String toString() {
        return instanceCreators.toString();
    }

}
