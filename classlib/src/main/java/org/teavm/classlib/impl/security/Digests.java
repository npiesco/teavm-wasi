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
package org.teavm.classlib.impl.security;

/**
 * The message digests of the JDK's SUN provider, by standard name, alias or OID, compared case-insensitively.
 */
public final class Digests {
    private Digests() {
    }

    public static String standardName(String name) {
        return canonical(name);
    }

    public static DigestEngine create(String name) {
        switch (canonical(name)) {
            case "MD2":
                return new Md2();
            case "MD5":
                return new Md5();
            case "SHA-1":
                return new Sha1();
            case "SHA-224":
                return new Sha256(28);
            case "SHA-256":
                return new Sha256(32);
            case "SHA-384":
                return new Sha512(48);
            case "SHA-512":
                return new Sha512(64);
            case "SHA-512/224":
                return new Sha512(28);
            case "SHA-512/256":
                return new Sha512(32);
            case "SHA3-224":
                return new Sha3(28);
            case "SHA3-256":
                return new Sha3(32);
            case "SHA3-384":
                return new Sha3(48);
            case "SHA3-512":
                return new Sha3(64);
            default:
                return null;
        }
    }

    private static String canonical(String name) {
        String upper = name.toUpperCase();
        if (upper.startsWith("OID.")) {
            upper = upper.substring(4);
        }
        switch (upper) {
            case "1.2.840.113549.2.2":
                return "MD2";
            case "1.2.840.113549.2.5":
                return "MD5";
            case "SHA":
            case "SHA1":
            case "1.3.14.3.2.26":
                return "SHA-1";
            case "SHA224":
            case "2.16.840.1.101.3.4.2.4":
                return "SHA-224";
            case "SHA256":
            case "2.16.840.1.101.3.4.2.1":
                return "SHA-256";
            case "SHA384":
            case "2.16.840.1.101.3.4.2.2":
                return "SHA-384";
            case "SHA512":
            case "2.16.840.1.101.3.4.2.3":
                return "SHA-512";
            case "SHA512/224":
            case "2.16.840.1.101.3.4.2.5":
                return "SHA-512/224";
            case "SHA512/256":
            case "2.16.840.1.101.3.4.2.6":
                return "SHA-512/256";
            case "2.16.840.1.101.3.4.2.7":
                return "SHA3-224";
            case "2.16.840.1.101.3.4.2.8":
                return "SHA3-256";
            case "2.16.840.1.101.3.4.2.9":
                return "SHA3-384";
            case "2.16.840.1.101.3.4.2.10":
                return "SHA3-512";
            default:
                return upper;
        }
    }
}
