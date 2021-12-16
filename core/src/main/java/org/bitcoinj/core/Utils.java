/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.core;

import java.io.ByteArrayOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URL;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.common.primitives.UnsignedLongs;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.io.BaseEncoding;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.lang.Math.min;

/**
 * A collection of various utility methods that are helpful for working with the Bitcoin protocol.
 * To enable debug logging from the library, run with -Dbitcoinj.logging=true on your command line.
 */
public class Utils {

    /** The string that prefixes all text messages signed using Bitcoin keys. */
    public static final String BITCOIN_SIGNED_MESSAGE_HEADER = "DarkCoin Signed Message:\n";  //Dash use DarkCoin here
    public static final byte[] BITCOIN_SIGNED_MESSAGE_HEADER_BYTES = BITCOIN_SIGNED_MESSAGE_HEADER.getBytes(Charsets.UTF_8);

    /** Joiner for concatenating words with a space inbetween. */
    public static final Joiner SPACE_JOINER = Joiner.on(" ");
    /** Splitter for splitting words on whitespaces. */
    public static final Splitter WHITESPACE_SPLITTER = Splitter.on(Pattern.compile("\\s+"));
    /** Hex encoding used throughout the framework. Use with HEX.encode(byte[]) or HEX.decode(CharSequence). */
    public static final BaseEncoding HEX = BaseEncoding.base16().lowerCase();

    /**
     * Max initial size of variable length arrays and ArrayLists that could be attacked.
     * Avoids this attack: Attacker sends a msg indicating it will contain a huge number (eg 2 billion) elements (eg transaction inputs) and
     * forces bitcoinj to try to allocate a huge piece of the memory resulting in OutOfMemoryError.
    */
    public static final int MAX_INITIAL_ARRAY_LENGTH = 20;

    private static BlockingQueue<Boolean> mockSleepQueue;

    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    /**
     * <p>
     * The regular {@link BigInteger#toByteArray()} includes the sign bit of the number and
     * might result in an extra byte addition. This method removes this extra byte.
     * </p>
     * <p>
     * Assuming only positive numbers, it's possible to discriminate if an extra byte
     * is added by checking if the first element of the array is 0 (0000_0000).
     * Due to the minimal representation provided by BigInteger, it means that the bit sign
     * is the least significant bit 0000_000<b>0</b> .
     * Otherwise the representation is not minimal.
     * For example, if the sign bit is 0000_00<b>0</b>0, then the representation is not minimal due to the rightmost zero.
     * </p>
     * @param b the integer to format into a byte array
     * @param numBytes the desired size of the resulting byte array
     * @return numBytes byte long array.
     */
    public static byte[] bigIntegerToBytes(BigInteger b, int numBytes) {
        checkArgument(b.signum() >= 0, "b must be positive or zero");
        checkArgument(numBytes > 0, "numBytes must be positive");
        byte[] src = b.toByteArray();
        byte[] dest = new byte[numBytes];
        boolean isFirstByteOnlyForSign = src[0] == 0;
        int length = isFirstByteOnlyForSign ? src.length - 1 : src.length;
        checkArgument(length <= numBytes, "The given number does not fit in " + numBytes);
        int srcPos = isFirstByteOnlyForSign ? 1 : 0;
        int destPos = numBytes - length;
        System.arraycopy(src, srcPos, dest, destPos, length);
        return dest;
    }

    /** Write 2 bytes to the byte array (starting at the offset) as unsigned 16-bit integer in little endian format. */
    public static void uint16ToByteArrayLE(int val, byte[] out, int offset) {
        out[offset] = (byte) (0xFF & val);
        out[offset + 1] = (byte) (0xFF & (val >> 8));
    }

    /** Write 4 bytes to the byte array (starting at the offset) as unsigned 32-bit integer in little endian format. */
    public static void uint32ToByteArrayLE(long val, byte[] out, int offset) {
        out[offset] = (byte) (0xFF & val);
        out[offset + 1] = (byte) (0xFF & (val >> 8));
        out[offset + 2] = (byte) (0xFF & (val >> 16));
        out[offset + 3] = (byte) (0xFF & (val >> 24));
    }

