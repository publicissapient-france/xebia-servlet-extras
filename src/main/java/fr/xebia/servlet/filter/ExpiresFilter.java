/*
 * Copyright 2008-2009 the original author or authors.
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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Port of <a
 * href="http://httpd.apache.org/docs/2.2/mod/mod_expires.html">Apache
 * mod_expires</a>.
 * 
 * <p>
 * Note : "Cache-Control" header is modified to add the "max-age" directive
 * instead of adding a second "Cache-Control" header because mod_expires works
 * like this. It makes senses as the <a
 * href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.9">RFC
 * 1616 - Hypertext Transfer Protocol -- HTTP/1.1, Cache-Control chapter</a>
 * does not state that the "Cache-Control" header can appear several times to
 * hold several directives.
 * </p>
 * 
 * @author <a href="mailto:cyrille@cyrilleleclerc.com">Cyrille Le Clerc</a>
 */
public class ExpiresFilter implements Filter {

    protected static class Duration {

        public DurationUnit getUnit() {
            return unit;
        }

        final protected int amout;

        final protected DurationUnit unit;

        public Duration(int amout, DurationUnit unit) {
            super();
            this.amout = amout;
            this.unit = unit;
        }

        public int getAmout() {
            return amout;
        }
    }

    protected enum DurationUnit {
        DAY(Calendar.DAY_OF_YEAR), MINUTE(Calendar.MINUTE), MONTH(Calendar.MONTH), SECOND(Calendar.SECOND), WEEK(Calendar.WEEK_OF_YEAR), YEAR(
                Calendar.YEAR);
        public int getCalendardField() {
            return calendardField;
        }

        private final int calendardField;

        private DurationUnit(int calendardField) {
            this.calendardField = calendardField;
        }

    }

    protected static class ExpiresConfiguration {
        private List<Duration> durations;

        private StartingPoint startingPoint;

        private ExpiresConfiguration(StartingPoint startingPoint, List<Duration> durations) {
            super();
            this.startingPoint = startingPoint;
            this.durations = durations;
        }

        public List<Duration> getDurations() {
            return durations;
        }

        public StartingPoint getStartingPoint() {
            return startingPoint;
        }
    }

    /**
     * Data structure for an Http header. It is basically a key-value pair.
     */
    protected static class Header {
        /**
         * Immutable name of the http header
         */
        final private String name;

        /**
         * Mutable value of the header. It can be a {@link String}, a
         * {@link Long} to hold a date or an {@link Integer}.
         */
        private Object value;

        public Header(String name, Object value) {
            super();
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }
    }

    /**
     * Expiration configuration starting point. Either the time the
     * html-page/servlet-response was served ({@link StartingPoint#ACCESS_TIME})
     * or the last time the html-page/servlet-response was modified (
     * {@link StartingPoint#LAST_MODIFICATION_TIME}).
     */
    protected enum StartingPoint {
        ACCESS_TIME, LAST_MODIFICATION_TIME
    }

    /**
     * <p>
     * Wrapping extension of the {@link HttpServletResponse} to :
     * <ul>
     * <li>Trap the "Start Write Response Body" event.</li>
     * <li>Have read access to http headers instead of just being able to know
     * if a header with a given name has been set (
     * {@link HttpServletResponse#containsHeader(String)}).</li>
     * <li>Differ http headers writing in the response stream to be able to
     * modify them.</li>
     * </ul>
     */
    public class XHttpServletResponse extends HttpServletResponseWrapper {

        private List<Header> headers = new ArrayList<Header>();

        private boolean headersAlreadyFlushed;

        private PrintWriter printWriter;

        private HttpServletRequest request;

        private ServletOutputStream servletOutputStream;

        public XHttpServletResponse(HttpServletRequest request, HttpServletResponse response) {
            super(response);
            this.request = request;
        }

        @Override
        public void addDateHeader(String name, long date) {
            if (headersAlreadyFlushed) {
                super.addDateHeader(name, date);
            } else {
                headers.add(new Header(name, date));
            }
        }

        @Override
        public void addHeader(String name, String value) {
            if (headersAlreadyFlushed) {
                super.addHeader(name, value);
            } else {
                headers.add(new Header(name, value));
            }
        }

