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
 * SHA-384, SHA-512, SHA-512/224 and SHA-512/256 as specified by FIPS 180-4.
 */
public final class Sha512 extends BlockDigest {
    private static final long[] K = {
        0x428A2F98D728AE22L, 0x7137449123EF65CDL, 0xB5C0FBCFEC4D3B2FL, 0xE9B5DBA58189DBBCL, 0x3956C25BF348B538L,
        0x59F111F1B605D019L, 0x923F82A4AF194F9BL, 0xAB1C5ED5DA6D8118L, 0xD807AA98A3030242L, 0x12835B0145706FBEL,
        0x243185BE4EE4B28CL, 0x550C7DC3D5FFB4E2L, 0x72BE5D74F27B896FL, 0x80DEB1FE3B1696B1L, 0x9BDC06A725C71235L,
        0xC19BF174CF692694L, 0xE49B69C19EF14AD2L, 0xEFBE4786384F25E3L, 0x0FC19DC68B8CD5B5L, 0x240CA1CC77AC9C65L,
        0x2DE92C6F592B0275L, 0x4A7484AA6EA6E483L, 0x5CB0A9DCBD41FBD4L, 0x76F988DA831153B5L, 0x983E5152EE66DFABL,
        0xA831C66D2DB43210L, 0xB00327C898FB213FL, 0xBF597FC7BEEF0EE4L, 0xC6E00BF33DA88FC2L, 0xD5A79147930AA725L,
        0x06CA6351E003826FL, 0x142929670A0E6E70L, 0x27B70A8546D22FFCL, 0x2E1B21385C26C926L, 0x4D2C6DFC5AC42AEDL,
        0x53380D139D95B3DFL, 0x650A73548BAF63DEL, 0x766A0ABB3C77B2A8L, 0x81C2C92E47EDAEE6L, 0x92722C851482353BL,
        0xA2BFE8A14CF10364L, 0xA81A664BBC423001L, 0xC24B8B70D0F89791L, 0xC76C51A30654BE30L, 0xD192E819D6EF5218L,
        0xD69906245565A910L, 0xF40E35855771202AL, 0x106AA07032BBD1B8L, 0x19A4C116B8D2D0C8L, 0x1E376C085141AB53L,
        0x2748774CDF8EEB99L, 0x34B0BCB5E19B48A8L, 0x391C0CB3C5C95A63L, 0x4ED8AA4AE3418ACBL, 0x5B9CCA4F7763E373L,
        0x682E6FF3D6B2B8A3L, 0x748F82EE5DEFB2FCL, 0x78A5636F43172F60L, 0x84C87814A1F0AB72L, 0x8CC702081A6439ECL,
        0x90BEFFFA23631E28L, 0xA4506CEBDE82BDE9L, 0xBEF9A3F7B2C67915L, 0xC67178F2E372532BL, 0xCA273ECEEA26619CL,
        0xD186B8C721C0C207L, 0xEADA7DD6CDE0EB1EL, 0xF57D4F7FEE6ED178L, 0x06F067AA72176FBAL, 0x0A637DC5A2C898A6L,
        0x113F9804BEF90DAEL, 0x1B710B35131C471BL, 0x28DB77F523047D84L, 0x32CAAB7B40C72493L, 0x3C9EBE0A15C9BEBCL,
        0x431D67C49C100D4CL, 0x4CC5D4BECB3E42B6L, 0x597F299CFC657E2AL, 0x5FCB6FAB3AD6FAECL, 0x6C44198C4A475817L
    };
    private static final long[] IV512 = {
        0x6A09E667F3BCC908L, 0xBB67AE8584CAA73BL, 0x3C6EF372FE94F82BL, 0xA54FF53A5F1D36F1L, 0x510E527FADE682D1L,
        0x9B05688C2B3E6C1FL, 0x1F83D9ABFB41BD6BL, 0x5BE0CD19137E2179L
    };
    private static final long[] IV384 = {
        0xCBBB9D5DC1059ED8L, 0x629A292A367CD507L, 0x9159015A3070DD17L, 0x152FECD8F70E5939L, 0x67332667FFC00B31L,
        0x8EB44A8768581511L, 0xDB0C2E0D64F98FA7L, 0x47B5481DBEFA4FA4L
    };
    // FIPS 180-4 5.3.6: the SHA-512/t initial values.
    private static final long[] IV512_224 = {
        0x8C3D37C819544DA2L, 0x73E1996689DCD4D6L, 0x1DFAB7AE32FF9C82L, 0x679DD514582F9FCFL,
        0x0F6D2B697BD44DA8L, 0x77E36F7304C48942L, 0x3F9D85A86A1D36C8L, 0x1112E6AD91D692A1L
    };
    private static final long[] IV512_256 = {
        0x22312194FC2BF72CL, 0x9F555FA3C84C64C2L, 0x2393B86B6F53B151L, 0x963877195940EABDL,
        0x96283EE2A88EFFE3L, 0xBE5E1E2553863992L, 0x2B0199FC2C85B8AAL, 0x0EB72DDC81C52CA2L
    };

