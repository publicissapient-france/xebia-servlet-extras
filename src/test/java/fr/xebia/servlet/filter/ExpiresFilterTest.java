/*
 * Copyright 2008-2010 Xebia and the original author or authors.
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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Map.Entry;

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
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.util.StringUtils;

import fr.xebia.servlet.filter.ExpiresFilter.Duration;
import fr.xebia.servlet.filter.ExpiresFilter.DurationUnit;
import fr.xebia.servlet.filter.ExpiresFilter.ExpiresConfiguration;
import fr.xebia.servlet.filter.ExpiresFilter.StartingPoint;

public class ExpiresFilterTest {

    @Test
    public void testUseDefaultConfiguration1() throws Exception {
        HttpServlet servlet = new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
                response.setContentType("image/jpeg");
                response.getWriter().print("Hello world");
            }
        };

        int expectedMaxAgeInSeconds = 1 * 60;

        validate(servlet, expectedMaxAgeInSeconds);
    }

    @Test
    public void testUseDefaultConfiguration2() throws Exception {
        HttpServlet servlet = new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
                response.setContentType("image/jpeg");
                response.addHeader("Cache-Control", "private");

                response.getWriter().print("Hello world");
            }
        };

        int expectedMaxAgeInSeconds = 1 * 60;

        validate(servlet, expectedMaxAgeInSeconds);
    }

    @Test
    public void testSkipBecauseExpiresIsDefined() throws Exception {
        HttpServlet servlet = new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
                response.setContentType("text/xml; charset=utf-8");
                response.addDateHeader("Expires", System.currentTimeMillis());
                response.getWriter().print("Hello world");
            }
        };

        validate(servlet, null);
    }

    @Test
    public void testSkipBecauseCacheControlMaxAgeIsDefined() throws Exception {
        HttpServlet servlet = new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
                response.setContentType("text/xml; charset=utf-8");
                response.addHeader("Cache-Control", "private, max-age=232");
                response.getWriter().print("Hello world");
            }
        };

        int expectedMaxAgeInSeconds = 232;
        validate(servlet, expectedMaxAgeInSeconds);
    }

    @Test
    public void testUseMajorTypeExpiresConfiguration() throws Exception {
        HttpServlet servlet = new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
                response.setContentType("text/json; charset=iso-8859-1");
                response.getWriter().print("Hello world");
            }
        };

        int expectedMaxAgeInSeconds = 7 * 60;

        validate(servlet, expectedMaxAgeInSeconds);
    }

    @Test
    public void testUseContentTypeWithoutCharsetExpiresConfiguration() throws Exception {
        HttpServlet servlet = new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
                response.setContentType("text/xml; charset=iso-8859-1");
                response.getWriter().print("Hello world");
            }
        };

        int expectedMaxAgeInSeconds = 5 * 60;

        validate(servlet, expectedMaxAgeInSeconds);
    }

    @Test
    public void testUseContentTypeExpiresConfiguration() throws Exception {
        HttpServlet servlet = new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
                response.setContentType("text/xml; charset=utf-8");
                response.getWriter().print("Hello world");
            }
        };

        int expectedMaxAgeInSeconds = 3 * 60;

        validate(servlet, expectedMaxAgeInSeconds);
    }

    @Test
    public void testParseExpiresConfigurationMonoDuration() {
        ExpiresFilter expiresFilter = new ExpiresFilter();
        ExpiresConfiguration actualConfiguration = expiresFilter
                .parseExpiresConfiguration("text/html \"access plus 2 hours\"");

        Assert.assertEquals("text/html", actualConfiguration.getContentType());
        Assert.assertEquals(StartingPoint.ACCESS_TIME, actualConfiguration.getStartingPoint());

        Assert.assertEquals(1, actualConfiguration.getDurations().size());
        Assert.assertEquals(2, actualConfiguration.getDurations().get(0).getAmount());
        Assert.assertEquals(DurationUnit.HOUR, actualConfiguration.getDurations().get(0).getUnit());

    }
    @Test
    public void testParseExpiresConfigurationCombinedDuration() {
        ExpiresFilter expiresFilter = new ExpiresFilter();
        ExpiresConfiguration actualConfiguration = expiresFilter
                .parseExpiresConfiguration("text/html \"access plus 1 month 15 days 2 hours\"");

        Assert.assertEquals("text/html", actualConfiguration.getContentType());
        Assert.assertEquals(StartingPoint.ACCESS_TIME, actualConfiguration.getStartingPoint());

        Assert.assertEquals(3, actualConfiguration.getDurations().size());

    }

    protected void validate(HttpServlet servlet, Integer expectedMaxAgeInSeconds) throws ServletException, Exception, IOException,
            MalformedURLException {
        // SETUP
        int port = 6666;
        Server server = new Server(port);
        Context context = new Context(server, "/", Context.SESSIONS);

        ExpiresFilter expiresFilter = new ExpiresFilter();
        expiresFilter.defaultExpiresConfiguration = new ExpiresConfiguration("#DEFAULT#", StartingPoint.ACCESS_TIME, Duration.minutes(1));

        expiresFilter.expiresConfigurationByContentType.put("text/xml; charset=utf-8", new ExpiresConfiguration("text/xml; charset=utf-8",
                StartingPoint.ACCESS_TIME, Duration.minutes(3)));

        expiresFilter.expiresConfigurationByContentType.put("text/xml", new ExpiresConfiguration("text/xml", StartingPoint.ACCESS_TIME,
                Duration.minutes(5)));

        expiresFilter.expiresConfigurationByContentType.put("text", new ExpiresConfiguration("text/xml", StartingPoint.ACCESS_TIME,
                Duration.minutes(7)));

        MockFilterConfig filterConfig = new MockFilterConfig();
        expiresFilter.init(filterConfig);
        context.addFilter(new FilterHolder(expiresFilter), "/*", Handler.REQUEST);

        context.addServlet(new ServletHolder(servlet), "/test");

        server.start();
        try {
            Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            long timeBeforeInMillis = System.currentTimeMillis();

            // TEST
            HttpURLConnection httpURLConnection = (HttpURLConnection) new URL("http://localhost:" + port + "/test").openConnection();

            // VALIDATE
            Assert.assertEquals(HttpURLConnection.HTTP_OK, httpURLConnection.getResponseCode());

            for (Entry<String, List<String>> field : httpURLConnection.getHeaderFields().entrySet()) {
                System.out.println(field.getKey() + ": " + StringUtils.arrayToDelimitedString(field.getValue().toArray(), ", "));
            }

            Integer actualMaxAgeInSeconds;

            String cacheControlHeader = httpURLConnection.getHeaderField("Cache-Control");
            if (cacheControlHeader == null) {
                actualMaxAgeInSeconds = null;
            } else {
                actualMaxAgeInSeconds = null;
                System.out.println("Evaluate Cache-Control:" + cacheControlHeader);
                StringTokenizer cacheControlTokenizer = new StringTokenizer(cacheControlHeader, ",");
                while (cacheControlTokenizer.hasMoreTokens() && actualMaxAgeInSeconds == null) {
                    String cacheDirective = cacheControlTokenizer.nextToken();
                    System.out.println("\tEvaluate directive: " + cacheDirective);
                    StringTokenizer cacheDirectiveTokenizer = new StringTokenizer(cacheDirective, "=");
                    System.out.println("countTokens=" + cacheDirectiveTokenizer.countTokens());
                    if (cacheDirectiveTokenizer.countTokens() == 2) {
                        String key = cacheDirectiveTokenizer.nextToken().trim();
                        String value = cacheDirectiveTokenizer.nextToken().trim();
                        if (key.equalsIgnoreCase("max-age")) {
                            actualMaxAgeInSeconds = Integer.parseInt(value);
                        }
                    }
                }
            }

            if (expectedMaxAgeInSeconds == null) {
                Assert.assertNull("actualMaxAgeInSeconds '" + actualMaxAgeInSeconds + "' should be null", actualMaxAgeInSeconds);
                return;
            }

            Assert.assertNotNull(actualMaxAgeInSeconds);

            int deltaInSeconds = Math.abs(actualMaxAgeInSeconds - expectedMaxAgeInSeconds);
            Assert.assertTrue("actualMaxAgeInSeconds: " + actualMaxAgeInSeconds + ", expectedMaxAgeInSeconds: " + expectedMaxAgeInSeconds
                    + ", request time: " + timeBeforeInMillis + " for content type " + httpURLConnection.getContentType(),
                    deltaInSeconds < 2000);

        } finally {
            server.stop();
        }
    }
}
