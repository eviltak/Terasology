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
package org.terasology.rendering.nui.widgets.types.builtin;

import org.terasology.rendering.nui.databinding.Binding;

public class ShortWidgetFactory extends NumberWidgetFactory<Short> {

    public ShortWidgetFactory() {
        super(Short.class, Short.TYPE);
    }

    @Override
    protected void setToDefaultValue(Binding<Short> binding) {
        binding.set((short) 0);
    }

    @Override
    protected Short parse(String value) throws NumberFormatException {
        return Short.parseShort(value);
    }
}