    private final int length;
    private final long[] initial;
    private final long[] state = new long[8];
    private final long[] words = new long[80];

    public Sha512(int length) {
        super(128, true);
        this.length = length;
        switch (length) {
            case 28:
                initial = IV512_224;
                break;
            case 32:
                initial = IV512_256;
                break;
            case 48:
                initial = IV384;
                break;
            default:
                initial = IV512;
                break;
        }
        resetState();
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    void resetState() {
        System.arraycopy(initial, 0, state, 0, 8);
    }

    @Override
    void transform() {
        for (int i = 0; i < 16; ++i) {
            long value = 0;
            for (int j = 0; j < 8; ++j) {
                value = (value << 8) | (block[i * 8 + j] & 0xFF);
            }
            words[i] = value;
        }
        for (int i = 16; i < 80; ++i) {
            long s0 = Long.rotateRight(words[i - 15], 1) ^ Long.rotateRight(words[i - 15], 8) ^ (words[i - 15] >>> 7);
            long s1 = Long.rotateRight(words[i - 2], 19) ^ Long.rotateRight(words[i - 2], 61) ^ (words[i - 2] >>> 6);
            words[i] = words[i - 16] + s0 + words[i - 7] + s1;
        }
        long a = state[0];
        long b = state[1];
        long c = state[2];
        long d = state[3];
        long e = state[4];
        long f = state[5];
        long g = state[6];
        long h = state[7];
        for (int i = 0; i < 80; ++i) {
            long s1 = Long.rotateRight(e, 14) ^ Long.rotateRight(e, 18) ^ Long.rotateRight(e, 41);
            long choice = (e & f) ^ (~e & g);
            long temp1 = h + s1 + choice + K[i] + words[i];
            long s0 = Long.rotateRight(a, 28) ^ Long.rotateRight(a, 34) ^ Long.rotateRight(a, 39);
            long majority = (a & b) ^ (a & c) ^ (b & c);
            long temp2 = s0 + majority;
            h = g;
            g = f;
            f = e;
            e = d + temp1;
            d = c;
            c = b;
            b = a;
            a = temp1 + temp2;
        }
        state[0] += a;
        state[1] += b;
        state[2] += c;
        state[3] += d;
        state[4] += e;
        state[5] += f;
        state[6] += g;
        state[7] += h;
    }

    @Override
    byte[] output() {
        byte[] result = new byte[length];
        for (int i = 0; i < length; ++i) {
            result[i] = (byte) (state[i / 8] >>> (56 - 8 * (i % 8)));
        }
        return result;
    }

    @Override
    public Sha512 copy() {
        Sha512 copy = new Sha512(length);
        copyBlockTo(copy);
        System.arraycopy(state, 0, copy.state, 0, state.length);
        return copy;
    }
}
