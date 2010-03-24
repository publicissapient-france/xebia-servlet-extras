/*
 * Copyright 2009 Xebia and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.xebia.servlet.filter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.Assert;

import org.junit.Test;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.FilterHolder;
import org.mortbay.jetty.servlet.ServletHolder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class XForwardedFilterTest {
    
    public static class MockHttpServlet extends HttpServlet {
        
        private static final long serialVersionUID = 1L;
        
        HttpServletRequest request;
        
        public HttpServletRequest getRequest() {
            return request;
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            this.request = (HttpServletRequest)request;
            PrintWriter writer = response.getWriter();
            
            writer.println("request.remoteAddr=" + request.getRemoteAddr());
            writer.println("request.remoteHost=" + request.getRemoteHost());
            writer.println("request.secure=" + request.isSecure());
            writer.println("request.scheme=" + request.getScheme());
            writer.println("request.serverPort=" + request.getServerPort());
            
            writer.println();
            for (Enumeration<?> headers = request.getHeaderNames(); headers.hasMoreElements();) {
                String name = headers.nextElement().toString();
                writer.println("request.header['" + name + "']=" + Collections.list(request.getHeaders(name)));
            }
        }
    }
    
    @Test
    public void testCommaDelimitedListToStringArray() {
        List<String> elements = Arrays.asList("element1", "element2", "element3");
        String actual = XForwardedFilter.listToCommaDelimitedString(elements);
        assertEquals("element1, element2, element3", actual);
    }
    
    @Test
    public void testCommaDelimitedListToStringArrayEmptyList() {
        List<String> elements = new ArrayList<String>();
        String actual = XForwardedFilter.listToCommaDelimitedString(elements);
        assertEquals("", actual);
    }
    
    @Test
    public void testCommaDelimitedListToStringArrayNullList() {
        String actual = XForwardedFilter.listToCommaDelimitedString(null);
        assertEquals("", actual);
    }
    
    @Test
    public void testHeaderNamesCaseInsensitivity() {
        XForwardedFilter.XForwardedRequest request = new XForwardedFilter.XForwardedRequest(new MockHttpServletRequest());
        request.setHeader("myheader", "lower Case");
        request.setHeader("MYHEADER", "UPPER CASE");
        request.setHeader("MyHeader", "Camel Case");
        assertEquals(1, request.headers.size());
        assertEquals("Camel Case", request.getHeader("myheader"));
    }
    
    @Test
    public void testIncomingRequestIsSecuredButProtocolHeaderSaysItIsNotWithDefaultValues() throws Exception {
        // PREPARE
        XForwardedFilter xforwardedFilter = new XForwardedFilter();
        MockFilterConfig filterConfig = new MockFilterConfig();
        filterConfig.addInitParameter(XForwardedFilter.PROTOCOL_HEADER_PARAMETER, "x-forwarded-proto");

        xforwardedFilter.init(filterConfig);
        MockFilterChain filterChain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest();

        request.setRemoteAddr("192.168.0.10");
        request.setSecure(true);
        request.setScheme("https");
        request.addHeader("x-forwarded-for", "140.211.11.130");
        request.addHeader("x-forwarded-proto", "http");

        // TEST
        xforwardedFilter.doFilter(request, new MockHttpServletResponse(), filterChain);

        // VERIFY
        boolean actualSecure = filterChain.getRequest().isSecure();
        assertEquals("request must be unsecured as header x-forwarded-proto said it is http", false, actualSecure);

        String actualScheme = filterChain.getRequest().getScheme();
        assertEquals("scheme must be http as header x-forwarded-proto said it is http", "http", actualScheme);

        String actualRemoteAddr = ((HttpServletRequest) filterChain.getRequest()).getRemoteAddr();
        assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = ((HttpServletRequest) filterChain.getRequest()).getRemoteHost();
        assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);
    }

    @Test
    public void testIncomingRequestIsSecuredButProtocolHeaderSaysItIsNotWithCustomValues() throws Exception {
        // PREPARE
        XForwardedFilter xforwardedFilter = new XForwardedFilter();
        MockFilterConfig filterConfig = new MockFilterConfig();
        filterConfig.addInitParameter(XForwardedFilter.PROTOCOL_HEADER_PARAMETER, "x-forwarded-proto");
        filterConfig.addInitParameter(XForwardedFilter.REMOTE_IP_HEADER_PARAMETER, "x-my-forwarded-for");
        filterConfig.addInitParameter(XForwardedFilter.HTTP_SERVER_PORT_PARAMETER, "8080");

        xforwardedFilter.init(filterConfig);
        MockFilterChain filterChain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest();

        request.setRemoteAddr("192.168.0.10");
        request.setSecure(true);
        request.setScheme("https");
        request.addHeader("x-my-forwarded-for", "140.211.11.130");
        request.addHeader("x-forwarded-proto", "http");

        // TEST
        xforwardedFilter.doFilter(request, new MockHttpServletResponse(), filterChain);

        // VERIFY
        boolean actualSecure = filterChain.getRequest().isSecure();
        assertEquals("request must be unsecured as header x-forwarded-proto said it is http", false, actualSecure);

        String actualScheme = filterChain.getRequest().getScheme();
        assertEquals("scheme must be http as header x-forwarded-proto said it is http", "http", actualScheme);

        int actualServerPort = filterChain.getRequest().getServerPort();
        assertEquals("wrong http server port", 8080, actualServerPort);
        
        String actualRemoteAddr = ((HttpServletRequest) filterChain.getRequest()).getRemoteAddr();
        assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = ((HttpServletRequest) filterChain.getRequest()).getRemoteHost();
        assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);
    }

    @Test
    public void testInvokeAllowedRemoteAddrWithNullRemoteIpHeader() throws Exception {
        // PREPARE
        XForwardedFilter xforwardedFilter = new XForwardedFilter();
        MockFilterConfig filterConfig = new MockFilterConfig();
        filterConfig.addInitParameter(XForwardedFilter.INTERNAL_PROXIES_PARAMETER, "192\\.168\\.0\\.10, 192\\.168\\.0\\.11");
        filterConfig.addInitParameter(XForwardedFilter.TRUSTED_PROXIES_PARAMETER, "proxy1, proxy2, proxy3");
        filterConfig.addInitParameter(XForwardedFilter.REMOTE_IP_HEADER_PARAMETER, "x-forwarded-for");
        filterConfig.addInitParameter(XForwardedFilter.PROXIES_HEADER_PARAMETER, "x-forwarded-by");
        
        xforwardedFilter.init(filterConfig);
        MockFilterChain filterChain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        
        // TEST
        xforwardedFilter.doFilter(request, new MockHttpServletResponse(), filterChain);
        
        // VERIFY
        String actualXForwardedFor = request.getHeader("x-forwarded-for");
        assertNull("x-forwarded-for must be null", actualXForwardedFor);
        
        String actualXForwardedBy = request.getHeader("x-forwarded-by");
        assertNull("x-forwarded-by must be null", actualXForwardedBy);
        
        String actualRemoteAddr = filterChain.getRequest().getRemoteAddr();
        assertEquals("remoteAddr", "192.168.0.10", actualRemoteAddr);
        
        String actualRemoteHost = filterChain.getRequest().getRemoteHost();
        assertEquals("remoteHost", "remote-host-original-value", actualRemoteHost);
        
    }
    
    @Test
    public void testInvokeAllProxiesAreInternal() throws Exception {
        
        // PREPARE
        XForwardedFilter xforwardedFilter = new XForwardedFilter();
        MockFilterConfig filterConfig = new MockFilterConfig();
        filterConfig.addInitParameter(XForwardedFilter.INTERNAL_PROXIES_PARAMETER, "192\\.168\\.0\\.10, 192\\.168\\.0\\.11");
        filterConfig.addInitParameter(XForwardedFilter.TRUSTED_PROXIES_PARAMETER, "proxy1, proxy2, proxy3");
        filterConfig.addInitParameter(XForwardedFilter.REMOTE_IP_HEADER_PARAMETER, "x-forwarded-for");
        filterConfig.addInitParameter(XForwardedFilter.PROXIES_HEADER_PARAMETER, "x-forwarded-by");
        
        xforwardedFilter.init(filterConfig);
        MockFilterChain filterChain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        request.addHeader("x-forwarded-for", "140.211.11.130, 192.168.0.10, 192.168.0.11");
        
        // TEST
        xforwardedFilter.doFilter(request, new MockHttpServletResponse(), filterChain);
        
        // VERIFY
        String actualXForwardedFor = ((HttpServletRequest)filterChain.getRequest()).getHeader("x-forwarded-for");
        assertNull("all proxies are internal, x-forwarded-for must be null", actualXForwardedFor);
        
        String actualXForwardedBy = ((HttpServletRequest)filterChain.getRequest()).getHeader("x-forwarded-by");
        assertNull("all proxies are internal, x-forwarded-by must be null", actualXForwardedBy);
        
        String actualRemoteAddr = ((HttpServletRequest)filterChain.getRequest()).getRemoteAddr();
        assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);
        
        String actualRemoteHost = ((HttpServletRequest)filterChain.getRequest()).getRemoteHost();
        assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);
    }
    
    @Test
    public void testInvokeAllProxiesAreTrusted() throws Exception {
        
        // PREPARE
        XForwardedFilter xforwardedFilter = new XForwardedFilter();
        MockFilterConfig filterConfig = new MockFilterConfig();
        filterConfig.addInitParameter(XForwardedFilter.INTERNAL_PROXIES_PARAMETER, "192\\.168\\.0\\.10, 192\\.168\\.0\\.11");
        filterConfig.addInitParameter(XForwardedFilter.TRUSTED_PROXIES_PARAMETER, "proxy1, proxy2, proxy3");
        filterConfig.addInitParameter(XForwardedFilter.REMOTE_IP_HEADER_PARAMETER, "x-forwarded-for");
        filterConfig.addInitParameter(XForwardedFilter.PROXIES_HEADER_PARAMETER, "x-forwarded-by");
        
        xforwardedFilter.init(filterConfig);
        MockFilterChain filterChain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        request.addHeader("x-forwarded-for", "140.211.11.130, proxy1, proxy2");
        
        // TEST
        xforwardedFilter.doFilter(request, new MockHttpServletResponse(), filterChain);
        
        // VERIFY
        String actualXForwardedFor = ((HttpServletRequest)filterChain.getRequest()).getHeader("x-forwarded-for");
        assertNull("all proxies are trusted, x-forwarded-for must be null", actualXForwardedFor);
        
        String actualXForwardedBy = ((HttpServletRequest)filterChain.getRequest()).getHeader("x-forwarded-by");
        assertEquals("all proxies are trusted, they must appear in x-forwarded-by", "proxy1, proxy2", actualXForwardedBy);
        
        String actualRemoteAddr = ((HttpServletRequest)filterChain.getRequest()).getRemoteAddr();
        assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);
        
        String actualRemoteHost = ((HttpServletRequest)filterChain.getRequest()).getRemoteHost();
        assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);
    }
    
    @Test
    public void testInvokeAllProxiesAreTrustedAndRemoteAddrMatchRegexp() throws Exception {
        
        // PREPARE
        XForwardedFilter xforwardedFilter = new XForwardedFilter();
        MockFilterConfig filterConfig = new MockFilterConfig();
        filterConfig.addInitParameter(XForwardedFilter.INTERNAL_PROXIES_PARAMETER,
                                      "127\\.0\\.0\\.1, 192\\.168\\..*, another-internal-proxy");
        filterConfig.addInitParameter(XForwardedFilter.TRUSTED_PROXIES_PARAMETER, "proxy1, proxy2, proxy3");
        filterConfig.addInitParameter(XForwardedFilter.REMOTE_IP_HEADER_PARAMETER, "x-forwarded-for");
        filterConfig.addInitParameter(XForwardedFilter.PROXIES_HEADER_PARAMETER, "x-forwarded-by");
        
        xforwardedFilter.init(filterConfig);
        MockFilterChain filterChain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        request.addHeader("x-forwarded-for", "140.211.11.130, proxy1, proxy2");
        
        // TEST
        xforwardedFilter.doFilter(request, new MockHttpServletResponse(), filterChain);
        
        // VERIFY
        String actualXForwardedFor = ((HttpServletRequest)filterChain.getRequest()).getHeader("x-forwarded-for");
        assertNull("all proxies are trusted, x-forwarded-for must be null", actualXForwardedFor);
        
        String actualXForwardedBy = ((HttpServletRequest)filterChain.getRequest()).getHeader("x-forwarded-by");
        assertEquals("all proxies are trusted, they must appear in x-forwarded-by", "proxy1, proxy2", actualXForwardedBy);
        
        String actualRemoteAddr = ((HttpServletRequest)filterChain.getRequest()).getRemoteAddr();
        assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);
        
        String actualRemoteHost = ((HttpServletRequest)filterChain.getRequest()).getRemoteHost();
        assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);
    }
    
    @Test
    public void testInvokeAllProxiesAreTrustedOrInternal() throws Exception {
        
        // PREPARE
        XForwardedFilter xforwardedFilter = new XForwardedFilter();
        MockFilterConfig filterConfig = new MockFilterConfig();
        filterConfig.addInitParameter(XForwardedFilter.INTERNAL_PROXIES_PARAMETER, "192\\.168\\.0\\.10, 192\\.168\\.0\\.11");
        filterConfig.addInitParameter(XForwardedFilter.TRUSTED_PROXIES_PARAMETER, "proxy1, proxy2, proxy3");
        filterConfig.addInitParameter(XForwardedFilter.REMOTE_IP_HEADER_PARAMETER, "x-forwarded-for");
        filterConfig.addInitParameter(XForwardedFilter.PROXIES_HEADER_PARAMETER, "x-forwarded-by");
        
        xforwardedFilter.init(filterConfig);
        MockFilterChain filterChain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        request.addHeader("x-forwarded-for", "140.211.11.130, proxy1, proxy2, 192.168.0.10, 192.168.0.11");
        
        // TEST
        xforwardedFilter.doFilter(request, new MockHttpServletResponse(), filterChain);
        
        // VERIFY
        String actualXForwardedFor = ((HttpServletRequest)filterChain.getRequest()).getHeader("x-forwarded-for");
        assertNull("all proxies are trusted, x-forwarded-for must be null", actualXForwardedFor);
        
        String actualXForwardedBy = ((HttpServletRequest)filterChain.getRequest()).getHeader("x-forwarded-by");
        assertEquals("all proxies are trusted, they must appear in x-forwarded-by", "proxy1, proxy2", actualXForwardedBy);
        
        String actualRemoteAddr = ((HttpServletRequest)filterChain.getRequest()).getRemoteAddr();
        assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);
        
        String actualRemoteHost = ((HttpServletRequest)filterChain.getRequest()).getRemoteHost();
        assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);
    }
    
    @Test
    public void testInvokeNotAllowedRemoteAddr() throws Exception {
        // PREPARE
        XForwardedFilter xforwardedFilter = new XForwardedFilter();
        MockFilterConfig filterConfig = new MockFilterConfig();
        filterConfig.addInitParameter(XForwardedFilter.INTERNAL_PROXIES_PARAMETER, "192\\.168\\.0\\.10, 192\\.168\\.0\\.11");
        filterConfig.addInitParameter(XForwardedFilter.TRUSTED_PROXIES_PARAMETER, "proxy1, proxy2, proxy3");
        filterConfig.addInitParameter(XForwardedFilter.REMOTE_IP_HEADER_PARAMETER, "x-forwarded-for");
        filterConfig.addInitParameter(XForwardedFilter.PROXIES_HEADER_PARAMETER, "x-forwarded-by");
        
        xforwardedFilter.init(filterConfig);
        MockFilterChain filterChain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        request.setRemoteAddr("not-allowed-internal-proxy");
        request.setRemoteHost("not-allowed-internal-proxy-host");
        request.addHeader("x-forwarded-for", "140.211.11.130, proxy1, proxy2");
        
        // TEST
        xforwardedFilter.doFilter(request, new MockHttpServletResponse(), filterChain);
        
        // VERIFY
        String actualXForwardedFor = ((HttpServletRequest)filterChain.getRequest()).getHeader("x-forwarded-for");
        assertEquals("x-forwarded-for must be unchanged", "140.211.11.130, proxy1, proxy2", actualXForwardedFor);
        
        String actualXForwardedBy = ((HttpServletRequest)filterChain.getRequest()).getHeader("x-forwarded-by");
        assertNull("x-forwarded-by must be null", actualXForwardedBy);
        
        String actualRemoteAddr = ((HttpServletRequest)filterChain.getRequest()).getRemoteAddr();
        assertEquals("remoteAddr", "not-allowed-internal-proxy", actualRemoteAddr);
        
        String actualRemoteHost = ((HttpServletRequest)filterChain.getRequest()).getRemoteHost();
        assertEquals("remoteHost", "not-allowed-internal-proxy-host", actualRemoteHost);
    }
        
    @Test
    public void testInvokeUntrustedProxyInTheChain() throws Exception {
        // PREPARE
        XForwardedFilter xforwardedFilter = new XForwardedFilter();
        MockFilterConfig filterConfig = new MockFilterConfig();
        filterConfig.addInitParameter(XForwardedFilter.INTERNAL_PROXIES_PARAMETER, "192\\.168\\.0\\.10, 192\\.168\\.0\\.11");
        filterConfig.addInitParameter(XForwardedFilter.TRUSTED_PROXIES_PARAMETER, "proxy1, proxy2, proxy3");
        filterConfig.addInitParameter(XForwardedFilter.REMOTE_IP_HEADER_PARAMETER, "x-forwarded-for");
        filterConfig.addInitParameter(XForwardedFilter.PROXIES_HEADER_PARAMETER, "x-forwarded-by");
        
        xforwardedFilter.init(filterConfig);
        MockFilterChain filterChain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        request.addHeader("x-forwarded-for", "140.211.11.130, proxy1, untrusted-proxy, proxy2");
        
        // TEST
        xforwardedFilter.doFilter(request, new MockHttpServletResponse(), filterChain);
        
        // VERIFY
        String actualXForwardedFor = ((HttpServletRequest)filterChain.getRequest()).getHeader("x-forwarded-for");
        assertEquals("ip/host before untrusted-proxy must appear in x-forwarded-for", "140.211.11.130, proxy1", actualXForwardedFor);
        
        String actualXForwardedBy = ((HttpServletRequest)filterChain.getRequest()).getHeader("x-forwarded-by");
        assertEquals("ip/host after untrusted-proxy must appear in  x-forwarded-by", "proxy2", actualXForwardedBy);
        
        String actualRemoteAddr = ((HttpServletRequest)filterChain.getRequest()).getRemoteAddr();
        assertEquals("remoteAddr", "untrusted-proxy", actualRemoteAddr);
        
        String actualRemoteHost = ((HttpServletRequest)filterChain.getRequest()).getRemoteHost();
        assertEquals("remoteHost", "untrusted-proxy", actualRemoteHost);
    }
    
    @Test
    public void testListToCommaDelimitedString() {
        String[] actual = XForwardedFilter.commaDelimitedListToStringArray("element1, element2, element3");
        String[] expected = new String[] {
            "element1", "element2", "element3"
        };
        assertArrayEquals(expected, actual);
    }
    
    @Test
    public void testListToCommaDelimitedStringMixedSpaceChars() {
        String[] actual = XForwardedFilter.commaDelimitedListToStringArray("element1  , element2,\t element3");
        String[] expected = new String[] {
            "element1", "element2", "element3"
        };
        assertArrayEquals(expected, actual);
    }
    
    /**
     * Test {@link XForwardedFilter} in Jetty
     */
    @Test
    public void testWithJetty() throws Exception {
        
        // SETUP
        int port = 6666;
        Server server = new Server(port);
        Context context = new Context(server, "/", Context.SESSIONS);
        
        // mostly default configuration : enable "x-forwarded-proto"
        XForwardedFilter xforwardedFilter = new XForwardedFilter();
        MockFilterConfig filterConfig = new MockFilterConfig();
        filterConfig.addInitParameter(XForwardedFilter.PROTOCOL_HEADER_PARAMETER, "x-forwarded-proto");
        xforwardedFilter.init(filterConfig);
        context.addFilter(new FilterHolder(xforwardedFilter), "/*", Handler.REQUEST);
        
        MockHttpServlet mockServlet = new MockHttpServlet();
        context.addServlet(new ServletHolder(mockServlet), "/test");
        
        server.start();
        try {
            // TEST
            HttpURLConnection httpURLConnection = (HttpURLConnection)new URL("http://localhost:" + port + "/test").openConnection();
            String expectedRemoteAddr = "my-remote-addr";
            httpURLConnection.addRequestProperty("x-forwarded-for", expectedRemoteAddr);
            httpURLConnection.addRequestProperty("x-forwarded-proto", "https");
            
            // VALIDATE
            
            Assert.assertEquals(HttpURLConnection.HTTP_OK, httpURLConnection.getResponseCode());
            HttpServletRequest request = mockServlet.getRequest();
            Assert.assertNotNull(request);
            
            // VALIDATE X-FOWARDED-FOR
            Assert.assertEquals(expectedRemoteAddr, request.getRemoteAddr());
            Assert.assertEquals(expectedRemoteAddr, request.getRemoteHost());
            
            // VALIDATE X-FORWARDED-PROTO
            Assert.assertTrue(request.isSecure());
            Assert.assertEquals("https", request.getScheme());
            Assert.assertEquals(443, request.getServerPort());
        } finally {
            server.stop();
        }
    }
}
