/*
 *  Copyright 2026 spooler contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.classlib.java.text;

import org.teavm.classlib.impl.unicode.UnicodeHelper;
import org.teavm.classlib.impl.unicode.UnicodeSupport;
import org.teavm.model.MethodReference;
import org.teavm.platform.metadata.MetadataGenerator;
import org.teavm.platform.metadata.MetadataGeneratorContext;
import org.teavm.platform.metadata.Resource;
import org.teavm.platform.metadata.StringResource;

public class NormalizerMetadataGenerator implements MetadataGenerator {
    @Override
    public Resource generateMetadata(MetadataGeneratorContext context, MethodReference method) {
        int[] data;
        switch (method.getName()) {
            case "obtainCombiningClasses":
                data = UnicodeSupport.getCombiningClasses();
                break;
            case "obtainDecompositions":
                data = UnicodeSupport.getDecompositions();
                break;
            case "obtainCompositions":
                data = UnicodeSupport.getCompositions();
                break;
            default:
                return null;
        }
        StringResource res = context.createResource(StringResource.class);
        res.setValue(UnicodeHelper.encodeIntDiff(data));
        return res;
    }
}
