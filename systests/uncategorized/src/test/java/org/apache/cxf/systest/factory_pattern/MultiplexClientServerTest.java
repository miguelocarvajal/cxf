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

package org.apache.cxf.systest.factory_pattern;


import java.util.HashMap;
import java.util.Map;

import javax.xml.ws.Endpoint;
import javax.xml.ws.wsaddressing.W3CEndpointReference;

import org.apache.cxf.BusFactory;
import org.apache.cxf.factory_pattern.Number;
import org.apache.cxf.factory_pattern.NumberFactory;
import org.apache.cxf.factory_pattern.NumberFactoryService;
import org.apache.cxf.factory_pattern.NumberService;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.ServiceImpl;
import org.apache.cxf.jaxws.support.ServiceDelegateAccessor;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.cxf.testutil.common.AbstractBusTestServerBase;
import org.apache.cxf.testutil.common.EmbeddedJMSBrokerLauncher;

import org.junit.BeforeClass;
import org.junit.Test;



public class MultiplexClientServerTest extends AbstractBusClientServerTestBase {
    static final String PORT = NumberFactoryImpl.PORT;
    static final String JMS_PORT = EmbeddedJMSBrokerLauncher.PORT;
    
    public static class Server extends AbstractBusTestServerBase {        

        protected void run() {
            Object implementor = new NumberFactoryImpl();
            Endpoint.publish(NumberFactoryImpl.FACTORY_ADDRESS, implementor);
        }

        public static void main(String[] args) {
            try {
                Server s = new Server();
                s.start();
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(-1);
            } finally {
                System.out.println("done!");
            }
        }
    }

    @BeforeClass
    public static void startServers() throws Exception {
        // requires ws-a support to propagate reference parameters
        createStaticBus("org/apache/cxf/systest/factory_pattern/cxf_multiplex.xml");
        Map<String, String> props = new HashMap<String, String>();    
        if (System.getProperty("org.apache.activemq.default.directory.prefix") != null) {
            props.put("org.apache.activemq.default.directory.prefix", 
                      System.getProperty("org.apache.activemq.default.directory.prefix"));
        }
        props.put("java.util.logging.config.file", 
                  System.getProperty("java.util.logging.config.file"));
        assertTrue("server did not launch correctly", 
                   launchServer(EmbeddedJMSBrokerLauncher.class, props, null, true));
        
        props.put("cxf.config.file", defaultConfigFileName);
        assertTrue("server did not launch correctly",
                   launchServer(Server.class, props, null, true));
    }

    
    @Test
    public void testWithGetPortExtensionHttp() throws Exception {
        
        NumberFactoryService service = new NumberFactoryService();
        NumberFactory factory = service.getNumberFactoryPort();
        updateAddressPort(factory, PORT);
        
        NumberService numService = new NumberService();
        ServiceImpl serviceImpl = ServiceDelegateAccessor.get(numService);
        
        W3CEndpointReference numberTwoRef = factory.create("20");
        assertNotNull("reference", numberTwoRef);
           
        Number num =  (Number)serviceImpl.getPort(numberTwoRef, Number.class);
        assertTrue("20 is even", num.isEven().isEven());
        
        ClientProxy.getClient(num).getConduit().close();
        
        W3CEndpointReference numberTwentyThreeRef = factory.create("23");
        num =  (Number)serviceImpl.getPort(numberTwentyThreeRef, Number.class);
        assertTrue("23 is not even", !num.isEven().isEven());
        
        ClientProxy.getClient(num).getConduit().close();
        ClientProxy.getClient(factory).getConduit().close();
    }
    
    @Test
    public void testWithGetPortExtensionOverJMS() throws Exception {
        
        NumberFactoryService service = new NumberFactoryService();
        NumberFactory factory = service.getNumberFactoryPort();
        updateAddressPort(factory, PORT);
        

        // use values >= 30 to create JMS eprs - see NumberFactoryImpl.create
        
        // verify it is JMS, 999 for JMS will throw a fault
        W3CEndpointReference ref = factory.create("999");
        String s = NumberService.WSDL_LOCATION.toString();
        EmbeddedJMSBrokerLauncher.updateWsdlExtensors(BusFactory.getDefaultBus(), s);
        NumberService numService = new NumberService();

        assertNotNull("reference", ref);
        ServiceImpl serviceImpl = ServiceDelegateAccessor.get(numService);    
        Number num =  (Number)serviceImpl.getPort(ref, Number.class); 
        try {
            num.isEven().isEven();
            fail("there should be a fault on val 999");
        } catch (Exception expected) {
            assertTrue("match on exception message " + expected.getMessage(),
                       expected.getMessage().indexOf("999") != -1);
        }
        ClientProxy.getClient(num).getConduit().close();
        
        ref = factory.create("37");
        assertNotNull("reference", ref);
        num =  (Number)serviceImpl.getPort(ref, Number.class);
        assertTrue("37 is not even", !num.isEven().isEven());
        
        ClientProxy.getClient(num).getConduit().close();
        ClientProxy.getClient(factory).getConduit().close();
    }
}
