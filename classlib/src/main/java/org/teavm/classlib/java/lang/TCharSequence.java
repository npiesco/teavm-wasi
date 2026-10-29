/*
 *  Copyright 2013 Alexey Andreev.
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
package org.teavm.classlib.java.lang;

import org.teavm.classlib.java.util.stream.TIntStream;

public interface TCharSequence {
    int length();

    char charAt(int index);

    TCharSequence subSequence(int start, int end);

    @Override
    String toString();

    default TIntStream chars() {
        TIntStream.Builder builder = TIntStream.builder();
        for (int i = 0; i < length(); ++i) {
            builder.add(charAt(i));
        }
        return builder.build();
    }

    // Supplementary characters become one code point; an unpaired surrogate is passed through as is.
    default TIntStream codePoints() {
        TIntStream.Builder builder = TIntStream.builder();
        int i = 0;
        while (i < length()) {
            char c = charAt(i++);
            if (Character.isHighSurrogate(c) && i < length() && Character.isLowSurrogate(charAt(i))) {
                builder.add(Character.toCodePoint(c, charAt(i++)));
            } else {
                builder.add(c);
            }
        }
        return builder.build();
    }
}
