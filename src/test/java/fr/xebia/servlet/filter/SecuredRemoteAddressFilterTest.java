/*
 * Copyright 2008-2009 Xebia and the original author or authors.
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

import static junit.framework.Assert.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class SecuredRemoteAddressFilterTest {
    
    private void testRemoteAddr(String remoteAddr, boolean expected) throws ServletException, IOException {
        SecuredRemoteAddressFilter filter = new SecuredRemoteAddressFilter();
        MockFilterConfig filterConfig = new MockFilterConfig();
        filter.init(filterConfig);
        final AtomicBoolean secured = new AtomicBoolean();
        MockFilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) {
                secured.set(request.isSecure());
            }
        };
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertEquals(expected, secured.get());
    }

    @Test
    public void testSecuredPrivateClassAAddress() throws Exception {
        testRemoteAddr("10.0.0.0", true);
        testRemoteAddr("10.0.0.1", true);
        testRemoteAddr("10.255.255.255", true);
    }

    @Test
    public void testSecuredPrivateClassBAddress() throws Exception {
        testRemoteAddr("172.16.0.0", true);
        testRemoteAddr("172.16.0.5", true);
        testRemoteAddr("172.31.255.255", true);
    }

    @Test
    public void testSecuredPrivateClassCAddress() throws Exception {
        testRemoteAddr("192.168.0.0", true);
        testRemoteAddr("192.168.0.5", true);
        testRemoteAddr("192.168.255.255", true);
    }

    @Test
    public void testUnsecuredAddress() throws Exception {
        testRemoteAddr("82.66.240.18", false);
    }
}
