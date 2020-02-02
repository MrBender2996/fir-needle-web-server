/**
 * MIT License
 * <p>
 * Copyright (c) 2019 Nikita Vasilev
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE  LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package fir.needle.web.server.http.netty;


import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.io.InputStream;

public class NettyByteBufInputStream extends InputStream {
    private ByteBuf buffer;

    public NettyByteBufInputStream() {
        super();
    }

    public void setBuffer(final ByteBuf buffer) {
        this.buffer = buffer;
    }

    @Override
    public int read() throws IOException {
        return buffer.readableBytes() == 0 ? -1 : buffer.readByte();
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (b.length == 0) {
            return 0;
        }

        final int readBytes = len - off < buffer.readableBytes() ? len - off : buffer.readableBytes();
        try {
            buffer.readBytes(b, off, len);
        } catch (final IndexOutOfBoundsException ex) {
            return -1;
        }

        return readBytes;
    }

    @Override
    public long skip(final long n) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int available() throws IOException {
        return buffer.readableBytes();
    }

    @Override
    public void close() throws IOException {
        buffer = null;
    }

    @Override
    public synchronized void mark(final int readlimit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean markSupported() {
        throw new UnsupportedOperationException();
    }
}
