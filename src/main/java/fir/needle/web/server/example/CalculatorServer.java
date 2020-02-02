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
package fir.needle.web.server.example;


import fir.needle.joint.io.CharAppendable;
import fir.needle.joint.io.CharArea;
import fir.needle.joint.logging.SystemLogger;
import fir.needle.web.server.http.HttpRequestListener;
import fir.needle.web.server.http.netty.NettyHttpServer;
import fir.needle.web.server.http.rest.RestListener;
import fir.needle.web.server.http.rest.RestOutputMessage;
import fir.needle.web.server.http.rest.RestResponse;
import fir.needle.web.server.http.rest.RestRouter;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

public class CalculatorServer {
    public static void main(final String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        NettyHttpServer.builder()
            .withWorkerThreadsAmount(10)
            .withLogger(SystemLogger.trace())
            .build(port, new HttpRequestListenerSupplier(
                RestRouter.builder()
                    .withPair("/arithmetic/sum/{firstSummand}/{secondSummand}", Adder::new)
                    .withPair("/arithmetic/mul/{multiplicand}/{multiplier}", Multiplier::new)
                    .withPair("/arithmetic/div/{dividend}/{divider}", Divider::new)
                    .withPair("/arithmetic/sub/{minuend}/{subtrahend}", Substractor::new))
            )
            .run();
    }

    private static class HttpRequestListenerSupplier implements Supplier<HttpRequestListener> {
        private final RestRouter.RestRouterBuilder builder;

        HttpRequestListenerSupplier(final RestRouter.RestRouterBuilder builder) {
            this.builder = builder;
        }

        @Override
        public HttpRequestListener get() {
            return builder.build();
        }
    }

    private static class Multiplier implements RestListener {
        private List<Integer> params = new LinkedList<>();
        private RestResponse response;

        @Override
        public void onRequestStarted(final CharSequence method, final RestResponse response) {
            this.response = response;
        }

        @Override
        public void onParameter(final CharSequence name, final CharSequence value) {
            params.add(Integer.parseInt(value.toString()));
        }

        @Override
        public void onBodyPart(final CharArea charArea, final long startIndex, final long length) {

        }

        @Override
        public void onError(final Throwable exception) {
            response.error().internalServerError(exception).commit();
        }

        @Override
        public void onRequestFinished() {
            final String result = Integer.toString(params.get(0) * params.get(1));

            final RestOutputMessage outputMessage = response.success().ok();
            final CharAppendable body = outputMessage.body("txt/html", result.length());
            for (int i = 0; i < result.length(); i++) {
                body.appendChar(result.charAt(i));
            }

            outputMessage.commit();
            reset();
        }

        private void reset() {
            params.clear();
            response = null;
        }
    }

    private static class Substractor implements RestListener {
        private List<Integer> params = new LinkedList<>();
        private RestResponse response;

        @Override
        public void onRequestStarted(final CharSequence method, final RestResponse response) {
            this.response = response;
        }

        @Override
        public void onParameter(final CharSequence name, final CharSequence value) {
            params.add(Integer.parseInt(value.toString()));
        }

        @Override
        public void onBodyPart(final CharArea charArea, final long startIndex, final long length) {

        }

        @Override
        public void onError(final Throwable exception) {
            response.error().internalServerError(exception).commit();
        }

        @Override
        public void onRequestFinished() {
            final String result = Integer.toString(params.get(0) - params.get(1));

            final RestOutputMessage outputMessage = response.success().ok();
            final CharAppendable body = outputMessage.body("txt/html", result.length());
            for (int i = 0; i < result.length(); i++) {
                body.appendChar(result.charAt(i));
            }

            outputMessage.commit();
            reset();
        }

        private void reset() {
            params.clear();
            response = null;
        }
    }

    private static class Divider implements RestListener {
        private List<Integer> params = new LinkedList<>();
        private RestResponse response;

        @Override
        public void onRequestStarted(final CharSequence method, final RestResponse response) {
            this.response = response;
        }

        @Override
        public void onParameter(final CharSequence name, final CharSequence value) {
            params.add(Integer.parseInt(value.toString()));
        }

        @Override
        public void onBodyPart(final CharArea charArea, final long startIndex, final long length) {

        }

        @Override
        public void onError(final Throwable exception) {
            response.error().internalServerError(exception).commit();
        }

        @Override
        public void onRequestFinished() {
            final String result = Integer.toString(params.get(0) / params.get(1));

            final RestOutputMessage outputMessage = response.success().ok();
            final CharAppendable body = outputMessage.body("txt/html", result.length());
            for (int i = 0; i < result.length(); i++) {
                body.appendChar(result.charAt(i));
            }

            outputMessage.commit();
            reset();
        }

        private void reset() {
            params.clear();
            response = null;
        }
    }

    private static class Adder implements RestListener {
        private List<Integer> params = new LinkedList<>();
        private RestResponse response;

        @Override
        public void onRequestStarted(final CharSequence method, final RestResponse response) {
            this.response = response;
        }

        @Override
        public void onParameter(final CharSequence name, final CharSequence value) {
            params.add(Integer.parseInt(value.toString()));
        }

        @Override
        public void onBodyPart(final CharArea charArea, final long startIndex, final long length) {

        }

        @Override
        public void onError(final Throwable exception) {
            response.error().internalServerError(exception).commit();
        }

        @Override
        public void onRequestFinished() {
            final String result = Integer.toString(params.get(0) + params.get(1));

            final RestOutputMessage outputMessage = response.success().ok();
            final CharAppendable body = outputMessage.body("txt/html", result.length());
            for (int i = 0; i < result.length(); i++) {
                body.appendChar(result.charAt(i));
            }

            outputMessage.commit();
            reset();
        }

        private void reset() {
            params.clear();
            response = null;
        }
    }
}