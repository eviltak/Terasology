/*
 * Copyright 2013 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.persistence.typeHandling.mathTypes;

import com.google.common.collect.Maps;
import org.terasology.engine.module.UriUtil;
import org.terasology.math.Region3i;
import org.terasology.math.geom.Vector3i;
import org.terasology.persistence.typeHandling.PersistedData;
import org.terasology.persistence.typeHandling.PersistedDataMap;
import org.terasology.persistence.typeHandling.PersistedDataSerializer;
import org.terasology.persistence.typeHandling.TypeHandler;

import java.util.Map;

/**
 */
public class Region3iTypeHandler extends TypeHandler<Region3i> {

    private static final String MIN_FIELD = UriUtil.normalise("min");
    private static final String SIZE_FIELD = UriUtil.normalise("size");

    private TypeHandler<Vector3i> vector3iTypeHandler;

    public Region3iTypeHandler(TypeHandler<Vector3i> vector3iTypeHandler) {
        this.vector3iTypeHandler = vector3iTypeHandler;
    }

    @Override
    public PersistedData serializeNonNull(Region3i value, PersistedDataSerializer serializer) {
        if (value == null) {
            return serializer.serializeNull();
        } else {
            Map<String, PersistedData> map = Maps.newLinkedHashMap();

            map.put(MIN_FIELD, vector3iTypeHandler.serialize(value.min(), serializer));
            map.put(SIZE_FIELD, vector3iTypeHandler.serialize(value.size(), serializer));

            return serializer.serialize(map);
        }
    }

    @Override
    public Region3i deserialize(PersistedData data) {
        if (!data.isNull() && data.isValueMap()) {
            PersistedDataMap map = data.getAsValueMap();

            Vector3i min = vector3iTypeHandler.deserialize(map.get(MIN_FIELD));
            Vector3i size = vector3iTypeHandler.deserialize(map.get(SIZE_FIELD));

            return Region3i.createFromMinAndSize(min, size);
        }
        return null;
    }

}
