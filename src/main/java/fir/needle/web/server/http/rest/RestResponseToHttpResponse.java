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


import fir.needle.joint.io.CharAppendable;
import fir.needle.web.server.http.HttpOutputMessage;
import fir.needle.web.server.http.HttpResponse;

class RestResponseToHttpResponse implements RestResponse, RestSuccess, RestError, RestOutputMessage {
    private final HttpResponse response;
    private HttpOutputMessage httpOutputMessage;

    RestResponseToHttpResponse(final HttpResponse response) {
        this.response = response;
    }

    @Override
    public RestSuccess success() {
        return this;
    }

    @Override
    public RestError error() {
        return this;
    }

    @Override
    public RestOutputMessage badRequest() {
        httpOutputMessage = response.error().badRequest();
        return this;
    }

    @Override
    public RestOutputMessage unauthorized() {
        httpOutputMessage = response.error().unauthorized();
        return this;
    }

    @Override
    public RestOutputMessage forbidden() {
        httpOutputMessage = response.error().forbidden();
        return this;
    }

    @Override
    public RestOutputMessage notFound() {
        httpOutputMessage = response.error().notFound();
        return this;
    }

    @Override
    public RestOutputMessage ok() {
        httpOutputMessage = response.success().ok();
        return this;
    }

    @Override
    public RestOutputMessage created() {
        httpOutputMessage = response.success().created();
        return this;
    }

    @Override
    public RestOutputMessage noContent() {
        httpOutputMessage = response.success().noContent();
        return this;
    }

    @Override
    public RestOutputMessage custom(final int statusCode) {
        httpOutputMessage = response.error().custom(statusCode);
        return this;
    }

    @Override
    public RestOutputMessage internalServerError(final CharSequence message) {
        httpOutputMessage = response.error().internalServerError(message);
        return this;
    }

    @Override
    public RestOutputMessage internalServerError(final Throwable throwable) {
        httpOutputMessage = response.error().internalServerError(throwable);
        return this;
    }

    @Override
    public RestOutputMessage internalServerError(final CharSequence message, final Throwable throwable) {
        httpOutputMessage = response.error().internalServerError(message, throwable);
        return this;
    }

    @Override
    public RestOutputMessage custom(final int code, final CharSequence message) {
        httpOutputMessage = response.error().custom(code, message);
        return this;
    }

    @Override
    public RestOutputMessage custom(final int code, final Throwable throwable) {
        httpOutputMessage = response.error().custom(code, throwable);
        return this;
    }

    @Override
    public RestOutputMessage custom(final int code, final CharSequence message, final Throwable throwable) {
        httpOutputMessage = response.error().custom(code, message, throwable);
        return this;
    }

    @Override
    public CharAppendable body(final CharSequence contentType) {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    @Override
    public CharAppendable body(final CharSequence contentType, final int contentLength) {
        return new AppendableResponseBody(httpOutputMessage.body(contentType, contentLength));
    }

    @Override
    public void commit() {
        httpOutputMessage.commit();
    }
}