        @Override
        public void addIntHeader(String name, int value) {
            if (headersAlreadyFlushed) {
                super.addIntHeader(name, value);
            } else {
                headers.add(new Header(name, value));
            }
        }

        @Override
        public boolean containsHeader(String name) {
            if (headersAlreadyFlushed) {
                return super.containsHeader(name);
            } else {
                return getHeader(name) != null;
            }
        }

        protected void flushHeaders() {
            for (Header header : headers) {
                Object value = header.getValue();
                String name = header.getName();
                logger.trace("flush {}: {}", name, value);
                if (value instanceof Integer) {
                    super.addIntHeader(name, ((Integer) value).intValue());
                } else if (value instanceof Long) {
                    super.addDateHeader(name, ((Long) value).longValue());
                } else if (value instanceof String) {
                    super.addHeader(name, (String) value);
                } else {
                    throw new IllegalStateException("unsupported value type " + header.getValue());
                }
            }
            headers = Collections.emptyList();
            headersAlreadyFlushed = true;
        }

        protected Header getHeader(String name) {
            for (Header header : headers) {
                if (header.getName().equalsIgnoreCase(name)) {
                    return header;
                }
            }
            return null;
        }

        protected String getHeaderValue(String name) {
            Header header = getHeader(name);
            return header == null ? null : header.getValue().toString();
        }

        protected Long getDateHeader(String name) {
            Header header = getHeader(name);
            if (header == null) {
                return null;
            } else if (header.getValue() instanceof Long) {
                return (Long) header.getValue();
            } else {
                return null;
            }
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (servletOutputStream == null) {
                servletOutputStream = new XServletOutputStream(super.getOutputStream(), request, this);
            }
            return servletOutputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (printWriter == null) {
                printWriter = new XPrintWriter(super.getWriter(), request, this);
            }
            return printWriter;
        }

        @Override
        public void setDateHeader(String name, long date) {
            if (headersAlreadyFlushed) {
                super.setDateHeader(name, date);
            } else {
                Header header = getHeader(name);
                if (header == null) {
                    addDateHeader(name, date);
                } else {
                    header.setValue(date);
                }
            }
        }

        @Override
        public void setHeader(String name, String value) {
            if (headersAlreadyFlushed) {
                super.setHeader(name, value);
            } else {
                Header header = getHeader(name);
                if (header == null) {
                    addHeader(name, value);
                } else {
                    header.setValue(value);
                }
            }
        }

        @Override
        public void setIntHeader(String name, int value) {
            if (headersAlreadyFlushed) {
                super.setIntHeader(name, value);
            } else {
                Header header = getHeader(name);
                if (header == null) {
                    addIntHeader(name, value);
                } else {
                    header.setValue(value);
                }
            }
        }

    }

    /**
     * Wrapping extension of {@link PrintWriter} to trap the
     * "Start Write Response Body" event.
     */
    public class XPrintWriter extends PrintWriter {
        private PrintWriter out;

        private HttpServletRequest request;

        private XHttpServletResponse response;

        private boolean writeStarted;

        public XPrintWriter(PrintWriter out, HttpServletRequest request, XHttpServletResponse response) {
            super(out);
            this.out = out;
            this.request = request;
            this.response = response;
        }

        public PrintWriter append(char c) {
            fireWriteStartEvent();
            return out.append(c);
        }

        public PrintWriter append(CharSequence csq) {
            fireWriteStartEvent();
            return out.append(csq);
        }

        public PrintWriter append(CharSequence csq, int start, int end) {
            fireWriteStartEvent();
            return out.append(csq, start, end);
        }

        public void close() {
            fireWriteStartEvent();
            out.close();
        }

        private void fireWriteStartEvent() {
            if (!writeStarted) {
                writeStarted = true;
                onStartWriteResponseBody(request, response);
            }
        }

        public void flush() {
            fireWriteStartEvent();
            out.flush();
        }

        public void print(boolean b) {
            fireWriteStartEvent();
            out.print(b);
        }

        public void print(char c) {
            fireWriteStartEvent();
            out.print(c);
        }

        public void print(char[] s) {
            fireWriteStartEvent();
            out.print(s);
        }

