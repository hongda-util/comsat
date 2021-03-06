/*
 * COMSAT
 * Copyright (c) 2013-2014, Parallel Universe Software Co. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
/*
 * Based on the corresponding class in Spring Boot Samples.
 * Copyright the original author Dave Syer.
 * Released under the ASF 2.0 license.
 */
package comsat.sample.actuator;

import co.paralleluniverse.test.categories.CI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Basic integration tests for service demo application.
 *
 * @author Dave Syer
 * @author circlespainter
 */
@Category(CI.class)
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = SampleActuatorApplication.class)
@WebAppConfiguration
@IntegrationTest("server.port=0")
@DirtiesContext
public class SampleActuatorApplicationTests {

    @Autowired
    private SecurityProperties security;

    @Value("${local.server.port}")
    private int port;

    private String emptyIfNull(String s) {
        return (s != null ? s : "");
    }
    
    private void testHomeIsSecure(final String path) throws Exception {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> entity = new TestRestTemplate().getForEntity(
                "http://localhost:" + this.port + "/" + emptyIfNull(path), Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, entity.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = entity.getBody();
        assertEquals("Wrong body: " + body, "Unauthorized", body.get("error"));
        assertFalse("Wrong headers: " + entity.getHeaders(), entity.getHeaders()
                .containsKey("Set-Cookie"));
    }
    
    @Test
    public void testHomeIsSecureDeferred() throws Exception {
        testHomeIsSecure("deferred");
    }

    @Test
    public void testHomeIsSecureCallable() throws Exception {
        testHomeIsSecure("callable");
    }

    @Test
    public void testMetricsIsSecure() throws Exception {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> entity = new TestRestTemplate().getForEntity(
                "http://localhost:" + this.port + "/metrics", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, entity.getStatusCode());
        entity = new TestRestTemplate().getForEntity("http://localhost:" + this.port
                + "/metrics/", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, entity.getStatusCode());
        entity = new TestRestTemplate().getForEntity("http://localhost:" + this.port
                + "/metrics/foo", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, entity.getStatusCode());
        entity = new TestRestTemplate().getForEntity("http://localhost:" + this.port
                + "/metrics.json", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, entity.getStatusCode());
    }

    private void testHome(final String path) throws Exception {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> entity = new TestRestTemplate("user", getPassword())
                .getForEntity("http://localhost:" + this.port + "/" + emptyIfNull(path), Map.class);
        assertEquals(HttpStatus.OK, entity.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = entity.getBody();
        assertEquals("Hello Phil", body.get("message"));
    }
    
    @Test
    public void testHomeDeferred() throws Exception {
        testHome("deferred");
    }

    @Test
    public void testHomeCallable() throws Exception {
        testHome("callable");
    }

    @Test
    public void testMetrics() throws Exception {
        testHomeCallable(); // makes sure some requests have been made
        testHomeDeferred(); // makes sure some requests have been made
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> entity = new TestRestTemplate("user", getPassword())
                .getForEntity("http://localhost:" + this.port + "/metrics", Map.class);
        assertEquals(HttpStatus.OK, entity.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = entity.getBody();
        assertTrue("Wrong body: " + body, body.containsKey("counter.status.200.callable"));
        assertTrue("Wrong body: " + body, body.containsKey("counter.status.200.deferred"));
    }

    @Test
    public void testEnv() throws Exception {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> entity = new TestRestTemplate("user", getPassword())
                .getForEntity("http://localhost:" + this.port + "/env", Map.class);
        assertEquals(HttpStatus.OK, entity.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = entity.getBody();
        assertTrue("Wrong body: " + body, body.containsKey("systemProperties"));
    }

    @Test
    public void testHealth() throws Exception {
        ResponseEntity<String> entity = new TestRestTemplate().getForEntity(
                "http://localhost:" + this.port + "/health", String.class);
        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertTrue("Wrong body: " + entity.getBody(),
                entity.getBody().contains("\"status\":\"UP\""));
    }

    private void testErrorPage(final String path) throws Exception {
        ResponseEntity<String> entity = new TestRestTemplate("user", getPassword())
                .getForEntity("http://localhost:" + this.port + "/" + emptyIfNull(path) + "/foo", String.class);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, entity.getStatusCode());
        String body = entity.getBody();
        assertNotNull(body);
        assertTrue("Wrong body: " + body, body.contains("\"error\":"));
    }

    @Test
    public void testErrorPageDeferred() throws Exception {
        testErrorPage("deferred");
    }

    @Test
    public void testErrorPageCallable() throws Exception {
        testErrorPage("callable");
    }

    private void testHtmlErrorPage(final String path) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.TEXT_HTML));
        HttpEntity<?> request = new HttpEntity<Void>(headers);
        ResponseEntity<String> entity = new TestRestTemplate("user", getPassword())
                .exchange("http://localhost:" + this.port + "/" + emptyIfNull(path) + "/foo", HttpMethod.GET,
                        request, String.class);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, entity.getStatusCode());
        String body = entity.getBody();
        assertNotNull("Body was null", body);
        assertTrue("Wrong body: " + body,
                body.contains("This application has no explicit mapping for /error"));
    }

    @Test
    public void testHtmlErrorPageDeferred() throws Exception {
        testHtmlErrorPage("deferred");
    }

    @Test
    public void testHtmlErrorPageCallable() throws Exception {
        testHtmlErrorPage("callable");
    }

    @Test
    public void testTrace() throws Exception {
        new TestRestTemplate().getForEntity("http://localhost:" + this.port + "/health",
                String.class);
        @SuppressWarnings("rawtypes")
        ResponseEntity<List> entity = new TestRestTemplate("user", getPassword())
                .getForEntity("http://localhost:" + this.port + "/trace", List.class);
        assertEquals(HttpStatus.OK, entity.getStatusCode());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = entity.getBody();
        Map<String, Object> trace = list.get(list.size() - 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) trace
                .get("info")).get("headers")).get("response");
        assertEquals("200", map.get("status"));
    }

    @Test
    public void testErrorPageDirectAccess() throws Exception {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> entity = new TestRestTemplate().getForEntity(
                "http://localhost:" + this.port + "/error", Map.class);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, entity.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = entity.getBody();
        assertEquals("None", body.get("error"));
        assertEquals(999, body.get("status"));
    }

    @Test
    public void testBeans() throws Exception {
        @SuppressWarnings("rawtypes")
        ResponseEntity<List> entity = new TestRestTemplate("user", getPassword())
                .getForEntity("http://localhost:" + this.port + "/beans", List.class);
        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertEquals(1, entity.getBody().size());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) entity.getBody().get(0);
        assertTrue("Wrong body: " + body,
                ((String) body.get("context")).startsWith("application"));
    }

    private String getPassword() {
        return this.security.getUser().getPassword();
    }
}
