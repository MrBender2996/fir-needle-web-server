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


import fir.needle.joint.colleclions.ConcurrentObjectPool;
import fir.needle.joint.colleclions.Pool;
import fir.needle.joint.colleclions.SimpleParametrizedPrefixTree;
import fir.needle.joint.io.ByteArea;
import fir.needle.joint.io.ByteToCharArea;
import fir.needle.joint.logging.Logger;
import fir.needle.joint.logging.SystemLogger;
import fir.needle.web.server.http.HttpRequestListener;
import fir.needle.web.server.http.HttpResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RestRouter implements HttpRequestListener {
    private static final String EOL = "\n";
    private final SimpleParametrizedPrefixTree<Pool<RestListener>> routesTree;
    private final List<SimpleParametrizedPrefixTree.Parameter> pathParams = new ArrayList<>();
    private final Logger logger;

    private RestListener crtListener;
    private Pool<RestListener> crtListenersPool;
    private FlyweightCharSequence parameter = new FlyweightCharSequence();


    RestRouter(final RestRouterBuilder builder, final Logger logger) {
        this.routesTree = builder.routesTree;
        this.logger = logger;
    }

    public static RestRouterBuilder builder() {
        return new RestRouterBuilder();
    }

    @Override
    public void onRequestStarted(final CharSequence method, final CharSequence url, final HttpResponse response) {
        if (logger.isTraceEnabled()) {
            logger.trace("On get started " + this.toString() + " by " + Thread.currentThread());
        }

        crtListenersPool = routesTree.find(url, pathParams);

        if (crtListenersPool == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("On get started NOT_FOUND " + this.toString() + " by " + Thread.currentThread() + " for " +
                        url);
            }

            throwNotFound(response, url);
            return;
        }

        try {
            crtListener = crtListenersPool.borrow();
        } catch (final Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error(getStackTrace(e));
            }
        }

        try {
            crtListener.onRequestStarted(method, new RestResponseToHttpResponse(response));
        } catch (final Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error(getStackTrace(e));
            }
        }

        for (int i = 0; i < pathParams.size(); i++) {
            parameter.set(url, pathParams.get(i).getStartIndex(), pathParams.get(i).getLength());
            try {
                crtListener.onParameter(pathParams.get(i).getName(), parameter);
            } catch (final Exception e) {
                if (logger.isErrorEnabled()) {
                    logger.error(getStackTrace(e));
                }
            }
        }

        pathParams.clear();
        parameter.clear();
    }

    private void throwNotFound(final HttpResponse response, final CharSequence url) {
        response.error().custom(404, "No such url!\n" + url.toString()).commit();
        onCommitted();
    }

    private String getStackTrace(final Throwable throwable) {
        return Stream.of(throwable.getStackTrace())
                .map(Object::toString)
                .collect(Collectors.joining(EOL));
    }

    @Override
    public void onParameter(final CharSequence name, final CharSequence value) {
        try {
            crtListener.onParameter(name, value);
        } catch (final Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error(getStackTrace(e));
            }
        }

    }

    @Override
    public void onHeader(final CharSequence key, final CharSequence value) {

    }

    @Override
    public void onBodyStarted() {

    }

    @Override
    public void onBodyContent(final ByteArea buffer, final long startIndex, final long length) {
        try {
            crtListener.onBodyPart(new ByteToCharArea(buffer), startIndex, length);
        } catch (final Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error(getStackTrace(e));
            }
        }
    }

    @Override
    public void onBodyFinished() {

    }

    @Override
    public void onPartStarted() {

    }

    @Override
    public void onPartContent(final ByteArea buffer, final long startIndex, final long length) {

    }

    @Override
    public void onPartFinished() {

    }

    @Override
    public void onError(final Throwable exception) {
        try {
            crtListener.onError(exception);
        } catch (final Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error(getStackTrace(e));
            }
        }
    }

    @Override
    public void onRequestFinished() {
        if (logger.isTraceEnabled()) {
            logger.trace("On get finished " + this.toString() + " by " + Thread.currentThread());
        }

        if (crtListenersPool != null && crtListener != null) {
            try {
                crtListener.onRequestFinished();
            } catch (final Exception e) {
                if (logger.isErrorEnabled()) {
                    logger.error(getStackTrace(e));
                }
            } finally {
                crtListenersPool.release(crtListener);
                crtListenersPool = null;
                crtListener = null;
            }
        }
    }

    @Override
    public void onCommitted() {

    }

    public static final class RestRouterBuilder {
        private static final String DELIMITER = "/";
        private final SimpleParametrizedPrefixTree<Pool<RestListener>> routesTree;
        private Logger logger;

        private RestRouterBuilder() {
            routesTree = new SimpleParametrizedPrefixTree<>(DELIMITER);
        }

        public RestRouterBuilder withPair(final CharSequence url, final Supplier<RestListener> supplier) {
            routesTree.insert(url.toString(), new ConcurrentObjectPool<>(supplier));
            return this;
        }

        public RestRouterBuilder withLogger(final Logger logger) {
            this.logger = logger;
            return this;
        }

        public RestRouter build() {
            if (logger == null) {
                logger = SystemLogger.info();
            }

            return new RestRouter(this, logger);
        }
    }
}