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

import java.io.IOException;

public class TFileSystemException extends IOException {
    private final String file;
    private final String other;

    public TFileSystemException(String file) {
        super((String) null);
        this.file = file;
        this.other = null;
    }

    public TFileSystemException(String file, String other, String reason) {
        super(reason);
        this.file = file;
        this.other = other;
    }

    public String getFile() {
        return file;
    }

    public String getOtherFile() {
        return other;
    }

    public String getReason() {
        return super.getMessage();
    }

    @Override
    public String getMessage() {
        if (file == null && other == null) {
            return getReason();
        }
        StringBuilder sb = new StringBuilder();
        if (file != null) {
            sb.append(file);
        }
        if (other != null) {
            sb.append(" -> ").append(other);
        }
        if (getReason() != null) {
            sb.append(": ").append(getReason());
        }
        return sb.toString();
    }
}