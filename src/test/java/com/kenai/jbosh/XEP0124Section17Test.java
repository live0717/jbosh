/*
 * Copyright 2009 Mike Cumings
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kenai.jbosh;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static junit.framework.Assert.*;

/**
 * BOSH XEP-0124 specification section 17 tests: Error and Status Codes.
 */
public class XEP0124Section17Test extends AbstractBOSHTest {

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 17.1: HTTP Conditions

    /*
     * Non-legacy connection managers SHOULD NOT send HTTP error codes unless
     * they are communicating with a legacy client.
     */
    // BOSH CM functionality not supported.

    /*
     * Upon receiving an HTTP error (400, 403, 404), a legacy client or any
     * client that is communicating with a legacy connection manager MUST
     * consider the HTTP session to be null and void.
     */
    @Test(timeout=5000)
    public void interpretLegacyHTTPCodes() throws Exception {
        logTestStart();
        // Initiate a new session with a legacy response
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponseWithStatus(scr, 400);

        // This should not work, since the ver attr indicated legacy CM
        try {
            session.send(ComposableBody.builder().build());
            conn = cm.awaitConnection();
            conn.sendResponse(ComposableBody.builder()
                    .setAttribute(Attributes.SID, "123XYZ")
                    .build());
            fail("Did not catch legacy CM terminal binding error");
        } catch (BOSHException boshx) {
            // Good.
        }

        assertValidators(scr);
    }

    /*
     * A non-legacy client that is communicating with a non-legacy connection
     * manager MAY consider that the session is still active.
     */
    @Test(timeout=5000)
    public void ignoreNonLegacyHTTPCodes() throws Exception {
        logTestStart();
        // Initiate a new session
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .setAttribute(Attributes.VER, "1.8")
                .build();
        conn.sendResponseWithStatus(scr, 400);

        // This should work, since the ver attr indicated non-legacy CM
        try {
            session.send(ComposableBody.builder().build());
            conn = cm.awaitConnection();
            conn.sendResponse(ComposableBody.builder()
                    .setAttribute(Attributes.SID, "123XYZ")
                    .build());
        } catch (BOSHException boshx) {
            fail("Caught boshx: " + boshx.getMessage());
        }

        assertValidators(scr);
    }

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 17.2: Terminal Binding Conditions

    /*
     * In any response it sends to the client, the connection manager MAY
     * return a fatal error by setting a 'type' attribute of the <body/>
     * element to "terminate".
     */
    @Test(timeout=5000)
    public void testTerminalBindingError() throws Exception {
        logTestStart();

        final AtomicBoolean connected = new AtomicBoolean();
        final AtomicReference<Throwable> caught =
                new AtomicReference<Throwable>();
        session.addBOSHClientConnListener(new BOSHClientConnListener() {
            public void connectionEvent(final BOSHClientConnEvent connEvent) {
                connected.set(connEvent.isConnected());
                caught.set(connEvent.getCause());
            }
        });

        assertFalse(connected.get());
        assertNull(caught.get());

        // Initiate a new session
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .setAttribute(Attributes.VER, "1.8")
                .setAttribute(Attributes.TYPE, "terminate")
                .setAttribute(Attributes.CONDITION, "bad-request")
                .build();
        conn.sendResponse(scr);
        session.drain();

        assertFalse(connected.get());
        assertNotNull(caught.get());
        BOSHException boshx = (BOSHException) caught.get();
        assertTrue(boshx.getMessage().contains(
                TerminalBindingCondition.BAD_REQUEST.getMessage()));

        // Attempts to send anything else should fail
        try {
            session.send(ComposableBody.builder().build());
            conn = cm.awaitConnection();
            conn.sendResponse(ComposableBody.builder()
                    .setAttribute(Attributes.SID, "123XYZ")
                    .build());
            fail("Shouldn't be able to send after terminal binding error");
        } catch (BOSHException ex) {
            // Good
        }
        assertValidators(scr);
    }

    /*
     * In cases where BOSH is being used to transport XMPP, any fatal XMPP
     * stream error conditions experienced between the connection manager and
     * the XMPP server SHOULD only be reported using the "remote-stream-error"
     * condition.
     */
    // Application considerations are out of slope for this library

    /*
     * If the client did not include a 'ver' attribute in its session creation
     * request then the connection manager SHOULD send a deprecated HTTP Error
     * Condition instead of this terminal binding condition.
     */
    // BOSH CM functionality not supported.

    /*
     * If the connection manager did not include a 'ver' attribute in its
     * session creation response then the client SHOULD expect it to send a
     * deprecated HTTP Error Condition instead of this terminal binding
     * condition.
     */
    @Test(timeout=5000)
    public void testDeprecatedHTTPError() throws Exception {
        logTestStart();
        // Initiate a new session
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponseWithStatus(scr, 400);

        // Attempts to send anything else should fail
        try {
            session.send(ComposableBody.builder().build());
            conn = cm.awaitConnection();
            conn.sendResponse(ComposableBody.builder()
                    .setAttribute(Attributes.SID, "123XYZ")
                    .build());
            fail("Shouldn't be able to send after terminal binding error");
        } catch (BOSHException boshx) {
            // Good
        }
        assertValidators(scr);
    }

