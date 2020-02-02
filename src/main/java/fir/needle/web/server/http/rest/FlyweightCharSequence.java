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
package fir.needle.web.server.http.rest;

class FlyweightCharSequence implements CharSequence {
    private CharSequence source;
    private int startIndexInSrc;
    private int length;

    FlyweightCharSequence() {

    }

    void set(final CharSequence source, final int startIndexInSrc, final int length) {
        this.source = source;
        this.startIndexInSrc = startIndexInSrc;
        this.length = length;
    }

    void clear() {
        source = null;
        startIndexInSrc = 0;
        length = 0;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public char charAt(final int index) {
        return source.charAt(startIndexInSrc + index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        if (end > length) {
            throw new IndexOutOfBoundsException();
        }

        return source.subSequence(startIndexInSrc + start, end);
    }

    @Override
    public String toString() {
        return source.subSequence(startIndexInSrc, startIndexInSrc + length).toString();
    }
}
