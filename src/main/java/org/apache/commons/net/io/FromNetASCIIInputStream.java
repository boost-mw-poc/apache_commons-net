/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.net.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.net.util.NetConstants;

/**
 * This class wraps an input stream, replacing all occurrences of &lt;CR&gt;&lt;LF&gt; (carriage return followed by a linefeed), which is the NETASCII standard
 * for representing a newline, with the local line separator representation. You would use this class to implement ASCII file transfers requiring conversion
 * from NETASCII.
 */
public final class FromNetASCIIInputStream extends PushbackInputStream {

    static final boolean NO_CONVERSION_REQUIRED = System.lineSeparator().equals("\r\n");
    static final byte[] LINE_SEPARATOR_BYTES = System.lineSeparator().getBytes(StandardCharsets.US_ASCII);

    /**
     * Returns true if the NetASCII line separator differs from the system line separator, false if they are the same. This method is useful to determine
     * whether or not you need to instantiate a FromNetASCIIInputStream object.
     *
     * @return True if the NETASCII line separator differs from the local system line separator, false if they are the same.
     */
    public static boolean isConversionRequired() {
        return !NO_CONVERSION_REQUIRED;
    }

    private int length;

    /**
     * Creates a FromNetASCIIInputStream instance that wraps an existing InputStream.
     *
     * @param input the stream to wrap
     */
    public FromNetASCIIInputStream(final InputStream input) {
        super(input, LINE_SEPARATOR_BYTES.length + 1);
    }

    // PushbackInputStream in JDK 1.1.3 returns the wrong thing
    // TODO - can we delete this override now?
    /**
     * Returns the number of bytes that can be read without blocking EXCEPT when newline conversions have to be made somewhere within the available block of
     * bytes. In other words, you really should not rely on the value returned by this method if you are trying to avoid blocking.
     */
    @Override
    public int available() throws IOException {
        if (in == null) {
            throw new IOException("Stream closed");
        }
        return buf.length - pos + in.available();
    }

    /**
     * Reads and returns the next byte in the stream. If the end of the message has been reached, returns -1. Note that a call to this method may result in
     * multiple reads from the underlying input stream in order to convert NETASCII line separators to the local line separator format. This is transparent to
     * the programmer and is only mentioned for completeness.
     *
     * @return The next character in the stream. Returns -1 if the end of the stream has been reached.
     * @throws IOException If an error occurs while reading the underlying stream.
     */
    @Override
    public int read() throws IOException {
        if (NO_CONVERSION_REQUIRED) {
            return super.read();
        }
        return readInt();
    }

    /**
     * Reads the next number of bytes from the stream into an array and returns the number of bytes read. Returns -1 if the end of the stream has been reached.
     *
     * @param buffer The byte array in which to store the data.
     * @return The number of bytes read. Returns -1 if the end of the message has been reached.
     * @throws IOException If an error occurs in reading the underlying stream.
     */
    @Override
    public int read(final byte buffer[]) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    /**
     * Reads the next number of bytes from the stream into an array and returns the number of bytes read. Returns -1 if the end of the message has been reached.
     * The characters are stored in the array starting from the given offset and up to the length specified.
     *
     * @param buffer The byte array in which to store the data.
     * @param offset The offset into the array at which to start storing data.
     * @param length The number of bytes to read.
     * @return The number of bytes read. Returns -1 if the end of the stream has been reached.
     * @throws IOException If an error occurs while reading the underlying stream.
     */
    @Override
    public int read(final byte buffer[], int offset, final int length) throws IOException {
        if (NO_CONVERSION_REQUIRED) {
            return super.read(buffer, offset, length);
        }
        if (length < 1) {
            return 0;
        }
        int ch;
        final int off;
        ch = available();
        this.length = Math.min(length, ch);
        // If nothing is available, block to read only one character
        if (this.length < 1) {
            this.length = 1;
        }
        if ((ch = readInt()) == -1) {
            return NetConstants.EOS;
        }
        off = offset;
        do {
            buffer[offset++] = (byte) ch;
        } while (--this.length > 0 && (ch = readInt()) != -1);
        return offset - off;
    }

    private int readInt() throws IOException {
        int ch;
        ch = super.read();
        if (ch == '\r') {
            ch = super.read();
            if (ch != '\n') {
                if (ch != -1) {
                    unread(ch);
                }
                return '\r';
            }
            unread(LINE_SEPARATOR_BYTES);
            ch = super.read();
            // This is a kluge for read(byte[], ...) to read the right amount
            --length;
        }
        return ch;
    }

}
