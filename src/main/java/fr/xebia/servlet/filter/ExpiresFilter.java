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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

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
 * <p>
 * Port of <a
 * href="http://httpd.apache.org/docs/2.2/mod/mod_expires.html">Apache
 * mod_expires</a>. Following documentation is inspired by
 * <code>mod_expires</code> .
 * </p>
 * <p>
 * <strong>Description</strong>
 * </p>
 * <p>
 * Generation of <code>Expires</code> and <code>Cache-Control</code> HTTP
 * headers according to user-specified criteria
 * </p>
 * <p>
 * <strong>Summary</strong>
 * </p>
 * <p>
 * This module controls the setting of the <code>Expires</code> HTTP header and
 * the <code>max-age</code> directive of the <code>Cache-Control</code> HTTP
 * header in server responses. The expiration date can set to be relative to
 * either the time the source file was last modified, or to the time of the
 * client access.
 * </p>
 * 
 * <p>
 * These HTTP headers are an instruction to the client about the document's
 * validity and persistence. If cached, the document may be fetched from the
 * cache rather than from the source until this time has passed. After that, the
 * cache copy is considered "expired" and invalid, and a new copy must be
 * obtained from the source.
 * </p>
 * 
 * <p>
 * To modify <code>Cache-Control</code> directives other than
 * <code>max-age</code> (see <a
 * href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.9">RFC
 * 2616 section 14.9</a>), you can use the
 * <code class="directive"><a href="http://httpd.apache.org/docs/2.2/mod/mod_headers.html#header">Header</a></code>
 * directive for the <a
 * href="http://httpd.apache.org/docs/2.2/mod/mod_headers.html">Apache Httpd
 * mod_headers</a> module.
 * </p>
 * 
 * 
 * 
 * 
 * 
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

        public static Duration minutes(int amount) {
            return new Duration(amount, DurationUnit.MINUTE);
        }

        public static Duration seconds(int amount) {
            return new Duration(amount, DurationUnit.SECOND);
        }

        final protected int amount;

        final protected DurationUnit unit;

        public Duration(int amount, DurationUnit unit) {
            super();
            this.amount = amount;
            this.unit = unit;
        }

        public int getAmount() {
            return amount;
        }

        public DurationUnit getUnit() {
            return unit;
        }

        @Override
        public String toString() {
            return amount + " " + unit;
        }
    }

    protected enum DurationUnit {
        DAY(Calendar.DAY_OF_YEAR), HOUR(Calendar.HOUR), MINUTE(Calendar.MINUTE), MONTH(Calendar.MONTH), SECOND(Calendar.SECOND), WEEK(
                Calendar.WEEK_OF_YEAR), YEAR(Calendar.YEAR);
        private final int calendardField;

        private DurationUnit(int calendardField) {
            this.calendardField = calendardField;
        }

        public int getCalendardField() {
            return calendardField;
        }

    }

    protected static class ExpiresConfiguration {
        private List<Duration> durations;

        private StartingPoint startingPoint;

        public ExpiresConfiguration(StartingPoint startingPoint, Duration... durations) {
            this(startingPoint, Arrays.asList(durations));
        }

        public ExpiresConfiguration(StartingPoint startingPoint, List<Duration> durations) {
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

        @Override
        public String toString() {
            return "ExpiresConfiguration[startingPoint=" + startingPoint + ", duration=" + durations + "]";
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

        private PrintWriter printWriter;

        private HttpServletRequest request;

        private ServletOutputStream servletOutputStream;

        private boolean writeStarted;

        public XHttpServletResponse(HttpServletRequest request, HttpServletResponse response) {
            super(response);
            this.request = request;
        }

        @Override
        public void addDateHeader(String name, long date) {
            super.addDateHeader(name, date);
            headers.add(new Header(name, date));
        }

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name, value);
            headers.add(new Header(name, value));
        }

        @Override
        public void addIntHeader(String name, int value) {
            super.addIntHeader(name, value);
            headers.add(new Header(name, value));
        }

        protected Long getDateHeader(String name) {
            Header header = getHeaderObject(name);
            if (header == null) {
                return null;
            } else if (header.getValue() instanceof Long) {
                return (Long) header.getValue();
            } else {
                return null;
            }
        }

        private Header getHeaderObject(String name) {
            for (Header header : headers) {
                if (header.getName().equalsIgnoreCase(name)) {
                    return header;
                }
            }
            return null;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (servletOutputStream == null) {
                servletOutputStream = new XServletOutputStream(super.getOutputStream(), request, this);
            }
            return servletOutputStream;
        }

        protected String getStringHeader(String name) {
            Header header = getHeaderObject(name);
            return header == null ? null : header.getValue().toString();
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (printWriter == null) {
                printWriter = new XPrintWriter(super.getWriter(), request, this);
            }
            return printWriter;
        }

        public boolean isWriteStarted() {
            return writeStarted;
        }

        @Override
        public void setDateHeader(String name, long date) {
            super.setDateHeader(name, date);
            Header header = getHeaderObject(name);
            if (header == null) {
                headers.add(new Header(name, date));
            } else {
                header.setValue(date);
            }
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, value);
            Header header = getHeaderObject(name);
            if (header == null) {
                headers.add(new Header(name, value));
            } else {
                header.setValue(value);
            }
        }

        @Override
        public void setIntHeader(String name, int value) {
            super.setIntHeader(name, value);
            Header header = getHeaderObject(name);
            if (header == null) {
                headers.add(new Header(name, value));
            } else {
                header.setValue(value);
            }
        }

        public void setWriteStarted(boolean writeStarted) {
            this.writeStarted = writeStarted;
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

        public XPrintWriter(PrintWriter out, HttpServletRequest request, XHttpServletResponse response) {
            super(out);
            this.out = out;
            this.request = request;
            this.response = response;
        }

        public PrintWriter append(char c) {
            fireBeforeWriteResponseBodyEvent();
            return out.append(c);
        }

        public PrintWriter append(CharSequence csq) {
            fireBeforeWriteResponseBodyEvent();
            return out.append(csq);
        }

        public PrintWriter append(CharSequence csq, int start, int end) {
            fireBeforeWriteResponseBodyEvent();
            return out.append(csq, start, end);
        }

        public void close() {
            fireBeforeWriteResponseBodyEvent();
            out.close();
        }

        private void fireBeforeWriteResponseBodyEvent() {
            if (!this.response.isWriteStarted()) {
                this.response.setWriteStarted(true);
                onBeforeWriteResponseBody(request, response);
            }
        }

        public void flush() {
            fireBeforeWriteResponseBodyEvent();
            out.flush();
        }

        public void print(boolean b) {
            fireBeforeWriteResponseBodyEvent();
            out.print(b);
        }

        public void print(char c) {
            fireBeforeWriteResponseBodyEvent();
            out.print(c);
        }

        public void print(char[] s) {
            fireBeforeWriteResponseBodyEvent();
            out.print(s);
        }

        public void print(double d) {
            fireBeforeWriteResponseBodyEvent();
            out.print(d);
        }

        public void print(float f) {
            fireBeforeWriteResponseBodyEvent();
            out.print(f);
        }

        public void print(int i) {
            fireBeforeWriteResponseBodyEvent();
            out.print(i);
        }

        public void print(long l) {
            fireBeforeWriteResponseBodyEvent();
            out.print(l);
        }

        public void print(Object obj) {
            fireBeforeWriteResponseBodyEvent();
            out.print(obj);
        }

        public void print(String s) {
            fireBeforeWriteResponseBodyEvent();
            out.print(s);
        }

        public PrintWriter printf(Locale l, String format, Object... args) {
            fireBeforeWriteResponseBodyEvent();
            return out.printf(l, format, args);
        }

        public PrintWriter printf(String format, Object... args) {
            fireBeforeWriteResponseBodyEvent();
            return out.printf(format, args);
        }

        public void println() {
            fireBeforeWriteResponseBodyEvent();
            out.println();
        }

        public void println(boolean x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        public void println(char x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        public void println(char[] x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        public void println(double x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        public void println(float x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        public void println(int x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        public void println(long x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        public void println(Object x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        public void println(String x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        public void write(char[] buf) {
            fireBeforeWriteResponseBodyEvent();
            out.write(buf);
        }

        public void write(char[] buf, int off, int len) {
            fireBeforeWriteResponseBodyEvent();
            out.write(buf, off, len);
        }

        public void write(int c) {
            fireBeforeWriteResponseBodyEvent();
            out.write(c);
        }

        public void write(String s) {
            fireBeforeWriteResponseBodyEvent();
            out.write(s);
        }

        public void write(String s, int off, int len) {
            fireBeforeWriteResponseBodyEvent();
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

        public XServletOutputStream(ServletOutputStream servletOutputStream, HttpServletRequest request, XHttpServletResponse response) {
            super();
            this.servletOutputStream = servletOutputStream;
            this.response = response;
            this.request = request;
        }

        public void close() throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.close();
        }

        private void fireOnBeforeWriteResponseBodyEvent() {
            if (!this.response.isWriteStarted()) {
                this.response.setWriteStarted(true);
                onBeforeWriteResponseBody(request, response);
            }
        }

        public void flush() throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.flush();
        }

        public void print(boolean b) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(b);
        }

        public void print(char c) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(c);
        }

        public void print(double d) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(d);
        }

        public void print(float f) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(f);
        }

        public void print(int i) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(i);
        }

        public void print(long l) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(l);
        }

        public void print(String s) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(s);
        }

        public void println() throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println();
        }

        public void println(boolean b) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(b);
        }

        public void println(char c) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(c);
        }

        public void println(double d) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(d);
        }

        public void println(float f) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(f);
        }

        public void println(int i) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(i);
        }

        public void println(long l) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(l);
        }

        public void println(String s) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(s);
        }

        public void write(byte[] b) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.write(b);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.write(b, off, len);
        }

        public void write(int b) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.write(b);
        }

    }

    private static final String HEADER_CACHE_CONTROL = "Cache-Control";

    private static final String HEADER_EXPIRES = "Expires";

    private static final Logger logger = LoggerFactory.getLogger(ExpiresFilter.class);

    private static final String PARAMETER_EXPIRES_ACTIVE = "ExpiresActive";

    private static final String PARAMETER_EXPIRES_BY_TYPE = "ExpiresByType";

    private static final String PARAMETER_EXPIRES_DEFAULT = "ExpiresDefault";

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

    protected static String substringBefore(String str, String separator) {
        if (str == null || str.isEmpty() || separator == null) {
            return null;
        }

        if (separator.isEmpty()) {
            return "";
        }

        int separatorIndex = str.indexOf(separator);
        if (separatorIndex == -1) {
            return str;
        }
        return str.substring(0, separatorIndex);
    }

    private boolean active = true;

    /**
     * Default Expires configuration.
     */
    private ExpiresConfiguration defaultExpiresConfiguration;

    /**
     * Expires configuration by content type. Visible for test.
     */
    private Map<String, ExpiresConfiguration> expiresConfigurationByContentType = new LinkedHashMap<String, ExpiresConfiguration>();

    public void destroy() {

    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            if (response.isCommitted()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Request '" + httpRequest.getRequestURL()
                            + "', can not apply ExpiresFilter on already committed response.");
                }
                chain.doFilter(request, response);
            } else if (active) {
                XHttpServletResponse xResponse = new XHttpServletResponse(httpRequest, httpResponse);
                chain.doFilter(request, xResponse);
                if (!xResponse.isWriteStarted()) {
                    // Empty response, manually trigger
                    // onBeforeWriteResponseBody()
                    onBeforeWriteResponseBody(httpRequest, xResponse);
                }
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Request '" + httpRequest.getRequestURL() + "', ExpiresFilter is NOT active");
                }
                chain.doFilter(request, response);
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    public ExpiresConfiguration getDefaultExpiresConfiguration() {
        return defaultExpiresConfiguration;
    }

    /**
     * Returns the expiration date of the given {@link XHttpServletResponse} or
     * <code>null</code> if no expiration date has been configured for the
     * declared content type.
     * 
     * @see HttpServletResponse#getContentType()
     */
    private Date getExpirationDate(XHttpServletResponse response) {
        String contentType = response.getContentType();

        // lookup exact content-type match (e.g.
        // "text/html; charset=iso-8859-1")
        ExpiresConfiguration configuration = expiresConfigurationByContentType.get(contentType);
        String matchingContentType = contentType;

        if (configuration == null && contains(contentType, ";")) {
            // lookup content-type without charset match (e.g. "text/html")
            String contentTypeWithoutCharset = substringBefore(contentType, ";").trim();
            configuration = expiresConfigurationByContentType.get(contentTypeWithoutCharset);
            matchingContentType = contentTypeWithoutCharset;
        }

        if (configuration == null && contains(contentType, "/")) {
            // lookup content-type without charset match (e.g. "text/html")
            String majorType = substringBefore(contentType, "/");
            configuration = expiresConfigurationByContentType.get(majorType);
            matchingContentType = majorType;
        }

        if (configuration == null) {
            configuration = defaultExpiresConfiguration;
            matchingContentType = "#DEFAULT#";
        }

        if (configuration == null) {
            logger.trace("No Expires configuration found for content-type {}", contentType);
            return null;
        }

        logger.trace("Use {} matching '{}' for content-type '{}'", new Object[] { configuration, matchingContentType, contentType });

        Calendar calendar;
        switch (configuration.getStartingPoint()) {
        case ACCESS_TIME:
            calendar = GregorianCalendar.getInstance();
            break;
        case LAST_MODIFICATION_TIME:
            Long lastModified = response.getDateHeader("Last-Modified");
            if (lastModified == null) {
                // Last-Modified header not found, use now
                calendar = GregorianCalendar.getInstance();
            } else {
                calendar = GregorianCalendar.getInstance();
                calendar.setTimeInMillis(lastModified);
            }
            break;
        default:
            throw new IllegalStateException("Unsupported startingPoint '" + configuration.getStartingPoint() + "'");
        }
        for (Duration duration : configuration.getDurations()) {
            calendar.add(duration.getUnit().getCalendardField(), duration.getAmount());
        }

        return calendar.getTime();
    }

    public Map<String, ExpiresConfiguration> getExpiresConfigurationByContentType() {
        return expiresConfigurationByContentType;
    }

    @SuppressWarnings("unchecked")
    public void init(FilterConfig filterConfig) throws ServletException {
        for (Enumeration<String> names = filterConfig.getInitParameterNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            String value = filterConfig.getInitParameter(name);

            if (name.startsWith(PARAMETER_EXPIRES_BY_TYPE)) {
                String contentType = name.substring(PARAMETER_EXPIRES_BY_TYPE.length()).trim();
                ExpiresConfiguration expiresConfiguration = parseExpiresConfiguration(value);
                this.expiresConfigurationByContentType.put(contentType, expiresConfiguration);
            } else if (name.equalsIgnoreCase(PARAMETER_EXPIRES_DEFAULT)) {
                ExpiresConfiguration expiresConfiguration = parseExpiresConfiguration(value);
                this.defaultExpiresConfiguration = expiresConfiguration;
            } else if (name.equalsIgnoreCase(PARAMETER_EXPIRES_ACTIVE)) {
                active = "On".equalsIgnoreCase(value) || Boolean.valueOf(value);
            } else {
                logger.warn("Uknown parameter '" + name + "' with value '" + value + "' is ignored !");
            }
        }

        logger.info("Filter initialized with configuration " + this.toString());
    }

    public boolean isActive() {
        return active;
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
    public void onBeforeWriteResponseBody(HttpServletRequest request, XHttpServletResponse response) {
        String cacheControlHeader = response.getStringHeader(HEADER_CACHE_CONTROL);
        boolean expirationHeaderHasBeenSet = response.containsHeader(HEADER_EXPIRES) || contains(cacheControlHeader, "max-age");
        if (expirationHeaderHasBeenSet) {
            logger.debug("Request '{}' with content-type '{}‘, expiration header already defined", request.getRequestURI(), response
                    .getContentType());
        } else {
            Date expirationDate = getExpirationDate(response);
            if (expirationDate == null) {
                logger.debug("Request '{}' with content-type '{}‘, no expiration configured for given content-type", request
                        .getRequestURI(), response.getContentType());
            } else {
                logger.debug("Request '{}' with content-type '{}‘, set expiration date {}", new Object[] { request.getRequestURI(),
                        response.getContentType(), expirationDate });

                String maxAgeDirective = "max-age=" + ((expirationDate.getTime() - System.currentTimeMillis()) / 1000);

                String newCacheControlHeader = (cacheControlHeader == null) ? maxAgeDirective : cacheControlHeader + ", " + maxAgeDirective;
                response.setHeader(HEADER_CACHE_CONTROL, newCacheControlHeader);
                response.setDateHeader(HEADER_EXPIRES, expirationDate.getTime());
            }
        }
    }

    /**
     * "access plus 1 month 15 days 2 hours"
     * "access plus 1 month 15 days 2 hours"
     * 
     * @param line
     * @return
     */
    protected ExpiresConfiguration parseExpiresConfiguration(String line) {
        line = line.trim();

        StringTokenizer tokenizer = new StringTokenizer(line, " ");

        String currentToken;

        try {
            currentToken = tokenizer.nextToken();
        } catch (NoSuchElementException e) {
            throw new IllegalStateException("Starting point (access|now|modification) not found in directive '" + line + "'");
        }
        StartingPoint startingPoint;
        if ("access".equalsIgnoreCase(currentToken) || "now".equalsIgnoreCase(currentToken)) {
            startingPoint = StartingPoint.ACCESS_TIME;
        } else if ("modification".equalsIgnoreCase(currentToken)) {
            startingPoint = StartingPoint.LAST_MODIFICATION_TIME;
        } else {
            throw new IllegalStateException("Invalid starting point (access|now|modification) '" + currentToken + "' in directive '" + line
                    + "'");
        }

        try {
            currentToken = tokenizer.nextToken();
        } catch (NoSuchElementException e) {
            throw new IllegalStateException("Duration not found in directive '" + line + "'");
        }

        if ("plus".equalsIgnoreCase(currentToken)) {
            // skip
            try {
                currentToken = tokenizer.nextToken();
            } catch (NoSuchElementException e) {
                throw new IllegalStateException("Duration not found in directive '" + line + "'");
            }
        }

        List<Duration> durations = new ArrayList<Duration>();

        while (currentToken != null) {
            int amount;
            try {
                amount = Integer.parseInt(currentToken);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid duration (number) '" + currentToken + "' in directive '" + line + "'");
            }

            try {
                currentToken = tokenizer.nextToken();
            } catch (NoSuchElementException e) {
                throw new IllegalStateException("Duration unit not found after amount " + amount + " in directive '" + line + "'");
            }
            DurationUnit durationUnit;
            if ("years".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.YEAR;
            } else if ("month".equalsIgnoreCase(currentToken) || "months".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.MONTH;
            } else if ("week".equalsIgnoreCase(currentToken) || "weeks".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.WEEK;
            } else if ("day".equalsIgnoreCase(currentToken) || "days".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.DAY;
            } else if ("hour".equalsIgnoreCase(currentToken) || "hours".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.HOUR;
            } else if ("minute".equalsIgnoreCase(currentToken) || "minutes".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.MINUTE;
            } else if ("second".equalsIgnoreCase(currentToken) || "seconds".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.SECOND;
            } else {
                throw new IllegalStateException("Invalid duration unit (years|months|weeks|days|hours|minutes|seconds) '" + currentToken
                        + "' in directive '" + line + "'");
            }

            Duration duration = new Duration(amount, durationUnit);
            durations.add(duration);

            if (tokenizer.hasMoreTokens()) {
                currentToken = tokenizer.nextToken();
            } else {
                currentToken = null;
            }
        }

        return new ExpiresConfiguration(startingPoint, durations);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setDefaultExpiresConfiguration(ExpiresConfiguration defaultExpiresConfiguration) {
        this.defaultExpiresConfiguration = defaultExpiresConfiguration;
    }

    public void setExpiresConfigurationByContentType(Map<String, ExpiresConfiguration> expiresConfigurationByContentType) {
        this.expiresConfigurationByContentType = expiresConfigurationByContentType;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[default=" + this.defaultExpiresConfiguration + ", byType="
                + this.expiresConfigurationByContentType + ", active=" + this.active + "]";
    }
}
