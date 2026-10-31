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
 */package org.teavm.classlib.java.util;

import java.util.Objects;

public final class TStringJoiner {
    private final String prefix;
    private final String delimiter;
    private final String suffix;
    private StringBuilder value;
    private String emptyValue;

    public TStringJoiner(CharSequence delimiter) {
        this(delimiter, "", "");
    }

    public TStringJoiner(CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        Objects.requireNonNull(prefix, "The prefix must not be null");
        Objects.requireNonNull(delimiter, "The delimiter must not be null");
        Objects.requireNonNull(suffix, "The suffix must not be null");
        this.prefix = prefix.toString();
        this.delimiter = delimiter.toString();
        this.suffix = suffix.toString();
    }

    public TStringJoiner setEmptyValue(CharSequence emptyValue) {
        this.emptyValue = Objects.requireNonNull(emptyValue, "The empty value must not be null").toString();
        return this;
    }

    @Override
    public String toString() {
        if (value == null) {
            return emptyValue != null ? emptyValue : prefix + suffix;
        }
        return prefix + value + suffix;
    }

    public TStringJoiner add(CharSequence newElement) {
        prepareBuilder().append(String.valueOf(newElement));
        return this;
    }

    // As the JDK's: the other joiner's content without its prefix and suffix, captured before appending, so a
    // joiner can be merged into itself.
    public TStringJoiner merge(TStringJoiner other) {
        Objects.requireNonNull(other);
        if (other.value == null) {
            return this;
        }
        String content = other.value.toString();
        prepareBuilder().append(content);
        return this;
    }

    private StringBuilder prepareBuilder() {
        if (value != null) {
            value.append(delimiter);
        } else {
            value = new StringBuilder();
        }
        return value;
    }

    public int length() {
        if (value == null) {
            return emptyValue != null ? emptyValue.length() : prefix.length() + suffix.length();
        }
        return prefix.length() + value.length() + suffix.length();
    }
}