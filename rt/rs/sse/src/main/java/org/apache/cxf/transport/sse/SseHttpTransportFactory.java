/**
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
 */
package org.apache.cxf.transport.sse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.cxf.Bus;
import org.apache.cxf.common.injection.NoJSR250Annotations;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.ConduitInitiator;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.DestinationFactory;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.cxf.transport.http.DestinationRegistry;
import org.apache.cxf.transport.http.HTTPTransportFactory;

@NoJSR250Annotations
public class SseHttpTransportFactory extends HTTPTransportFactory
        implements ConduitInitiator, DestinationFactory {

    public static final String TRANSPORT_ID = "http://cxf.apache.org/transports/http/sse";
    public static final List<String> DEFAULT_NAMESPACES = Collections.unmodifiableList(Arrays.asList(
        TRANSPORT_ID,
        "http://cxf.apache.org/transports/http/sse/configuration"
    ));
    
    private final SseDestinationFactory factory = new SseDestinationFactory();

    public SseHttpTransportFactory() {
        this(null);
    }

    public SseHttpTransportFactory(DestinationRegistry registry) {
        super(DEFAULT_NAMESPACES, registry);
    }

    @Override
    public Destination getDestination(EndpointInfo endpointInfo, Bus bus) throws IOException {
        if (endpointInfo == null) {
            throw new IllegalArgumentException("EndpointInfo cannot be null");
        }
        
        // In order to register the destination in the OSGi container, we have to 
        // include it into 2 registries basically: for HTTP transport and the current 
        // one. The workaround is borrow from org.apache.cxf.transport.websocket.WebSocketTransportFactory,
        // it seems like no better option exists at the moment.
        synchronized (registry) {
            AbstractHTTPDestination d = registry.getDestinationForPath(endpointInfo.getAddress());
            
            if (d == null) {
                d = factory.createDestination(endpointInfo, bus, registry);
                if (d == null) {
                    throw new IOException("No destination available. The CXF SSE transport needs Atmosphere"
                        + " dependencies to be available");
                }
                registry.addDestination(d);
                configure(bus, d);
                d.finalizeConfig();
            }
            
            return d;
        }
    }
}