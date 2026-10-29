/*
 *  Copyright 2026 Spooler contributors.
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
package org.teavm.classlib.java.nio.file;

public class TInvalidPathException extends IllegalArgumentException {
    private final String input;
    private final int index;

    public TInvalidPathException(String input, String reason, int index) {
        super(reason);
        if (input == null || reason == null) {
            throw new NullPointerException();
        }
        if (index < -1) {
            throw new IllegalArgumentException();
        }
        this.input = input;
        this.index = index;
    }

    public TInvalidPathException(String input, String reason) {
        this(input, reason, -1);
    }

    public String getInput() {
        return input;
    }

    public String getReason() {
        return super.getMessage();
    }

    public int getIndex() {
        return index;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(getReason());
        if (index > -1) {
            sb.append(" at index ").append(index);
        }
        sb.append(": ").append(input);
        return sb.toString();
    }
}