        public void print(double d) {
            fireWriteStartEvent();
            out.print(d);
        }

        public void print(float f) {
            fireWriteStartEvent();
            out.print(f);
        }

        public void print(int i) {
            fireWriteStartEvent();
            out.print(i);
        }

        public void print(long l) {
            fireWriteStartEvent();
            out.print(l);
        }

        public void print(Object obj) {
            fireWriteStartEvent();
            out.print(obj);
        }

        public void print(String s) {
            fireWriteStartEvent();
            out.print(s);
        }

        public PrintWriter printf(Locale l, String format, Object... args) {
            fireWriteStartEvent();
            return out.printf(l, format, args);
        }

        public PrintWriter printf(String format, Object... args) {
            fireWriteStartEvent();
            return out.printf(format, args);
        }

        public void println() {
            fireWriteStartEvent();
            out.println();
        }

        public void println(boolean x) {
            fireWriteStartEvent();
            out.println(x);
        }

        public void println(char x) {
            fireWriteStartEvent();
            out.println(x);
        }

        public void println(char[] x) {
            fireWriteStartEvent();
            out.println(x);
        }

        public void println(double x) {
            fireWriteStartEvent();
            out.println(x);
        }

        public void println(float x) {
            fireWriteStartEvent();
            out.println(x);
        }

        public void println(int x) {
            fireWriteStartEvent();
            out.println(x);
        }

        public void println(long x) {
            fireWriteStartEvent();
            out.println(x);
        }

        public void println(Object x) {
            fireWriteStartEvent();
            out.println(x);
        }

        public void println(String x) {
            fireWriteStartEvent();
            out.println(x);
        }

        public void write(char[] buf) {
            fireWriteStartEvent();
            out.write(buf);
        }

        public void write(char[] buf, int off, int len) {
            fireWriteStartEvent();
            out.write(buf, off, len);
        }

        public void write(int c) {
            fireWriteStartEvent();
            out.write(c);
        }

        public void write(String s) {
            fireWriteStartEvent();
            out.write(s);
        }

        public void write(String s, int off, int len) {
            fireWriteStartEvent();
            out.write(s, off, len);
        }

    }

    /**
     * Wrapping extension of {@link ServletOutputStream} to trap the
     * "Start Write Response Body" event.
     */
    public class XServletOutputStream extends ServletOutputStream {

        private HttpServletRequest request;

        private XHttpServletResponse response;

        private ServletOutputStream servletOutputStream;

        private boolean writeStarted;

        public XServletOutputStream(ServletOutputStream servletOutputStream, HttpServletRequest request, XHttpServletResponse response) {
            super();
            this.servletOutputStream = servletOutputStream;
            this.response = response;
            this.request = request;
        }

        public void close() throws IOException {
            fireWriteStartEvent();
            servletOutputStream.close();
        }

        private void fireWriteStartEvent() {
            if (!writeStarted) {
                writeStarted = true;
                onStartWriteResponseBody(request, response);
            }
        }

        public void flush() throws IOException {
            fireWriteStartEvent();
            servletOutputStream.flush();
        }

        public void print(boolean b) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.print(b);
        }

