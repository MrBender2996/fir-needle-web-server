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

import fir.needle.joint.colleclions.Pool;
import fir.needle.joint.io.ByteAppendable;
import fir.needle.joint.io.ByteArea;
import fir.needle.joint.logging.Logger;
import fir.needle.web.server.http.HttpError;
import fir.needle.web.server.http.HttpOutputMessage;
import fir.needle.web.server.http.HttpRedirect;
import fir.needle.web.server.http.HttpRequestListener;
import fir.needle.web.server.http.HttpResponse;
import fir.needle.web.server.http.HttpSuccess;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.DiskAttribute;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class NettyHttpHandler extends SimpleChannelInboundHandler<HttpObject> {
    private static final HttpDataFactory FACTORY = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);

    static {
        DiskFileUpload.deleteOnExitTemporaryFile = true;
        DiskFileUpload.baseDirectory = null;
        DiskAttribute.deleteOnExitTemporaryFile = true;
        DiskAttribute.baseDirectory = null;
    }

    private HttpRequestListener listener;
    private final HttpResponse response;
    private final Pool<HttpRequestListener> pool;

    private boolean wasStarted;
    private boolean wasCommitted;
    private HttpPostRequestDecoder postDecoder;
    private ChannelHandlerContext context;
    private final Logger logger;

    NettyHttpHandler(final Pool<HttpRequestListener> pool, final Logger logger) {
        super();
        this.pool = pool;
        this.response = new NettyHttpResponse();
        this.logger = logger;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpObject msg) {
        if (wasCommitted) {
            return;
        }

        this.context = ctx;

        if (msg instanceof HttpRequest) {
            final HttpRequest request = (HttpRequest) msg;
            final QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());

            try {
                if (!wasStarted) {
                    wasStarted = true;
                    try {
                        listener.onRequestStarted(request.method().name(), queryStringDecoder.path(), response);
                    } catch (final Exception e) {
                        if (logger.isErrorEnabled()) {
                            logger.error(getStackTrace(e));
                        }
                    }
                } else {
                    throw new IllegalStateException();
                }
            } catch (final Exception e) {
                if (logger.isErrorEnabled()) {
                    logger.error(getStackTrace(e));
                }
            }

            if (wasCommitted) {
                return;
            }

            processParams(queryStringDecoder);
            processHeaders(request);

            if (!request.method().equals(HttpMethod.GET)) {
                createPostDecoder(request);
            }
        }

        if (msg instanceof HttpContent) {
            final HttpContent chunk = (HttpContent) msg;

            if (postDecoder != null) {
                processBody(chunk);
            }

            if (chunk instanceof LastHttpContent) {
                if (postDecoder != null && !postDecoder.isMultipart()) {
                    try {
                        listener.onBodyFinished();
                    } catch (final Exception e) {
                        if (logger.isErrorEnabled()) {
                            logger.error(getStackTrace(e));
                        }
                    }
                }

                if (wasStarted) {
                    wasStarted = false;
                    try {
                        listener.onRequestFinished();
                    } catch (final Exception e) {
                        if (logger.isErrorEnabled()) {
                            logger.error(getStackTrace(e));
                        }
                    }
                }
            }
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        System.out.println("Channel id: " + ctx.channel().id() + " " + ", socket: " + ctx.channel().remoteAddress());

        try {
            listener = pool.borrow();
        } catch (final Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error(getStackTrace(e));
            }
        }

        if (logger.isTraceEnabled()) {
            logger.trace("Channel is active " + listener + " by " + Thread.currentThread());
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        if (logger.isTraceEnabled()) {
            logger.trace("Channel is been deactivated " + listener + " by " + Thread.currentThread());
        }

        if (postDecoder != null) {
            postDecoder.cleanFiles();
        }

        if (wasStarted) {
            wasStarted = false;
            try {
                listener.onRequestFinished();
            } catch (final Exception e) {
                if (logger.isErrorEnabled()) {
                    logger.error(getStackTrace(e));
                }
            }
        }

        try {
            pool.release(listener);
            listener = null;
        } catch (final Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error(getStackTrace(e));
            }
        }
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        if (wasStarted) {
            try {
                listener.onError(cause);
            } catch (final Exception e) {
                if (logger.isErrorEnabled()) {
                    logger.error(getStackTrace(e));
                }
            }

            wasStarted = false;
            try {
                listener.onRequestFinished();
            } catch (final Exception e) {
                if (logger.isErrorEnabled()) {
                    logger.error(getStackTrace(e));
                }
            }
        }

        ctx.channel().close();
    }

    private void processParams(final QueryStringDecoder queryStringDecoder) {
        final Map<String, List<String>> params = queryStringDecoder.parameters();

        for (final Map.Entry<String, List<String>> crtParam : params.entrySet()) {
            for (final String crtValue : crtParam.getValue()) {
                try {
                    listener.onParameter(crtParam.getKey(), crtValue);
                } catch (final Exception e) {
                    if (logger.isErrorEnabled()) {
                        logger.error(getStackTrace(e));
                    }
                }

                if (wasCommitted) {
                    return;
                }
            }
        }
    }

    private void processHeaders(final HttpRequest request) {
        final List<Map.Entry<String, String>> headers = request.headers().entries();

        for (int i = 0; i < headers.size(); i++) {
            final Map.Entry<String, String> crtHeader = headers.get(i);
            try {
                listener.onHeader(crtHeader.getKey(), crtHeader.getValue());
            } catch (final Exception e) {
                if (logger.isErrorEnabled()) {
                    logger.error(getStackTrace(e));
                }
            }

            if (wasCommitted) {
                return;
            }
        }
    }

    private void processBody(final HttpContent chunk) {
        if (postDecoder.isMultipart()) {
            try {
                postDecoder.offer(chunk);
            } catch (final HttpPostRequestDecoder.ErrorDataDecoderException e) {
                try {
                    listener.onError(e);
                } catch (final Exception ee) {
                    if (logger.isErrorEnabled()) {
                        logger.error(getStackTrace(ee));
                    }
                }

                if (wasCommitted) {
                    return;
                }
            }

            try {
                while (postDecoder.hasNext()) {
                    final InterfaceHttpData data = postDecoder.next();

                    if (data != null) {
                        if (data.getHttpDataType().equals(InterfaceHttpData.HttpDataType.Attribute)) {
                            final Attribute attribute = (Attribute) data;

                            try {
                                listener.onParameter(attribute.getName(), attribute.getValue());
                            } catch (final Exception e) {
                                if (logger.isErrorEnabled()) {
                                    logger.error(getStackTrace(e));
                                }
                            }

                            if (wasCommitted) {
                                return;
                            }
                        }

                        if (data.getHttpDataType().equals(InterfaceHttpData.HttpDataType.FileUpload)) {
                            try {
                                listener.onPartStarted();
                            } catch (final Exception e) {
                                if (logger.isErrorEnabled()) {
                                    logger.error(getStackTrace(e));
                                }
                            }

                            final ByteBuf part = ((FileUpload) data).content();

                            try {
                                listener.onPartContent(new NettyInputByteBuffer(part), 0, part.readableBytes());
                            } catch (final Exception e) {
                                if (logger.isErrorEnabled()) {
                                    logger.error(getStackTrace(e));
                                }
                            }

                            try {
                                listener.onPartFinished();
                            } catch (final Exception e) {
                                if (logger.isErrorEnabled()) {
                                    logger.error(getStackTrace(e));
                                }
                            }

                            if (wasCommitted) {
                                return;
                            }
                        }
                    }
                }
            } catch (final HttpPostRequestDecoder.EndOfDataDecoderException e) {
                try {
                    listener.onError(e);
                } catch (final Exception ee) {
                    if (logger.isErrorEnabled()) {
                        logger.error(getStackTrace(e));
                    }
                }
            }
            return;
        }

        try {
            listener.onBodyStarted();
        } catch (final Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error(getStackTrace(e));
            }
        }

        try {
            listener.onBodyContent(new NettyInputByteBuffer(chunk.content()), 0, chunk.content().readableBytes());
        } catch (final Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error(getStackTrace(e));
            }
        }
    }

    private void createPostDecoder(final HttpRequest request) {
        try {
            postDecoder = new HttpPostRequestDecoder(FACTORY, request);
        } catch (final HttpPostRequestDecoder.ErrorDataDecoderException e) {
            if (logger.isErrorEnabled()) {
                logger.error(getStackTrace(e));
            }
        }
    }

    private String getStackTrace(final Throwable e) {
        return Stream.of(e.getStackTrace())
                .map(Object::toString)
                .collect(Collectors.joining("\n"));
    }

    private class NettyHttpResponse implements HttpResponse {

        private static final short BYTES_IN_CHAR = 2;
        private final String httpVersion = HttpVersion.HTTP_1_1.toString();
        private static final String EOL = "\n";
        private static final String COLON = ":";
        private static final String SPACE = " ";
        private static final String LOCATION_HEADER_NAME = "Location:";
        private static final String DEFAULT_CONTENT_TYPE = "text/html; charset=utf-8";
        private static final String CONTENT_TYPE_HEADER_NAME = "Content-Type";
        private static final String CONTENT_LENGTH_HEADER_NAME = "Content-Length";

        private ByteBuf buf;
        private final Charset charset = Charset.defaultCharset();

        private final NettyHttpSuccess nettyHttpSuccess = new NettyHttpSuccess();
        private final NettyHttpRedirect nettyHttpRedirect = new NettyHttpRedirect();
        private final NettyHttpError nettyHttpError = new NettyHttpError();
        private final NettyHttpOutputMessage nettyHttpOutputMessage = new NettyHttpOutputMessage();

        @Override
        public HttpSuccess success() {
            return nettyHttpSuccess;
        }

        @Override
        public HttpError error() {
            return nettyHttpError;
        }

        @Override
        public HttpRedirect redirect() {
            return nettyHttpRedirect;
        }

        private void writeIntoBuffer(final CharSequence message) {
            if (buf == null) {
                buf = context.alloc().buffer();
            }

            if (buf.writableBytes() < message.length() * BYTES_IN_CHAR) {
                flushBuffer();
            }

            buf.writeCharSequence(message, charset);
        }

        private void flushBuffer() {
            context.channel().write(buf);
            buf = context.alloc().buffer();
        }

        private final class NettyHttpSuccess implements HttpSuccess {

            @Override
            public HttpOutputMessage ok() {
                writeIntoBuffer(httpVersion + SPACE + HttpResponseStatus.OK + EOL);
                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage created() {
                writeIntoBuffer(httpVersion + SPACE + HttpResponseStatus.CREATED + EOL);
                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage noContent() {
                writeIntoBuffer(httpVersion + SPACE + HttpResponseStatus.NO_CONTENT + EOL);
                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage custom(final int code) {
                writeIntoBuffer(httpVersion + SPACE + HttpResponseStatus.valueOf(code) + EOL);
                return nettyHttpOutputMessage;
            }
        }

        private final class NettyHttpRedirect implements HttpRedirect {

            @Override
            public HttpOutputMessage movedPermanently(final CharSequence location) {
                writeIntoBuffer(
                        httpVersion + SPACE + HttpResponseStatus.MOVED_PERMANENTLY + EOL + LOCATION_HEADER_NAME +
                                COLON + SPACE + location.toString() + EOL);

                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage found(final CharSequence location) {
                writeIntoBuffer(
                        httpVersion + SPACE + HttpResponseStatus.FOUND + EOL + LOCATION_HEADER_NAME + COLON + SPACE +
                                location.toString() + EOL);

                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage seeOther(final CharSequence location) {
                writeIntoBuffer(
                        httpVersion + SPACE + HttpResponseStatus.SEE_OTHER + EOL + LOCATION_HEADER_NAME + COLON +
                                SPACE + location.toString() + EOL);

                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage custom(final int code, final CharSequence location) {
                writeIntoBuffer(
                        httpVersion + SPACE + HttpResponseStatus.valueOf(code) + EOL + LOCATION_HEADER_NAME + COLON +
                                SPACE + location.toString() + EOL);

                return nettyHttpOutputMessage;
            }
        }

        private final class NettyHttpError implements HttpError {
            private static final String MESSAGE_NAME = "Message:";
            private static final String EXCEPTION_NAME = "Exception:";

            @Override
            public HttpOutputMessage badRequest() {
                writeIntoBuffer(httpVersion + SPACE + HttpResponseStatus.BAD_REQUEST + EOL);
                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage unauthorized() {
                writeIntoBuffer(httpVersion + SPACE + HttpResponseStatus.UNAUTHORIZED + EOL);
                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage forbidden() {
                writeIntoBuffer(httpVersion + SPACE + HttpResponseStatus.FORBIDDEN + EOL);
                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage notFound() {
                writeIntoBuffer(httpVersion + SPACE + HttpResponseStatus.NOT_FOUND + EOL);
                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage internalServerError(final CharSequence message) {
                writeIntoBuffer(httpVersion + SPACE + HttpResponseStatus.INTERNAL_SERVER_ERROR + EOL);

                nettyHttpOutputMessage
                        .header(CONTENT_TYPE_HEADER_NAME, DEFAULT_CONTENT_TYPE)
                        .header(CONTENT_LENGTH_HEADER_NAME,
                                String.valueOf(message.length() + MESSAGE_NAME.length() + EOL.length()));

                writeIntoBuffer(EOL + MESSAGE_NAME + EOL + message);

                nettyHttpOutputMessage.isBodyEmpty = false;
                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage internalServerError(final Throwable throwable) {
                writeIntoBuffer(httpVersion + SPACE + HttpResponseStatus.INTERNAL_SERVER_ERROR + EOL);

                final String exception = getStackTrace(throwable);

                nettyHttpOutputMessage
                        .header(CONTENT_TYPE_HEADER_NAME, DEFAULT_CONTENT_TYPE)
                        .header(CONTENT_LENGTH_HEADER_NAME,
                                String.valueOf(exception.length() + EXCEPTION_NAME.length() + EOL.length()));

                writeIntoBuffer(EOL + EXCEPTION_NAME + EOL + exception);

                nettyHttpOutputMessage.isBodyEmpty = false;
                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage internalServerError(final CharSequence message, final Throwable throwable) {
                writeIntoBuffer(httpVersion + SPACE + HttpResponseStatus.INTERNAL_SERVER_ERROR + EOL);

                final String exception = getStackTrace(throwable);
                nettyHttpOutputMessage
                        .header(CONTENT_TYPE_HEADER_NAME, DEFAULT_CONTENT_TYPE)
                        .header(CONTENT_LENGTH_HEADER_NAME,
                                String.valueOf(exception.length() + MESSAGE_NAME.length() + message.length() +
                                        3 * EXCEPTION_NAME.length()));

                writeIntoBuffer(EOL + MESSAGE_NAME + EOL + message + EOL);
                writeIntoBuffer(EXCEPTION_NAME + EOL + exception);

                nettyHttpOutputMessage.isBodyEmpty = false;
                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage custom(final int code) {
                writeIntoBuffer(httpVersion + SPACE + HttpResponseStatus.valueOf(code) + EOL);
                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage custom(final int code, final CharSequence message) {
                writeIntoBuffer(httpVersion + SPACE + HttpResponseStatus.valueOf(code) + EOL);

                nettyHttpOutputMessage
                        .header(CONTENT_TYPE_HEADER_NAME, DEFAULT_CONTENT_TYPE)
                        .header(CONTENT_LENGTH_HEADER_NAME,
                                String.valueOf(message.length() + MESSAGE_NAME.length() + EOL.length()));

                writeIntoBuffer(EOL + MESSAGE_NAME + EOL + message);

                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage custom(final int code, final Throwable throwable) {
                writeIntoBuffer(httpVersion + SPACE + HttpResponseStatus.valueOf(code) + EOL);

                final String exception = getStackTrace(throwable);
                nettyHttpOutputMessage
                        .header(CONTENT_TYPE_HEADER_NAME, DEFAULT_CONTENT_TYPE)
                        .header(CONTENT_LENGTH_HEADER_NAME,
                                String.valueOf(exception.length() + EXCEPTION_NAME.length() + EOL.length()));

                writeIntoBuffer(EOL + EXCEPTION_NAME + EOL + exception);

                nettyHttpOutputMessage.isBodyEmpty = false;
                return nettyHttpOutputMessage;
            }

            @Override
            public HttpOutputMessage custom(final int code, final CharSequence message, final Throwable throwable) {
                writeIntoBuffer(httpVersion + SPACE + HttpResponseStatus.valueOf(code) + EOL);

                final String exception = getStackTrace(throwable);
                nettyHttpOutputMessage
                        .header(CONTENT_TYPE_HEADER_NAME, DEFAULT_CONTENT_TYPE)
                        .header(CONTENT_LENGTH_HEADER_NAME,
                                String.valueOf(
                                        exception.length() + MESSAGE_NAME.length() + message.length() +
                                                EXCEPTION_NAME.length() + 3 * EOL.length()));

                writeIntoBuffer(EOL + MESSAGE_NAME + EOL + message + EOL);
                writeIntoBuffer(EXCEPTION_NAME + EOL + exception);

                return nettyHttpOutputMessage;
            }
        }

        private final class NettyHttpOutputMessage implements HttpOutputMessage {
            private boolean isBodyEmpty = true;

            @Override
            public HttpOutputMessage header(final CharSequence name, final CharSequence value) {
                writeIntoBuffer(name + ":" + SPACE + value + EOL);
                return this;
            }

            @Override
            public ByteAppendable body(final CharSequence contentType) {
                throw new UnsupportedOperationException("Multipart is not implemented yet!!!");
            }

            @Override
            public ByteAppendable body(final CharSequence contentType, final int contentLength) {
                header(CONTENT_TYPE_HEADER_NAME, contentType);
                header(CONTENT_LENGTH_HEADER_NAME, Integer.toString(contentLength));

                writeIntoBuffer(EOL);

                return new NettyOutputBuffer();
            }

            @Override
            public void commit() {
                final Channel channel = context.channel();

                if (isBodyEmpty) {
                    writeIntoBuffer(EOL);
                }

                channel.write(buf);
                channel.flush();

                context.channel().close();
                buf = null;
                isBodyEmpty = true;

                wasCommitted = true;

                listener.onCommitted();
            }
        }

        private final class NettyOutputBuffer implements ByteAppendable {

            @Override
            public void appendByte(final byte toAppend) {
                if (!buf.isWritable()) {
                    flushBuffer();
                }

                buf.writeByte(toAppend);
            }

            @Override
            public void appendArea(final ByteArea area, final long startIndex, final long length) {
                if (area instanceof NettyInputByteBuffer) {
                    final NettyInputByteBuffer nettyArea = (NettyInputByteBuffer) area;
                    buf.writeBytes(nettyArea.buffer());
                    return;
                }

                for (long i = startIndex; i < length; i++) {
                    appendByte(area.getByte(i));
                }
            }
        }
    }
}