    /** Write 4 bytes to the byte array (starting at the offset) as unsigned 32-bit integer in big endian format. */
    public static void uint32ToByteArrayBE(long val, byte[] out, int offset) {
        out[offset] = (byte) (0xFF & (val >> 24));
        out[offset + 1] = (byte) (0xFF & (val >> 16));
        out[offset + 2] = (byte) (0xFF & (val >> 8));
        out[offset + 3] = (byte) (0xFF & val);
    }

    /** Write 8 bytes to the byte array (starting at the offset) as signed 64-bit integer in little endian format. */
    public static void int64ToByteArrayLE(long val, byte[] out, int offset) {
        out[offset] = (byte) (0xFF & val);
        out[offset + 1] = (byte) (0xFF & (val >> 8));
        out[offset + 2] = (byte) (0xFF & (val >> 16));
        out[offset + 3] = (byte) (0xFF & (val >> 24));
        out[offset + 4] = (byte) (0xFF & (val >> 32));
        out[offset + 5] = (byte) (0xFF & (val >> 40));
        out[offset + 6] = (byte) (0xFF & (val >> 48));
        out[offset + 7] = (byte) (0xFF & (val >> 56));
    }

    /** Write 2 bytes to the output stream as unsigned 16-bit integer in little endian format. */
    public static void uint16ToByteStreamLE(int val, OutputStream stream) throws IOException {
        stream.write((int) (0xFF & val));
        stream.write((int) (0xFF & (val >> 8)));
    }

    /** Write 2 bytes to the output stream as unsigned 16-bit integer in big endian format. */
    public static void uint16ToByteStreamBE(int val, OutputStream stream) throws IOException {
        stream.write((int) (0xFF & (val >> 8)));
        stream.write((int) (0xFF & val));
    }

    /** Write 4 bytes to the output stream as unsigned 32-bit integer in little endian format. */
    public static void uint32ToByteStreamLE(long val, OutputStream stream) throws IOException {
        stream.write((int) (0xFF & val));
        stream.write((int) (0xFF & (val >> 8)));
        stream.write((int) (0xFF & (val >> 16)));
        stream.write((int) (0xFF & (val >> 24)));
    }

    /** Write 4 bytes to the output stream as unsigned 32-bit integer in big endian format. */
    public static void uint32ToByteStreamBE(long val, OutputStream stream) throws IOException {
        stream.write((int) (0xFF & (val >> 24)));
        stream.write((int) (0xFF & (val >> 16)));
        stream.write((int) (0xFF & (val >> 8)));
        stream.write((int) (0xFF & val));
    }

    /** Write 8 bytes to the output stream as signed 64-bit integer in little endian format. */
    public static void int64ToByteStreamLE(long val, OutputStream stream) throws IOException {
        stream.write((int) (0xFF & val));
        stream.write((int) (0xFF & (val >> 8)));
        stream.write((int) (0xFF & (val >> 16)));
        stream.write((int) (0xFF & (val >> 24)));
        stream.write((int) (0xFF & (val >> 32)));
        stream.write((int) (0xFF & (val >> 40)));
        stream.write((int) (0xFF & (val >> 48)));
        stream.write((int) (0xFF & (val >> 56)));
    }

    /** Write 8 bytes to the output stream as unsigned 64-bit integer in little endian format. */
    public static void uint64ToByteStreamLE(BigInteger val, OutputStream stream) throws IOException {
        byte[] bytes = val.toByteArray();
        if (bytes.length > 8) {
            throw new RuntimeException("Input too large to encode into a uint64");
        }
        bytes = reverseBytes(bytes);
        stream.write(bytes);
        if (bytes.length < 8) {
            for (int i = 0; i < 8 - bytes.length; i++)
                stream.write(0);
        }
    }