        public void print(char c) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.print(c);
        }

        public void print(double d) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.print(d);
        }

        public void print(float f) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.print(f);
        }

        public void print(int i) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.print(i);
        }

        public void print(long l) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.print(l);
        }

        public void print(String s) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.print(s);
        }

        public void println() throws IOException {
            fireWriteStartEvent();
            servletOutputStream.println();
        }

        public void println(boolean b) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.println(b);
        }

        public void println(char c) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.println(c);
        }

        public void println(double d) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.println(d);
        }

        public void println(float f) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.println(f);
        }

        public void println(int i) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.println(i);
        }

        public void println(long l) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.println(l);
        }

        public void println(String s) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.println(s);
        }

        public void write(byte[] b) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.write(b);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.write(b, off, len);
        }

        public void write(int b) throws IOException {
            fireWriteStartEvent();
            servletOutputStream.write(b);
        }

    }

    private static final String HEADER_CACHE_CONTROL = "Cache-Control";

    private static final String HEADER_EXPIRES = "Expires";

    /**
     * Returns <code>true</code> if the given <code>str</code> contains the
     * given <code>searchStr</code>.
     */
    protected static boolean contains(String str, String searchStr) {
        if (str == null || searchStr == null) {
            return false;
        }
        return str.indexOf(searchStr) >= 0;
    }

    /**
     * Returns <code>true</code> if the given <code>str</code> is
     * <code>null</code> or has a zero characters length.
     */
    protected static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    /**
     * Returns <code>true</code> if the given <code>str</code> has at least one
     * character (can be a withespace).
     */
    protected static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    private final Logger logger = LoggerFactory.getLogger(ExpiresFilter.class);

    public void destroy() {

    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            chain.doFilter(request, new XHttpServletResponse((HttpServletRequest) request, (HttpServletResponse) response));
        } else {
            chain.doFilter(request, response);
        }
    };

    private Map<String, ExpiresConfiguration> expiresConfigurationByContentType;

    private ExpiresConfiguration defaultExpiresConfiguration;

    /**
     * Returns the expiration date of the given {@link XHttpServletResponse} or
     * <code>null</code> if no expiration date has been configured for the
     * declared content type.
     * 
     * @see HttpServletResponse#getContentType()
     */
    private Date getExpirationDate(XHttpServletResponse response) {
        ExpiresConfiguration configuration = expiresConfigurationByContentType.get(response.getContentType());

        if (configuration == null) {
            configuration = defaultExpiresConfiguration;
            logger.trace("No Expires configuration found for content type {}, use default ", response.getContentType(), configuration);
        }
        if (configuration == null) {
            return null;
        }
        Calendar calendar;
        switch (configuration.getStartingPoint()) {
        case ACCESS_TIME:
            calendar = GregorianCalendar.getInstance();
            break;
        case LAST_MODIFICATION_TIME:
            calendar = GregorianCalendar.getInstance();
            Long lastModified = response.getDateHeader("Last-Modified");
            if (lastModified == null) {
                // Last-Modified header not found, use now
            } else {
                calendar.setTimeInMillis(lastModified);
            }
            break;
        default:
            throw new IllegalStateException();
        }
        for (Duration duration : configuration.getDurations()) {
            calendar.add(duration.getUnit().getCalendardField(), duration.getAmout());
        }

        return calendar.getTime();
    }

    public void init(FilterConfig filterConfig) throws ServletException {
        // TODO : load configuration

    }

    /**
     * <p>
     * If no expiration header has been set by the servlet and an expiration has
     * been defined in the {@link ExpiresFilter} configuration, sets the
     * "Expires header" and the attribute "max-age" of the "Cache-Control"
     * header.
     * </p>
     * <p>
     * Must be called on the "Start Write Response Body" event.
     * </p>
     */
    public void onStartWriteResponseBody(HttpServletRequest request, XHttpServletResponse response) {
        String cacheControlHeader = response.getHeaderValue(HEADER_CACHE_CONTROL);
        String expiresHeader = response.getHeaderValue(HEADER_EXPIRES);
        boolean expirationHeaderExists = isNotEmpty(expiresHeader) || contains(cacheControlHeader, "max-age");
        if (expirationHeaderExists) {
            logger.debug("Expiration header already defined for request {}", request.getRequestURI());
        } else {
            String contentType = response.getContentType();
            Date expirationDate = getExpirationDate(response);
            if (expirationDate == null) {
                logger.debug("No expiration date configured for request {} content type '{}'", request.getRequestURI(), contentType);
            } else {
                logger.info("Set expiration date {} for request {} contentType'{}'", new Object[] { expirationDate,
                        request.getRequestURI(), contentType });

                String maxAgeDirective = "max-age=" + ((expirationDate.getTime() - System.currentTimeMillis()) / 1000);
                if (isEmpty(cacheControlHeader)) {
                    response.addHeader(HEADER_CACHE_CONTROL, maxAgeDirective);
                } else {
                    response.setHeader(HEADER_CACHE_CONTROL, cacheControlHeader + ", " + maxAgeDirective);
                }
                response.addDateHeader(HEADER_EXPIRES, expirationDate.getTime());
            }
        }

        // Flush headers
        response.flushHeaders();
    }
}