    /*
     * The client MAY report binding errors to the connection manager as well,
     * although this is unlikely
     */
    // Not supported

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 17.3: Recoverable Binding Conditions

    /*
     * In any response it sends to the client, the connection manager MAY
     * return a recoverable error by setting a 'type' attribute of the <body/>
     * element to "error".
     */
    // BOSH CM functionality not supported.

    /*
     * If it decides to recover from the error, then the client MUST repeat the
     * HTTP request that resulted in the error, as well as all the preceding
     * HTTP requests that have not received responses.  The content of these
     * requests MUST be identical to the <body/> elements of the original
     * requests.
     */
    @Test(timeout=5000)
    public void retryRecoverableErrors_HTTP10() throws Exception {
        retryRecoverableErrors(true);
    }

    @Test(timeout=5000)
    public void retryRecoverableErrors_HTTP11() throws Exception {
        retryRecoverableErrors(false);
    }

    public void retryRecoverableErrors(boolean http10) throws Exception {
        logTestStart();
        String testURI = "http://kenai.com/jbosh/junit";
        BodyQName ref = BodyQName.createWithPrefix(testURI, "ref", "test");

        // Initiate a new session
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = this.getSessionCreationResponse(conn.getRequest().getBody())
                .setAttribute(Attributes.REQUESTS, "3")
                .build();
        if(http10)
            conn.forceHTTP1();
        conn.sendResponse(scr);
        session.drain();

        // Send a couple requests
        session.send(ComposableBody.builder()
                .setNamespaceDefinition("test", testURI)
                .setAttribute(ref, "Req1")
                .build());
        StubConnection conn1 = cm.awaitConnection();
        session.send(ComposableBody.builder()
                .setNamespaceDefinition("test", testURI)
                .setAttribute(ref, "Req2")
                .build());
        StubConnection conn2 = cm.awaitConnection();
        String expected2 = conn2.getRequest().getBody().toXML();

        // When testing HTTP/1.0, send a response to the second request before
        // returning an error on the first.  With pipelining enabled on HTTP/1.1,
        // this can't happen.
        if(http10) {
            conn2.sendResponse(ComposableBody.builder()
                    .setNamespaceDefinition("test", testURI)
                    .setAttribute(ref, "Resp2")
                    .build());
        }

        // When we send the response below, the requests will be resent.  Verify
        // from a listener that the retransmissions are sent in the correct order.
        final ArrayList<String> orderedResends = new ArrayList<String>();
        session.addBOSHClientRequestListener(new BOSHClientRequestListener() {
            public void requestSent(BOSHMessageEvent event) {
                synchronized(orderedResends) {
                    String actual = event.getBody().toXML();
                    orderedResends.add(actual);
                    orderedResends.notify();
                }
            }
        });

        // Now respond to the first request with a recoverable error
        String expected1 = conn1.getRequest().getBody().toXML();
        conn1.sendResponse(ComposableBody.builder()
                .setNamespaceDefinition("test", testURI)
                .setAttribute(Attributes.TYPE, "error")
                .setAttribute(ref, "Resp1")
                .build());

        // We should now receive requests which are duplicates of msg 1 and 2

        // Get msg 1 duplicate
        conn = cm.awaitConnection();
        String actual1 = conn.getRequest().getBody().toXML();
        conn.sendResponse(ComposableBody.builder()
                .setNamespaceDefinition("test", testURI)
                .setAttribute(ref, "Resp3")
                .build());

        // Get msg 2 duplicate
        conn = cm.awaitConnection();
        String actual2 = conn.getRequest().getBody().toXML();
        conn.sendResponse(ComposableBody.builder()
                .setNamespaceDefinition("test", testURI)
                .setAttribute(ref, "Resp4")
                .build());

        synchronized(orderedResends) {
            while(orderedResends.size() < 2) {
                orderedResends.wait();
            }
        }

        // Check that the resends were sent out in the correct order.
        assertEquals(expected1, orderedResends.get(0));
        assertEquals(expected2, orderedResends.get(1));

        // Check that the CM received the resends, in either order.  In order
        // to verify ordering here, we'd need to enable pipelining in StubCM,
        // so the resends aren't sent in separate connections.
        if(!(expected1.equals(actual1) && expected2.equals(actual2)) &&
           !(expected1.equals(actual2) && expected2.equals(actual1))) {
            fail("Expected:\n" + expected1 + "\nand\n" + expected2 +
                    "\nbut got:\n" + actual1 + "\nand\n" + actual2 + "\n");
        }

        session.drain();
    }

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 17.4: XML Payload Conditions

}
