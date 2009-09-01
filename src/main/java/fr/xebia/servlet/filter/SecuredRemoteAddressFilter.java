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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sets {@link RequestFacade#isSecure()} to <code>true</code> if
 * {@link ServletRequest#getRemoteAddr()} matches one of the
 * <code>securedRemoteAddresses</code> of this valve.
 * 
 * @author <a href="mailto:cyrille@cyrilleleclerc.com">Cyrille Le Clerc</a>
 */
public class SecuredRemoteAddressFilter implements Filter {

    protected final static String SECURED_REMOTE_ADDRESSES_PARAMETER = "securedRemoteAddresses";

    /**
     * {@link Pattern} for a comma delimited string that support whitespace
     * characters
     */
    private static final Pattern commaSeparatedValuesPattern = Pattern.compile("\\s*,\\s*");

    /**
     * Logger
     */
    private static final Logger logger = LoggerFactory.getLogger(SecuredRemoteAddressFilter.class);

    /**
     * Convert a given comma delimited list of regular expressions into an array
     * of compiled {@link Pattern}
     */
    protected static Pattern[] commaDelimitedListToPatternArray(String commaDelimitedPatterns) {
        String[] patterns = commaDelimitedListToStringArray(commaDelimitedPatterns);
        List<Pattern> patternsList = new ArrayList<Pattern>();
        for (String pattern : patterns) {
            try {
                patternsList.add(Pattern.compile(pattern));
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Illegal pattern syntax '" + pattern + "'", e);
            }
        }
        return patternsList.toArray(new Pattern[0]);
    }

    /**
     * Convert a given comma delimited list of regular expressions into an array
     * of String
     */
    protected static String[] commaDelimitedListToStringArray(String commaDelimitedStrings) {
        return (commaDelimitedStrings == null || commaDelimitedStrings.length() == 0) ? new String[0] : commaSeparatedValuesPattern
                .split(commaDelimitedStrings);
    }

    /**
     * Return <code>true</code> if the given <code>str</code> matches at least
     * one of the given <code>patterns</code>.
     */
    protected static boolean matchesOne(String str, Pattern... patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(str).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @see #setSecuredRemoteAddresses(String)
     */
    private Pattern[] securedRemoteAddresses = new Pattern[] { Pattern.compile("10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"),
            Pattern.compile("192\\.168\\.\\d{1,3}\\.\\d{1,3}"), Pattern.compile("172\\.(?:1[6-9]|2\\d|3[0-1]).\\d{1,3}.\\d{1,3}"),
            Pattern.compile("169\\.254\\.\\d{1,3}\\.\\d{1,3}"), Pattern.compile("127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}") };

    public void destroy() {

    }

    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest xRequest;
        if (!request.isSecure() && matchesOne(request.getRemoteAddr(), securedRemoteAddresses)) {
            xRequest = new HttpServletRequestWrapper(request) {
                @Override
                public boolean isSecure() {
                    return true;
                }
            };
        } else {
            xRequest = request;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Incoming request uri=" + request.getRequestURI() + " with originalSecure='" + request.isSecure()
                    + "', remoteAddr='" + request.getRemoteAddr() + "' will be seen with newSecure='" + xRequest.isSecure() + "'");
        }

        chain.doFilter(xRequest, response);

    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            doFilter((HttpServletRequest) request, (HttpServletResponse) response, chain);
        } else {
            chain.doFilter(request, response);
        }
    }

    public void init(FilterConfig filterConfig) throws ServletException {
        String comaDelimitedSecuredRemoteAddresses = filterConfig.getInitParameter(SECURED_REMOTE_ADDRESSES_PARAMETER);
        if (comaDelimitedSecuredRemoteAddresses != null) {
            this.securedRemoteAddresses = commaDelimitedListToPatternArray(comaDelimitedSecuredRemoteAddresses);
        }
    }

}
