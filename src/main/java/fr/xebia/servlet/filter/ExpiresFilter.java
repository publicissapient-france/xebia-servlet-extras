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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
 * @author <a href="mailto:cyrille@cyrilleleclerc.com">Cyrille Le Clerc</a>
 */
public class ExpiresFilter implements Filter {

    protected static class Header {
        final private String name;

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
                onStartWrite(request, response);
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
                onStartWrite(request, response);
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

    protected static boolean contains(String str, String searchStr) {
        if (str == null || searchStr == null) {
            return false;
        }
        return str.indexOf(searchStr) >= 0;
    }

    protected static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

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
    }

    private Date getExpirationDate(XHttpServletResponse response) {
        // TODO Auto-generated method stub
        return null;
    }

    public void init(FilterConfig filterConfig) throws ServletException {

    }

    public void onStartWrite(HttpServletRequest request, XHttpServletResponse response) {
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
