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
package org.teavm.classlib.java.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * java.util.Base64 (RFC 4648 basic and URL-safe alphabets, RFC 2045 MIME).
 */
public final class TBase64 {
    private static final char[] BASIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final char[] URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();
    private static final byte[] CRLF = { '\r', '\n' };
    private static final int MIME_LINE_MAX = 76;

    private TBase64() {
    }

    public static Encoder getEncoder() {
        return new Encoder(false, null, -1, true);
    }

    public static Encoder getUrlEncoder() {
        return new Encoder(true, null, -1, true);
    }

    public static Encoder getMimeEncoder() {
        return new Encoder(false, CRLF, MIME_LINE_MAX, true);
    }

    public static Encoder getMimeEncoder(int lineLength, byte[] lineSeparator) {
        int[] reverse = reverse(BASIC);
        for (byte b : lineSeparator) {
            if (reverse[b & 0xFF] != -1 || b == '=') {
                throw new IllegalArgumentException("Illegal base64 line separator character 0x"
                        + Integer.toString(b, 16));
            }
        }
        lineLength &= ~3;
        if (lineLength <= 0) {
            return getEncoder();
        }
        return new Encoder(false, lineSeparator.clone(), lineLength, true);
    }

    public static Decoder getDecoder() {
        return new Decoder(false, false);
    }

    public static Decoder getUrlDecoder() {
        return new Decoder(true, false);
    }

    public static Decoder getMimeDecoder() {
        return new Decoder(false, true);
    }

    private static int[] reverse(char[] alphabet) {
        int[] result = new int[256];
        Arrays.fill(result, -1);
        for (int i = 0; i < alphabet.length; ++i) {
            result[alphabet[i]] = i;
        }
        return result;
    }

    public static class Encoder {
        private final char[] alphabet;
        private final byte[] newline;
        private final int lineMax;
        private final boolean doPadding;
        private final boolean urlSafe;

        Encoder(boolean urlSafe, byte[] newline, int lineMax, boolean doPadding) {
            this.urlSafe = urlSafe;
            this.alphabet = urlSafe ? URL : BASIC;
            this.newline = newline;
            this.lineMax = lineMax;
            this.doPadding = doPadding;
        }

        private int encodedLength(int srcLength) {
            int length;
            if (doPadding) {
                length = 4 * ((srcLength + 2) / 3);
            } else {
                int remainder = srcLength % 3;
                length = 4 * (srcLength / 3) + (remainder == 0 ? 0 : remainder + 1);
            }
            if (lineMax > 0 && length > 0) {
                length += (length - 1) / lineMax * newline.length;
            }
            return length;
        }

        public byte[] encode(byte[] src) {
            byte[] dst = new byte[encodedLength(src.length)];
            int written = encodeInto(src, 0, src.length, dst);
            return written == dst.length ? dst : Arrays.copyOf(dst, written);
        }

        public int encode(byte[] src, byte[] dst) {
            if (dst.length < encodedLength(src.length)) {
                throw new IllegalArgumentException("Output byte array is too small for encoding all input bytes");
            }
            return encodeInto(src, 0, src.length, dst);
        }

        public String encodeToString(byte[] src) {
            return new String(encode(src), StandardCharsets.ISO_8859_1);
        }

        public ByteBuffer encode(ByteBuffer buffer) {
            byte[] src = new byte[buffer.remaining()];
            buffer.get(src);
            return ByteBuffer.wrap(encode(src));
        }

        public OutputStream wrap(OutputStream os) {
            if (os == null) {
                throw new NullPointerException();
            }
            return new EncoderStream(os, this);
        }

        public Encoder withoutPadding() {
            if (!doPadding) {
                return this;
            }
            return new Encoder(urlSafe, newline, lineMax, false);
        }

        private int encodeInto(byte[] src, int offset, int end, byte[] dst) {
            int dp = 0;
            int lineLength = 0;
            int sp = offset;
            while (sp + 3 <= end) {
                if (lineMax > 0 && lineLength == lineMax) {
                    System.arraycopy(newline, 0, dst, dp, newline.length);
                    dp += newline.length;
                    lineLength = 0;
                }
                int bits = (src[sp++] & 0xFF) << 16 | (src[sp++] & 0xFF) << 8 | (src[sp++] & 0xFF);
                dst[dp++] = (byte) alphabet[(bits >>> 18) & 0x3F];
                dst[dp++] = (byte) alphabet[(bits >>> 12) & 0x3F];
                dst[dp++] = (byte) alphabet[(bits >>> 6) & 0x3F];
                dst[dp++] = (byte) alphabet[bits & 0x3F];
                lineLength += 4;
            }
            int remaining = end - sp;
            if (remaining > 0) {
                if (lineMax > 0 && lineLength == lineMax) {
                    System.arraycopy(newline, 0, dst, dp, newline.length);
                    dp += newline.length;
                }
                int b0 = src[sp++] & 0xFF;
                dst[dp++] = (byte) alphabet[b0 >> 2];
                if (remaining == 1) {
                    dst[dp++] = (byte) alphabet[(b0 << 4) & 0x3F];
                    if (doPadding) {
                        dst[dp++] = '=';
                        dst[dp++] = '=';
                    }
                } else {
                    int b1 = src[sp] & 0xFF;
                    dst[dp++] = (byte) alphabet[(b0 << 4) & 0x3F | (b1 >> 4)];
                    dst[dp++] = (byte) alphabet[(b1 << 2) & 0x3F];
                    if (doPadding) {
                        dst[dp++] = '=';
                    }
                }
            }
            return dp;
        }
    }

