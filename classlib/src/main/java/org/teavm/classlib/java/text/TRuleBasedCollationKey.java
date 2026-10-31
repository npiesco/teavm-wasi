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
 */package org.teavm.classlib.java.text;

final class TRuleBasedCollationKey extends TCollationKey {
    private final String key;

    TRuleBasedCollationKey(String source, String key) {
        super(source);
        this.key = key;
    }

    @Override
    public int compareTo(TCollationKey target) {
        int result = key.compareTo(((TRuleBasedCollationKey) target).key);
        if (result < 0) {
            return -1;
        } else if (result > 0) {
            return 1;
        }
        return 0;
    }

    @Override
    public boolean equals(Object target) {
        if (this == target) {
            return true;
        }
        if (target == null || !getClass().equals(target.getClass())) {
            return false;
        }
        return key.equals(((TRuleBasedCollationKey) target).key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public byte[] toByteArray() {
        byte[] dest = new byte[key.length() * 2];
        for (int i = 0; i < key.length(); ++i) {
            char c = key.charAt(i);
            dest[i * 2] = (byte) (c >>> 8);
            dest[i * 2 + 1] = (byte) c;
        }
        return dest;
    }
}