    /** Parse 2 bytes from the byte array (starting at the offset) as unsigned 16-bit integer in little endian format. */
    public static int readUint16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) |
                ((bytes[offset + 1] & 0xff) << 8);
    }

    public static void stringToByteStream(String string, OutputStream stream) throws IOException {
        stream.write(new VarInt(string.length()).encode());
        stream.write(string.getBytes());
    }

    public static void bytesToByteStream(byte [] string, OutputStream stream) throws IOException {
        stream.write(new VarInt(string.length).encode());
        stream.write(string);
    }

    /**
     * Work around lack of unsigned types in Java.
     */
    public static boolean isLessThanUnsigned(long n1, long n2) {
        return UnsignedLongs.compare(n1, n2) < 0;
    }

    /**
     * Work around lack of unsigned types in Java.
     */
    public static boolean isLessThanOrEqualToUnsigned(long n1, long n2) {
        return UnsignedLongs.compare(n1, n2) <= 0;
    }

    /** Parse 4 bytes from the byte array (starting at the offset) as unsigned 32-bit integer in little endian format. */
    public static long readUint32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffl) |
                ((bytes[offset + 1] & 0xffl) << 8) |
                ((bytes[offset + 2] & 0xffl) << 16) |
                ((bytes[offset + 3] & 0xffl) << 24);
    }

    /** Parse 8 bytes from the byte array (starting at the offset) as signed 64-bit integer in little endian format. */
    public static long readInt64(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffl) |
               ((bytes[offset + 1] & 0xffl) << 8) |
               ((bytes[offset + 2] & 0xffl) << 16) |
               ((bytes[offset + 3] & 0xffl) << 24) |
               ((bytes[offset + 4] & 0xffl) << 32) |
               ((bytes[offset + 5] & 0xffl) << 40) |
               ((bytes[offset + 6] & 0xffl) << 48) |
               ((bytes[offset + 7] & 0xffl) << 56);
    }

    /** Parse 4 bytes from the byte array (starting at the offset) as unsigned 32-bit integer in big endian format. */
    public static long readUint32BE(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xffl) << 24) |
                ((bytes[offset + 1] & 0xffl) << 16) |
                ((bytes[offset + 2] & 0xffl) << 8) |
                (bytes[offset + 3] & 0xffl);
    }

    /** Parse 2 bytes from the byte array (starting at the offset) as unsigned 16-bit integer in big endian format. */
    public static int readUint16BE(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) |
                (bytes[offset + 1] & 0xff);
    }

    /** Parse 2 bytes from the stream as unsigned 16-bit integer in little endian format. */
    public static int readUint16FromStream(InputStream is) {
        try {
            return (is.read() & 0xff) |
                    ((is.read() & 0xff) << 8);
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    /** Parse 4 bytes from the stream as unsigned 32-bit integer in little endian format. */
    public static long readUint32FromStream(InputStream is) {
        try {
            return (is.read() & 0xffl) |
                    ((is.read() & 0xffl) << 8) |
                    ((is.read() & 0xffl) << 16) |
                    ((is.read() & 0xffl) << 24);
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * Returns a copy of the given byte array in reverse order.
     */
    public static byte[] reverseBytes(byte[] bytes) {
        // We could use the XOR trick here but it's easier to understand if we don't. If we find this is really a
        // performance issue the matter can be revisited.
        byte[] buf = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++)
            buf[i] = bytes[bytes.length - 1 - i];
        return buf;
    }

    /**
     * Calculates RIPEMD160(SHA256(input)). This is used in Address calculations.
     */
    public static byte[] sha256hash160(byte[] input) {
        byte[] sha256 = Sha256Hash.hash(input);
        RIPEMD160Digest digest = new RIPEMD160Digest();
        digest.update(sha256, 0, sha256.length);
        byte[] out = new byte[20];
        digest.doFinal(out, 0);
        return out;
    }

    /**
     * MPI encoded numbers are produced by the OpenSSL BN_bn2mpi function. They consist of
     * a 4 byte big endian length field, followed by the stated number of bytes representing
     * the number in big endian format (with a sign bit).
     * @param hasLength can be set to false if the given array is missing the 4 byte length field
     */
    public static BigInteger decodeMPI(byte[] mpi, boolean hasLength) {
        byte[] buf;
        if (hasLength) {
            int length = (int) readUint32BE(mpi, 0);
            buf = new byte[length];
            System.arraycopy(mpi, 4, buf, 0, length);
        } else
            buf = mpi;
        if (buf.length == 0)
            return BigInteger.ZERO;
        boolean isNegative = (buf[0] & 0x80) == 0x80;
        if (isNegative)
            buf[0] &= 0x7f;
        BigInteger result = new BigInteger(buf);
        return isNegative ? result.negate() : result;
    }
    
    /**
     * MPI encoded numbers are produced by the OpenSSL BN_bn2mpi function. They consist of
     * a 4 byte big endian length field, followed by the stated number of bytes representing
     * the number in big endian format (with a sign bit).
     * @param includeLength indicates whether the 4 byte length field should be included
     */
    public static byte[] encodeMPI(BigInteger value, boolean includeLength) {
        if (value.equals(BigInteger.ZERO)) {
            if (!includeLength)
                return new byte[] {};
            else
                return new byte[] {0x00, 0x00, 0x00, 0x00};
        }
        boolean isNegative = value.signum() < 0;
        if (isNegative)
            value = value.negate();
        byte[] array = value.toByteArray();
        int length = array.length;
        if ((array[0] & 0x80) == 0x80)
            length++;
        if (includeLength) {
            byte[] result = new byte[length + 4];
            System.arraycopy(array, 0, result, length - array.length + 3, array.length);
            uint32ToByteArrayBE(length, result, 0);
            if (isNegative)
                result[4] |= 0x80;
            return result;
        } else {
            byte[] result;
            if (length != array.length) {
                result = new byte[length];
                System.arraycopy(array, 0, result, 1, array.length);
            }else
                result = array;
            if (isNegative)
                result[0] |= 0x80;
            return result;
        }
    }

    /**
     * <p>The "compact" format is a representation of a whole number N using an unsigned 32 bit number similar to a
     * floating point format. The most significant 8 bits are the unsigned exponent of base 256. This exponent can
     * be thought of as "number of bytes of N". The lower 23 bits are the mantissa. Bit number 24 (0x800000) represents
     * the sign of N. Therefore, N = (-1^sign) * mantissa * 256^(exponent-3).</p>
     *
     * <p>Satoshi's original implementation used BN_bn2mpi() and BN_mpi2bn(). MPI uses the most significant bit of the
     * first byte as sign. Thus 0x1234560000 is compact 0x05123456 and 0xc0de000000 is compact 0x0600c0de. Compact
     * 0x05c0de00 would be -0x40de000000.</p>
     *
     * <p>Bitcoin only uses this "compact" format for encoding difficulty targets, which are unsigned 256bit quantities.
     * Thus, all the complexities of the sign bit and using base 256 are probably an implementation accident.</p>
     */
    public static BigInteger decodeCompactBits(long compact) {
        int size = ((int) (compact >> 24)) & 0xFF;
        byte[] bytes = new byte[4 + size];
        bytes[3] = (byte) size;
        if (size >= 1) bytes[4] = (byte) ((compact >> 16) & 0xFF);
        if (size >= 2) bytes[5] = (byte) ((compact >> 8) & 0xFF);
        if (size >= 3) bytes[6] = (byte) (compact & 0xFF);
        return decodeMPI(bytes, true);
    }

    /**
     * @see Utils#decodeCompactBits(long)
     */
    public static long encodeCompactBits(BigInteger value) {
        long result;
        int size = value.toByteArray().length;
        if (size <= 3)
            result = value.longValue() << 8 * (3 - size);
        else
            result = value.shiftRight(8 * (size - 3)).longValue();
        // The 0x00800000 bit denotes the sign.
        // Thus, if it is already set, divide the mantissa by 256 and increase the exponent.
        if ((result & 0x00800000L) != 0) {
            result >>= 8;
            size++;
        }
        result |= size << 24;
        result |= value.signum() == -1 ? 0x00800000 : 0;
        return result;
    }

    /**
     * @see Utils#decodeCompactBits(long)
     */
    public static long encodeCompactBits(BigInteger value, boolean negative) {
        long result;
        int size = value.toByteArray().length;
        if (size <= 3)
            result = value.longValue() << 8 * (3 - size);
        else
            result = value.shiftRight(8 * (size - 3)).longValue();
        // The 0x00800000 bit denotes the sign.
        // Thus, if it is already set, divide the mantissa by 256 and increase the exponent.
        if ((result & 0x00800000L) != 0) {
            result >>= 8;
            size++;
        }
        result |= size << 24;
        //result |= value.signum() == -1 ? 0x00800000 : 0;
        result |= (negative && (result & 0x007fffff) !=0 ? 0x00800000 : 0);
        return result;
    }
    /**
     * If non-null, overrides the return value of now().
     */
    private static volatile Date mockTime;

    /**
     * Advances (or rewinds) the mock clock by the given number of seconds.
     */
    public static Date rollMockClock(int seconds) {
        return rollMockClockMillis(seconds * 1000L);
    }

    /**
     * Advances (or rewinds) the mock clock by the given number of milliseconds.
     */
    public static Date rollMockClockMillis(long millis) {
        if (mockTime == null)
            throw new IllegalStateException("You need to use setMockClock() first.");
        mockTime = new Date(mockTime.getTime() + millis);
        return mockTime;
    }

    /**
     * Sets the mock clock to the current time.
     */
    public static void setMockClock() {
        mockTime = new Date();
    }

    /**
     * Sets the mock clock to the given time (in seconds).
     */
    public static void setMockClock(long mockClockSeconds) {
        mockTime = new Date(mockClockSeconds * 1000);
    }

    /**
     * Clears the mock clock and sleep
     */
    public static void resetMocking() {
        mockTime = null;
        mockSleepQueue = null;
    }

    /**
     * Returns the current time, or a mocked out equivalent.
     */
    public static Date now() {
        return mockTime != null ? mockTime : new Date();
    }

    // TODO: Replace usages of this where the result is / 1000 with currentTimeSeconds.
    /** Returns the current time in milliseconds since the epoch, or a mocked out equivalent. */
    public static long currentTimeMillis() {
        return mockTime != null ? mockTime.getTime() : System.currentTimeMillis();
    }

    public static long currentTimeSeconds() {
        return currentTimeMillis() / 1000;
    }

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /**
     * Formats a given date+time value to an ISO 8601 string.
     * @param dateTime value to format, as a Date
     */
    public static String dateTimeFormat(Date dateTime) {
        DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso8601.setTimeZone(UTC);
        return iso8601.format(dateTime);
    }

    /**
     * Formats a given date+time value to an ISO 8601 string.
     * @param dateTime value to format, unix time (ms)
     */
    public static String dateTimeFormat(long dateTime) {
        DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso8601.setTimeZone(UTC);
        return iso8601.format(dateTime);
    }

    public static byte[] copyOf(byte[] in, int length) {
        byte[] out = new byte[length];
        System.arraycopy(in, 0, out, 0, min(length, in.length));
        return out;
    }

    /**
     * <p>Given a textual message, returns a byte buffer formatted as follows:</p>
     *
     * <p>[24] "DarkCoin Signed Message:\n" [message.length as a varint] message</p>
     */
    public static byte[] formatMessageForSigning(String message) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(BITCOIN_SIGNED_MESSAGE_HEADER_BYTES.length);
            bos.write(BITCOIN_SIGNED_MESSAGE_HEADER_BYTES);
            byte[] messageBytes = message.getBytes(Charsets.UTF_8);
            VarInt size = new VarInt(messageBytes.length);
            bos.write(size.encode());
            bos.write(messageBytes);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
    }

    public static byte[] formatMessageForSigning(byte[] message) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(BITCOIN_SIGNED_MESSAGE_HEADER_BYTES.length);
            bos.write(BITCOIN_SIGNED_MESSAGE_HEADER_BYTES);
            byte[] messageBytes = message;
            VarInt size = new VarInt(messageBytes.length);
            bos.write(size.encode());
            bos.write(messageBytes);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
    }

    // 00000001, 00000010, 00000100, 00001000, ...
    private static final int[] bitMask = {0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80};
    
    /** Checks if the given bit is set in data, using little endian (not the same as Java native big endian) */
    public static boolean checkBitLE(byte[] data, int index) {
        return (data[index >>> 3] & bitMask[7 & index]) != 0;
    }
    
    /** Sets the given bit in data to one, using little endian (not the same as Java native big endian) */
    public static void setBitLE(byte[] data, int index) {
        data[index >>> 3] |= bitMask[7 & index];
    }

    /** Sleep for a span of time, or mock sleep if enabled */
    public static void sleep(long millis) {
        if (mockSleepQueue == null) {
            sleepUninterruptibly(millis, TimeUnit.MILLISECONDS);
        } else {
            try {
                boolean isMultiPass = mockSleepQueue.take();
                rollMockClockMillis(millis);
                if (isMultiPass)
                    mockSleepQueue.offer(true);
            } catch (InterruptedException e) {
                // Ignored.
            }
        }
    }

    /** Enable or disable mock sleep.  If enabled, set mock time to current time. */
    public static void setMockSleep(boolean isEnable) {
        if (isEnable) {
            mockSleepQueue = new ArrayBlockingQueue<>(1);
            mockTime = new Date(System.currentTimeMillis());
        } else {
            mockSleepQueue = null;
        }
    }

    /** Let sleeping thread pass the synchronization point.  */
    public static void passMockSleep() {
        mockSleepQueue.offer(false);
    }

    /** Let the sleeping thread pass the synchronization point any number of times. */
    public static void finishMockSleep() {
        if (mockSleepQueue != null) {
            mockSleepQueue.offer(true);
        }
    }

    private enum Runtime {
        ANDROID, OPENJDK, ORACLE_JAVA
    }

    private enum OS {
        LINUX, WINDOWS, MAC_OS
    }

    private static Runtime runtime = null;
    private static OS os = null;
    static {
        String runtimeProp = System.getProperty("java.runtime.name", "").toLowerCase(Locale.US);
        if (runtimeProp.equals(""))
            runtime = null;
        else if (runtimeProp.contains("android"))
            runtime = Runtime.ANDROID;
        else if (runtimeProp.contains("openjdk"))
            runtime = Runtime.OPENJDK;
        else if (runtimeProp.contains("java(tm) se"))
            runtime = Runtime.ORACLE_JAVA;
        else
            log.info("Unknown java.runtime.name '{}'", runtimeProp);

        String osProp = System.getProperty("os.name", "").toLowerCase(Locale.US);
        if (osProp.equals(""))
            os = null;
        else if (osProp.contains("linux"))
            os = OS.LINUX;
        else if (osProp.contains("win"))
            os = OS.WINDOWS;
        else if (osProp.contains("mac"))
            os = OS.MAC_OS;
        else
            log.info("Unknown os.name '{}'", runtimeProp);
    }

    public static boolean isAndroidRuntime() {
        return runtime == Runtime.ANDROID;
    }

    public static boolean isOpenJDKRuntime() {
        return runtime == Runtime.OPENJDK;
    }

    public static boolean isOracleJavaRuntime() {
        return runtime == Runtime.ORACLE_JAVA;
    }

    public static boolean isLinux() {
        return os == OS.LINUX;
    }

    public static boolean isWindows() {
        return os == OS.WINDOWS;
    }

    public static boolean isMac() {
        return os == OS.MAC_OS;
    }

    /**
     * Reads and joins together with LF char (\n) all the lines from given file. It's assumed that file is in UTF-8.
     */
    public static String getResourceAsString(URL url) throws IOException {
        List<String> lines = Resources.readLines(url, Charsets.UTF_8);
        return Joiner.on('\n').join(lines);
    }
    static final String CHARS_ALPHA_NUM = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    static final String SAFE_CHARS[] =
            {
                    CHARS_ALPHA_NUM + " .,;-_/:?@()", // SAFE_CHARS_DEFAULT
                    CHARS_ALPHA_NUM + " .,;-_?@" // SAFE_CHARS_UA_COMMENT
            };

    static public String sanitizeString(String str, int rule)
    {
        StringBuilder strResult = new StringBuilder();
        for (int i = 0; i < str.length(); i++)
        {
            if (SAFE_CHARS[rule].indexOf(str.charAt(i)) != -1)
            strResult.append(str.charAt(i));
        }
        return strResult.toString();
    }
    static public String sanitizeString(String str) { return sanitizeString(str, 0); }

    public static long getTotalCoinEstimate(int nHeight)
    {
        long nTotalCoins = 0;

        // TODO: This could be vastly improved, look at GetBlockValue for a better method

    /* these values are taken from the block explorer */
        if(nHeight > 5076) nTotalCoins += 2021642;
        if(nHeight > 17000) nTotalCoins += 3267692-2021642;
        if(nHeight > 34000) nTotalCoins += 3688775-3267692;
        if(nHeight > 68000) nTotalCoins += 4277615-3688775;

        if(nHeight > 68000*2) {
            nTotalCoins += 4649913.99999995-4277615;
        } else {
            return nTotalCoins;
        }

        //5.383754730451325 per block average after this
        nTotalCoins += ((nHeight-68000*2)*((5382104.64334133-4649913.99999995)/(68000*2)));

        // TODO: this should include the 7.1% decline too
        return nTotalCoins;
    }
    static double convertBitsToDouble(long nBits){
        long nShift = (nBits >> 24) & 0xff;

        double dDiff =
                (double)0x0000ffff / (double)(nBits & 0x00ffffff);

        while (nShift < 29)
        {
            dDiff *= 256.0;
            nShift++;
        }
        while (nShift > 29)
        {
            dDiff /= 256.0;
            nShift--;
        }

        return dDiff;
    }

    public static ArrayList<String> split(String words, String regex) {
        return new ArrayList<String>(Arrays.asList(words.split(regex)));
    }

    public static int findFirstNotOf(String str, String match, int start) {
        final int len = str.length();
        for (int i = start; i < len; i++) {
            char ch = str.charAt(i);
            if (match.indexOf(ch) == -1) {
                return i;
            }
        }

        return -1;
    }

    public static org.bitcoinj.utils.Pair<Integer, String> splitHostPort(String in) {

        int port = 0;
        String host = "";
        int colon = in.lastIndexOf(':');
        // if a : is found, and it either follows a [...], or no other : is in the string, treat it as port separator
        boolean fHaveColon = colon != -1;
        boolean fBracketed = fHaveColon && (in.charAt(0) == '[' && in.charAt(colon - 1) == ']'); // if there is a colon, and in[0]=='[', colon is not 0, so in[colon-1] is safe
        boolean fMultiColon = fHaveColon && (in.lastIndexOf(':',colon - 1) != -1);
        if (fHaveColon && (colon == 0 || fBracketed || !fMultiColon)) {
            int n = Integer.parseInt(in.substring(colon + 1));
            if (n > 0 && n < 0x10000) {
                in = in.substring(0, colon);
                port = n;
            }
        }
        if (in.length() > 0 && in.charAt(0) == '[' && in.charAt(in.length() - 1) == ']') {
            host = in.substring(1, 1 + in.length() - 2);
        } else {
            host = in;
        }
        return new org.bitcoinj.utils.Pair<Integer, String>(port, host);
    }

    public static void booleanArrayListToStream(ArrayList<Boolean> vec, OutputStream stream) throws IOException
    {
        int size = vec.size();
        byte [] vBytes = new byte [((size + 7) / 8)];
        int ms = min(size, vec.size());
        for (int p = 0; p < ms; p++)
            vBytes[p / 8] |= (vec.get(p) ? 1 : 0) << (p % 8);
        stream.write(new VarInt(vec.size()).encode());
        stream.write(vBytes);
    }

    public static void intArrayListToStream(ArrayList<Integer> vec, OutputStream stream) throws IOException
    {
        int size = vec.size();
        stream.write(new VarInt(vec.size()).encode());
        for (int i = 0; i < size; ++i) {
            Utils.uint32ToByteStreamLE(vec.get(i), stream);
        }
    }

    public static String toString(List<byte[]> stack) {
        List<String> parts = new ArrayList<>(stack.size());
        for (byte[] push : stack)
            parts.add('[' + HEX.encode(push) + ']');
        return SPACE_JOINER.join(parts);
    }
}