    private static final class EncoderStream extends FilterOutputStream {
        private final Encoder encoder;
        private final byte[] pending = new byte[3];
        private int pendingCount;
        private int lineLength;
        private boolean closed;

        EncoderStream(OutputStream out, Encoder encoder) {
            super(out);
            this.encoder = encoder;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[] { (byte) b }, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Stream is closed");
            }
            if (off < 0 || len < 0 || len > b.length - off) {
                throw new ArrayIndexOutOfBoundsException();
            }
            for (int i = 0; i < len; ++i) {
                pending[pendingCount++] = b[off + i];
                if (pendingCount == 3) {
                    writeUnit(pending, 3);
                    pendingCount = 0;
                }
            }
        }

        private void writeUnit(byte[] unit, int count) throws IOException {
            if (encoder.lineMax > 0 && lineLength == encoder.lineMax) {
                out.write(encoder.newline);
                lineLength = 0;
            }
            byte[] encoded = new byte[4];
            int written = new Encoder(encoder.urlSafe, null, -1, encoder.doPadding).encodeInto(unit, 0, count, encoded);
            out.write(encoded, 0, written);
            lineLength += written;

        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            if (pendingCount > 0) {
                writeUnit(pending, pendingCount);
            }
            out.close();
        }
    }

    public static class Decoder {
        private final int[] reverse;
        private final boolean mime;

        Decoder(boolean urlSafe, boolean mime) {
            this.reverse = reverse(urlSafe ? URL : BASIC);
            this.mime = mime;
        }

        public byte[] decode(byte[] src) {
            byte[] dst = new byte[src.length / 4 * 3 + 3];
            int written = decodeInto(src, 0, src.length, dst);
            return Arrays.copyOf(dst, written);
        }

        public byte[] decode(String src) {
            return decode(src.getBytes(StandardCharsets.ISO_8859_1));
        }

        public int decode(byte[] src, byte[] dst) {
            byte[] decoded = decode(src);
            if (dst.length < decoded.length) {
                throw new IllegalArgumentException("Output byte array is too small for decoding all input bytes");
            }
            System.arraycopy(decoded, 0, dst, 0, decoded.length);
            return decoded.length;
        }

        public ByteBuffer decode(ByteBuffer buffer) {
            int position = buffer.position();
            byte[] src = new byte[buffer.remaining()];
            buffer.get(src);
            try {
                return ByteBuffer.wrap(decode(src));
            } catch (IllegalArgumentException e) {
                buffer.position(position);
                throw e;
            }
        }

        public InputStream wrap(InputStream is) {
            if (is == null) {
                throw new NullPointerException();
            }
            return new DecoderStream(is, this);
        }

        private int decodeInto(byte[] src, int sp, int end, byte[] dst) {
            int dp = 0;
            int bits = 0;
            int shift = 18;
            while (sp < end) {
                int b = src[sp++] & 0xFF;
                int value = reverse[b];
                if (value < 0) {
                    if (b == '=') {
                        // Two symbols seen (shift 6) require "=="; padding with no symbols is malformed.
                        if (shift == 6 && (sp == end || src[sp++] != '=') || shift == 18) {
                            throw new IllegalArgumentException("Input byte array has wrong 4-byte ending unit");
                        }
                        break;
                    }
                    if (mime) {
                        continue;
                    }
                    throw new IllegalArgumentException("Illegal base64 character " + Integer.toString(b, 16));
                }
                bits |= value << shift;
                shift -= 6;
                if (shift < 0) {
                    dst[dp++] = (byte) (bits >> 16);
                    dst[dp++] = (byte) (bits >> 8);
                    dst[dp++] = (byte) bits;
                    shift = 18;
                    bits = 0;
                }
            }
            if (shift == 6) {
                dst[dp++] = (byte) (bits >> 16);
            } else if (shift == 0) {
                dst[dp++] = (byte) (bits >> 16);
                dst[dp++] = (byte) (bits >> 8);
            } else if (shift == 12) {
                throw new IllegalArgumentException("Last unit does not have enough valid bits");
            }
            while (sp < end) {
                if (mime && reverse[src[sp] & 0xFF] < 0) {
                    sp++;
                    continue;
                }
                throw new IllegalArgumentException("Input byte array has incorrect ending byte at " + sp);
            }
            return dp;
        }
    }
    private static final class DecoderStream extends InputStream {
        private final InputStream in;
        private final Decoder decoder;
        private byte[] decoded;
        private int position;

        DecoderStream(InputStream in, Decoder decoder) {
            this.in = in;
            this.decoder = decoder;
        }

        private void fill() throws IOException {
            if (decoded != null) {
                return;
            }
            java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[512];
            int read;
            while ((read = in.read(chunk)) > 0) {
                all.write(chunk, 0, read);
            }
            try {
                decoded = decoder.decode(all.toByteArray());
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage());
            }
        }

        @Override
        public int read() throws IOException {
            fill();
            return position < decoded.length ? decoded[position++] & 0xFF : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            fill();
            if (position >= decoded.length) {
                return len == 0 ? 0 : -1;
            }
            int count = Math.min(len, decoded.length - position);
            System.arraycopy(decoded, position, b, off, count);
            position += count;
            return count;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
