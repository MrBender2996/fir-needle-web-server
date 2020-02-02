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

import fir.needle.joint.colleclions.ConcurrentObjectPool;
import fir.needle.joint.colleclions.Pool;
import fir.needle.joint.logging.Logger;
import fir.needle.joint.logging.SystemLogger;
import fir.needle.web.server.http.HttpRequestListener;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.util.function.Supplier;


public class NettyHttpServer implements AutoCloseable {
    private final Pool<HttpRequestListener> listenerPool;
    private final int port;
    private final int maxInitialLineLength;
    private final int maxHeaderSize;
    private final int maxChunkSize;
    private final boolean enableHeaderValidation;
    private final int workerThreadsAmount;
    private final SslContext sslContext;
    private final Logger logger;

    NettyHttpServer(final NettyHttpServerBuilder builder) {
        this.port = builder.port;
        this.listenerPool = builder.listenerPool;
        this.maxInitialLineLength = builder.maxInitialLineLength;
        this.maxHeaderSize = builder.maxHeaderSize;
        this.maxChunkSize = builder.maxChunkSize;
        this.enableHeaderValidation = builder.enableHeaderValidation;
        this.workerThreadsAmount = builder.workerThreadsAmount;
        this.sslContext = builder.sslContext;
        this.logger = builder.logger;
    }

    public static NettyHttpServerBuilder builder() {
        return new NettyHttpServerBuilder();
    }

    public void run() throws Exception {
        final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        final EventLoopGroup workerGroup = new NioEventLoopGroup(workerThreadsAmount);

        try {
            final ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(final SocketChannel ch) {
                            if (sslContext != null) {
                                ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
                            }

                            ch.pipeline()
                                    .addLast(new HttpRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize,
                                            enableHeaderValidation));

                            ch.pipeline().addLast(new NettyHttpHandler(listenerPool, logger));
                        }
                    })
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            final ChannelFuture f = b.bind(port).sync();

            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    @Override
    public void close() throws Exception {

    }

    public static class NettyHttpServerBuilder {
        private int port;
        private Pool<HttpRequestListener> listenerPool;

        private int maxInitialLineLength = 4096;
        private int maxHeaderSize = 8192;
        private int maxChunkSize = 8192;
        private boolean enableHeaderValidation;
        private int workerThreadsAmount = 0;
        private SslContext sslContext;
        private Logger logger;

        public NettyHttpServerBuilder withMaxInitialLineLength(final int maxInitialLineLength) {
            this.maxInitialLineLength = maxInitialLineLength;
            return this;
        }

        public NettyHttpServerBuilder withMaxHeaderSize(final int maxHeaderSize) {
            this.maxHeaderSize = maxHeaderSize;
            return this;
        }

        public NettyHttpServerBuilder withMaxChunkSize(final int maxChunkSize) {
            this.maxChunkSize = maxChunkSize;
            return this;
        }

        public NettyHttpServerBuilder withHeadersValidation() {
            this.enableHeaderValidation = true;
            return this;
        }

        public NettyHttpServerBuilder withWorkerThreadsAmount(final int workerThreadsAmount) {
            this.workerThreadsAmount = workerThreadsAmount;
            return this;
        }

        public NettyHttpServerBuilder withSSL(final File certificate, final File key) throws SSLException {
            if (certificate == null || key == null) {
                throw new IllegalArgumentException("Certificate and key must not be null!");
            }
            sslContext = SslContextBuilder.forServer(certificate, key).build();

            return this;
        }

        public NettyHttpServerBuilder withSSL(final InputStream certificate, final InputStream key)
                throws SSLException {
            if (certificate == null || key == null) {
                throw new IllegalArgumentException("Certificate and key must not be null!");
            }
            sslContext = SslContextBuilder.forServer(certificate, key).build();

            return this;
        }

        public NettyHttpServerBuilder withSelfSignedSSL() throws CertificateException, SSLException {
            final SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslContext = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();

            return this;
        }

        public NettyHttpServerBuilder withLogger(final Logger logger) {
            this.logger = logger;
            return this;
        }

        public NettyHttpServer build(final int port, final Supplier<HttpRequestListener> supplier) {
            this.port = port;
            this.listenerPool = new ConcurrentObjectPool<>(supplier);
            if (logger == null) {
                this.logger = SystemLogger.info();
            }

            return new NettyHttpServer(this);
        }
    }
}