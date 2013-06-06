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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.apache.http.Header;
import org.apache.http.HttpEntity;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.util.EntityUtils;
import org.apache.wink.client.ClientRequest;

import org.apache.wink.client.ClientResponse;
import org.apache.wink.client.EntityType;
import org.apache.wink.client.handlers.HandlerContext;
import org.apache.wink.client.internal.handlers.ClientResponseImpl;

public class FutureClientResponse implements Future<ClientResponse>, ClientResponse {

    private final ApacheHttpAsyncClientConnectionHandler handler;

    private final ClientRequest request;

    private final Future<HttpResponse> futureResponse;

    private final HandlerContext context;

    private ClientResponseImpl clientResponse;

    public FutureClientResponse(final ApacheHttpAsyncClientConnectionHandler handler, final ClientRequest request,
            final Future<HttpResponse> futureResponse, final HandlerContext context) {

        super();

        this.handler = handler;
        this.request = request;
        this.futureResponse = futureResponse;
        this.context = context;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return futureResponse.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return futureResponse.isCancelled();
    }

    @Override
    public boolean isDone() {
        return futureResponse.isDone();
    }

    private void createClientResponse(final HttpResponse httpResponse) throws IOException {
        this.clientResponse = new ClientResponseImpl();
        StatusLine statusLine = httpResponse.getStatusLine();
        this.clientResponse.setStatusCode(statusLine.getStatusCode());
        this.clientResponse.setMessage(statusLine.getReasonPhrase());
        this.clientResponse.getAttributes().putAll(this.request.getAttributes());
        this.clientResponse.setContentConsumer(new Runnable() {

            @Override
            public void run() {
                HttpEntity entity = httpResponse.getEntity();
                if (entity != null) {
                    try {
                        EntityUtils.consume(entity);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });

        Header[] allHeaders = httpResponse.getAllHeaders();
        for (Header header : allHeaders) {
            this.clientResponse.getHeaders().add(header.getName(), header.getValue());
        }

        HttpEntity entity = httpResponse.getEntity();
        InputStream is;
        if (entity == null) {
            is = new EmptyInputStream();
        } else {
            is = entity.getContent();
        }
        is = this.handler.adaptInputStream(is, this.clientResponse, this.context);
        this.clientResponse.setEntity(is);
    }

    @Override
    public ClientResponse get() throws InterruptedException, ExecutionException {
        synchronized (this) {
            if (this.clientResponse == null) {
                try {
                    createClientResponse(this.futureResponse.get());
                } catch (IOException e) {
                    throw new ExecutionException(e);
                }
            }
        }
        return this.clientResponse;
    }

    @Override
    public ClientResponse get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {

        synchronized (this) {
            if (this.clientResponse == null) {
                try {
                    createClientResponse(this.futureResponse.get(timeout, unit));
                } catch (IOException e) {
                    throw new ExecutionException(e);
                }
            }
        }
        return this.clientResponse;
    }

    //
    @Override
    public Response.StatusType getStatusType() {
        try {
            return get().getStatusType();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getStatusCode() {
        try {
            return get().getStatusCode();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setStatusCode(int code) {
        try {
            get().setStatusCode(code);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getMessage() {
        try {
            return get().getMessage();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setMessage(String message) {
        try {
            get().setMessage(message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T getEntity(Class<T> cls) {
        try {
            return get().getEntity(cls);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T getEntity(EntityType<T> entityType) {
        try {
            return get().getEntity(entityType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setEntity(Object entity) {
        try {
            get().setEntity(entity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void consumeContent() {
        try {
            get().consumeContent();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MultivaluedMap<String, String> getHeaders() {
        try {
            return get().getHeaders();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, Object> getAttributes() {
        try {
            return get().getAttributes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> void setAttribute(Class<T> key, T attribute) {
        try {
            get().setAttribute(key, attribute);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T getAttribute(Class<T> key) {
        try {
            return get().getAttribute(key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * An empty input stream to simulate an empty message body.
     */
    private static class EmptyInputStream extends InputStream {

        @Override
        public int read() throws IOException {
            return -1;
        }
    }
}
