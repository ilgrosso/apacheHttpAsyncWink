/** *****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 ****************************************************************************** */
package net.tirasa.wink.client.asynchttpclient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.conn.PlainIOSessionFactory;
import org.apache.http.nio.conn.SchemeIOSessionFactory;
import org.apache.http.nio.conn.ssl.SSLIOSessionFactory;
import org.apache.wink.client.ClientRequest;
import org.apache.wink.client.ClientResponse;
import org.apache.wink.client.handlers.HandlerContext;
import org.apache.wink.client.httpclient.ApacheHttpClientConfig;
import org.apache.wink.client.internal.handlers.AbstractConnectionHandler;
import org.apache.wink.common.internal.WinkConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extends AbstractConnectionHandler and uses Apache HttpClient to perform HTTP
 * request execution. Each outgoing http request is wrapped by EntityWriter.
 */
public class ApacheHttpAsyncClientConnectionHandler extends AbstractConnectionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApacheHttpAsyncClientConnectionHandler.class);

    private CloseableHttpAsyncClient httpclient;

    public ApacheHttpAsyncClientConnectionHandler() {
        httpclient = null;
    }

    public ApacheHttpAsyncClientConnectionHandler(CloseableHttpAsyncClient httpclient) {
        this.httpclient = httpclient;
    }

    @Override
    public FutureClientResponse handle(ClientRequest request, HandlerContext context) throws Exception {
        try {
            Future<HttpResponse> response = processRequest(request, context);
            return new FutureClientResponse(this, request, response, context);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream adaptInputStream(InputStream is, ClientResponse response, HandlerContext context)
            throws IOException {

        return adaptInputStream(is, response, context.getInputStreamAdapters());
    }

    private Future<HttpResponse> processRequest(ClientRequest request, HandlerContext context)
            throws IOException, KeyManagementException, NoSuchAlgorithmException {

        final CloseableHttpAsyncClient client = openConnection(request);
        // TODO: move this functionality to the base class
        NonCloseableOutputStream ncos = new NonCloseableOutputStream();

        EntityWriter entityWriter = null;
        if (request.getEntity() != null) {
            OutputStream os = adaptOutputStream(ncos, request, context.getOutputStreamAdapters());
            // cast is safe because we're on the client
            ApacheHttpClientConfig config = (ApacheHttpClientConfig) request.getAttribute(WinkConfiguration.class);
            // prepare the entity that will write our entity
            entityWriter = new EntityWriter(this, request, os, ncos, config.isChunked());
        }

        HttpRequestBase entityRequest = setupHttpRequest(request, entityWriter);

        try {
            return client.execute(entityRequest, new FutureCallback<HttpResponse>() {

                @Override
                public void completed(HttpResponse t) {
                    LOG.debug("Client completed with response {}", t);
                    try {
                        client.close();
                    } catch (IOException e) {
                        LOG.error("While closing", e);
                    }
                }

                @Override
                public void failed(Exception excptn) {
                    LOG.error("Client failed with exception", excptn);
                    try {
                        client.close();
                    } catch (IOException e) {
                        LOG.error("While closing", e);
                    }
                }

                @Override
                public void cancelled() {
                    LOG.debug("Client execution cancelled");
                    try {
                        client.close();
                    } catch (IOException e) {
                        LOG.error("While closing", e);
                    }
                }
            });
        } catch (Exception ex) {
            entityRequest.abort();
            throw new RuntimeException(ex);
        }
    }

    private HttpRequestBase setupHttpRequest(ClientRequest request,
            ApacheHttpAsyncClientConnectionHandler.EntityWriter entityWriter) {

        URI uri = request.getURI();
        String method = request.getMethod();
        HttpRequestBase httpRequest;
        if (entityWriter == null) {
            GenericHttpRequestBase entityRequest = new GenericHttpRequestBase(method);
            httpRequest = entityRequest;
        } else {
            // create a new request with the specified method
            HttpEntityEnclosingRequestBase entityRequest =
                    new ApacheHttpAsyncClientConnectionHandler.GenericHttpEntityEnclosingRequestBase(method);
            entityRequest.setEntity(entityWriter);
            httpRequest = entityRequest;
        }
        // set the uri
        httpRequest.setURI(uri);
        // add all headers
        MultivaluedMap<String, String> headers = request.getHeaders();
        for (String header : headers.keySet()) {
            List<String> values = headers.get(header);
            for (String value : values) {
                if (value != null) {
                    httpRequest.addHeader(header, value);
                }
            }
        }
        return httpRequest;
    }

    private synchronized CloseableHttpAsyncClient openConnection(ClientRequest request)
            throws NoSuchAlgorithmException, KeyManagementException, IOException {

        if (this.httpclient != null) {
            return this.httpclient;
        }

        HttpAsyncClientBuilder clientBuilder = HttpAsyncClientBuilder.create();

        // cast is safe because we're on the client
        ApacheHttpAsyncClientConfig config =
                (ApacheHttpAsyncClientConfig) request.getAttribute(WinkConfiguration.class);

        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom().
                setConnectTimeout(config.getConnectTimeout()).
                setSocketTimeout(config.getReadTimeout());
        if (config.isFollowRedirects()) {
            requestConfigBuilder.setRedirectsEnabled(true).setCircularRedirectsAllowed(true);
        }

        // setup proxy
        if (config.getProxyHost() != null) {
            requestConfigBuilder.setProxy(new HttpHost(config.getProxyHost(), config.getProxyPort()));
        }

        clientBuilder.setDefaultRequestConfig(requestConfigBuilder.build());

        Registry<SchemeIOSessionFactory> connManagerRegistry;
        if (config.getBypassHostnameVerification()) {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, null, null);

            connManagerRegistry = RegistryBuilder.<SchemeIOSessionFactory>create()
                    .register("http", PlainIOSessionFactory.INSTANCE)
                    .register("https", new SSLIOSessionFactory(sslcontext, new X509HostnameVerifier() {

                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }

                @Override
                public void verify(String host, String[] cns, String[] subjectAlts)
                        throws SSLException {
                }

                @Override
                public void verify(String host, X509Certificate cert) throws SSLException {
                }

                @Override
                public void verify(String host, SSLSocket ssl) throws IOException {
                }
            }))
                    .build();
        } else {
            connManagerRegistry = RegistryBuilder.<SchemeIOSessionFactory>create()
                    .register("http", PlainIOSessionFactory.INSTANCE)
                    .register("https", SSLIOSessionFactory.getDefaultStrategy())
                    .build();
        }

        PoolingNHttpClientConnectionManager httpConnectionManager = new PoolingNHttpClientConnectionManager(
                new DefaultConnectingIOReactor(IOReactorConfig.DEFAULT), connManagerRegistry);
        if (config.getMaxPooledConnections() > 0) {
            httpConnectionManager.setMaxTotal(config.getMaxPooledConnections());
            httpConnectionManager.setDefaultMaxPerRoute(config.getMaxPooledConnections());

        }
        clientBuilder.setConnectionManager(httpConnectionManager);

        this.httpclient = clientBuilder.build();
        this.httpclient.start();

        return this.httpclient;
    }

    private static class GenericHttpRequestBase extends HttpRequestBase {

        private String method;

        public GenericHttpRequestBase(String method) {
            this.method = method;
        }

        @Override
        public String getMethod() {
            return method;
        }
    }

    private static class GenericHttpEntityEnclosingRequestBase extends HttpEntityEnclosingRequestBase {

        private String method;

        public GenericHttpEntityEnclosingRequestBase(String method) {
            this.method = method;
        }

        @Override
        public String getMethod() {
            return method;
        }
    }

    // TODO: move this class to the base class
    private static class NonCloseableOutputStream extends OutputStream {

        OutputStream os;

        public NonCloseableOutputStream() {
        }

        public void setOutputStream(OutputStream os) {
            this.os = os;
        }

        @Override
        public void close() throws IOException {
            // do nothing
        }

        @Override
        public void flush() throws IOException {
            os.flush();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            os.write(b, off, len);
        }

        @Override
        public void write(byte[] b) throws IOException {
            os.write(b);
        }

        @Override
        public void write(int b) throws IOException {
            os.write(b);
        }
    }

    private class EntityWriter implements HttpEntity {

        private ClientRequest request;

        private OutputStream adaptedOutputStream;

        private NonCloseableOutputStream ncos;

        private boolean chunked;

        private long length = -1l;

        private byte[] content;

        public EntityWriter(ApacheHttpAsyncClientConnectionHandler apacheHttpClientHandler,
                ClientRequest request,
                OutputStream adaptedOutputStream,
                NonCloseableOutputStream ncos,
                boolean chunked) {

            this.request = request;
            this.adaptedOutputStream = adaptedOutputStream;
            this.ncos = ncos;
            this.chunked = chunked;

            if (!chunked) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try {
                    apacheHttpClientHandler.writeEntity(request, bos);
                    content = bos.toByteArray();
                    length = content.length;
                } catch (IOException e) {
                    throw new WebApplicationException(e);
                }
            }
        }

        @Deprecated
        @Override
        public void consumeContent() throws IOException {
        }

        @Override
        public InputStream getContent() throws IOException, IllegalStateException {
            return null;
        }

        @Override
        public Header getContentEncoding() {
            return null;
        }

        @Override
        public long getContentLength() {
            return length;
        }

        @Override
        public Header getContentType() {
            return null;
        }

        @Override
        public boolean isChunked() {
            return chunked;
        }

        @Override
        public boolean isRepeatable() {
            return true;
        }

        @Override
        public boolean isStreaming() {
            return content == null;
        }

        @Override
        public void writeTo(OutputStream os) throws IOException {
            if (!chunked && length > 0 && content != null) {
                os.write(content);
                os.flush();
            } else {
                ncos.setOutputStream(os);
                ApacheHttpAsyncClientConnectionHandler.this.writeEntity(request, adaptedOutputStream);
            }
        }
    }
}
