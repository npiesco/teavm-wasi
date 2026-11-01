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
package org.teavm.classlib.sun.security.provider;

import java.util.Arrays;
import java.util.Collections;
import org.teavm.classlib.impl.security.Digests;
import org.teavm.classlib.java.security.TProvider;

/**
 * {@code sun.security.provider.Sun}: the JDK provider whose message digests and secure random generators this
 * platform implements. Its version is the Java platform's.
 */
public final class TSun extends TProvider {
    private static final long serialVersionUID = 6440182097568097204L;
    public static final TSun INSTANCE = new TSun();

    private TSun() {
        super("SUN", System.getProperty("java.specification.version"), "SUN (DSA key/parameter generation; DSA "
                + "signing; SHA-1, MD5 digests; SecureRandom; X.509 certificates; PKCS12, JKS & DKS keystores; PKIX "
                + "CertPathValidator; PKIX CertPathBuilder; LDAP, Collection CertStores, JavaPolicy Policy; "
                + "JavaLoginConfig Configuration)");
        for (String name : Digests.standardNames()) {
            putService(new Service(this, "MessageDigest", name, "sun.security.provider." + name.replace("-", "")
                    .replace("/", "_"), Arrays.asList(Digests.aliases(name)), null));
        }
        for (String name : new String[] { "NativePRNG", "DRBG", "SHA1PRNG", "NativePRNGBlocking",
                "NativePRNGNonBlocking" }) {
            putService(new Service(this, "SecureRandom", name, "sun.security.provider." + name,
                    Collections.emptyList(), null));
        }
    }
}
