/*
 * Copyright 2010 West Texas A&M University. All rights reserved.
 */
package edu.esc.cas.client.webadvisor.filter;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.jasig.cas.client.authentication.AttributePrincipal;
import org.jasig.cas.client.util.AbstractCasFilter;
import org.jasig.cas.client.util.AbstractConfigurationFilter;
import org.jasig.cas.client.util.CommonUtils;
import org.jasig.cas.client.util.XmlUtils;
import org.jasig.cas.client.validation.Assertion;

/**
 * This filter needs to be configured in the chain so that it executes after both the authentication and the validation filters.
 * 
 * @author Adam Rybicki (arybicki@unicon.net)
 */
public final class WebAdvisorLoginFilter extends AbstractConfigurationFilter {

    // URL to which to send the SSO request
    private String       webadvisorSSOURL;

    // URL to which to send the SSO token
    private String       webadvisorLoginURL;

    // ClearPass URL
    private String       clearPassURL;

    // This is the pattern in request URL that is used to detect that the user requested WebAdvisor login
    private String       loginPattern;

    // This is the pattern in request URL that is used to detect that the user requested WebAdvisor logout
    private String       logoutPattern;

    // URI to redirect to when login is requested
    private String       loginURI;

    // URI to redirect to after WebAdvisor login
    private String       mainMenuURI;

    // List of browser headers to forward with WebAdvisor's login form 
    private List<String> browserHeadersToForward;

    // List of headers coming back from WebAdvisor's login form to send back to the browser 
    private List<String> webAdvisorHeadersToForward;

    public void destroy() {
        // nothing to do
    }

    public void init(final FilterConfig filterConfig) throws ServletException {
        String log4jLocation = getPropertyFromInitParams(filterConfig, "log4jLocation", "classpath:log4j.xml");
        org.apache.log4j.xml.DOMConfigurator.configureAndWatch(log4jLocation);
        log.info("init() completed and log4j initialized");
        this.clearPassURL = getPropertyFromInitParams(filterConfig, "clearPassURL", null);
        this.loginPattern = getPropertyFromInitParams(filterConfig, "loginPattern", "pid=UT-LGRQ");
        this.logoutPattern = getPropertyFromInitParams(filterConfig, "logoutPattern", "pid=UT-LORQ");
        this.loginURI = getPropertyFromInitParams(filterConfig, "loginURI", "/WebAdvisor/Login");
        this.mainMenuURI = getPropertyFromInitParams(filterConfig, "mainMenuURI", "/WebAdvisor/");

        if (this.clearPassURL == null) {
            final String message = "clearPassURL init-parameter is not optional. Please set it in web.xml for this filter and try again.";
            log.error(message);
            throw new ServletException(message);
        }
        try {
            this.webadvisorSSOURL = URLDecoder.decode(getPropertyFromInitParams(filterConfig, "webadvisorSSOURL", null), "UTF-8");
            this.webadvisorLoginURL = URLDecoder.decode(getPropertyFromInitParams(filterConfig, "webadvisorLoginURL", null), "UTF-8");
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new ServletException(ex);
        }
        if (this.webadvisorSSOURL == null) {
            final String message = "webadvisorSSOURL init-parameter is not optional. Please set it in web.xml for this filter and try again.";
            log.error(message);
            throw new ServletException(message);
        }
        if (this.webadvisorLoginURL == null) {
            final String message = "webadvisorLoginURL init-parameter is not optional. Please set it in web.xml for this filter and try again.";
            log.error(message);
            throw new ServletException(message);
        }
        String headers = getPropertyFromInitParams(filterConfig, "browserHeadersToForward", null);
        this.browserHeadersToForward = parseHeaderList(headers);
        headers = getPropertyFromInitParams(filterConfig, "webAdvisorHeadersToForward", null);
        this.webAdvisorHeadersToForward = parseHeaderList(headers);
    }

    private List<String> parseHeaderList(String headers) {
        String[] tokens = headers.split(" ");

        if (tokens != null) {
            List<String> list = new ArrayList<String>(Arrays.asList(tokens));
            return list;
        } else
            return null;
    }

    /**
     * This is the standard Web filter entry point
     */
    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain filterChain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        
        request.setCharacterEncoding("UTF-8");
        
        String url = request.getRequestURL().toString();
        String queryString = request.getQueryString();

        if (queryString != null)
            url += "?" + queryString;

        /*
         * The workflow of the filter:
         * If this is a WebAdvisor login request, redirect to CAS login
         * If this is a request after CAS login, process WebAdvisor login, retrieve the cookies, and redirect to WebAdvisor
         * Otherwise, let normal processing continue
         */

        log.debug("WebAdvisorLoginFilter accessed with URL: " + url);
        // If the request matches the pattern of login request, redirect to login
        if (url.contains(this.loginPattern)) {
            log.debug("Redirecting to " + this.loginURI);
            response.sendRedirect(this.loginURI);
            return;
        } else if (url.contains(this.loginURI)) {
            // We should get here only of CAS authentication has succeeded, so the principal should be set
            // If there is no principal, the filter is not installed after CAS, so throw an exception.
            AttributePrincipal principal = retrievePrincipalFromSessionOrRequest(servletRequest);

            // If we don't have principal, CAS authentication hasn't been done

            if (principal == null) {
                throw new ServletException("Filter not installed properly.  It should be installed after the CAS client filters.");
            }
            String clearPassTicket = principal.getProxyTicketFor(this.clearPassURL);

            if (clearPassTicket == null) {
                // This is a necessary test because it is possible that the CAS filter still has a principal,
                // but this principal has since logged out of CAS and cannot get a clearPassTicket.
                // WebAdvisor login cannot continue.  Remove any artifacts that may be spoofing CAS filter
                // and try again.
                log.warn("CAS filter did not apparently realize that the user has logged out from CAS.  Cleaning up its artifacts and trying again.");
                HttpSession session = request.getSession(false);

                if (session != null)
                    session.removeAttribute(AbstractCasFilter.CONST_CAS_ASSERTION);

                request.removeAttribute(AbstractCasFilter.CONST_CAS_ASSERTION);
                response.sendRedirect(url);
                return;
            }
            log.debug("Retrieved ClearPass proxy ticket [" + clearPassTicket + "] for ClearPassURL [" + this.clearPassURL + "]");
            String password = retrievePasswordFromResponse(clearPassTicket);
            String username = principal.getName();

            String ssoToken = getSSOToken(username, password);

            if (ssoToken != null) {
                String loginURL = getWebAdvisorLoginURL(ssoToken);
                submitLogin(request, response, loginURL);
            } else {
                response.sendRedirect("jsp/ssoError.jsp");
            }
            return;
        } else if (url.contains(this.logoutPattern)) {
            log.info("WebAdvisor logout request detected.  Cleaning up CAS assertion.");
            HttpSession session = request.getSession(false);

            if (session != null)
                session.removeAttribute(AbstractCasFilter.CONST_CAS_ASSERTION);

            request.removeAttribute(AbstractCasFilter.CONST_CAS_ASSERTION);

            // Let WebAdvisor processing continue
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        /*
         * In most cases, the filter will fall through to here, and the filter steps out of the way.
         */
        filterChain.doFilter(servletRequest, servletResponse);
    }

    protected void submitLogin(HttpServletRequest request, HttpServletResponse response, String loginURL) throws IOException {
        log.debug("Will submit WebAdvisor login to: [" + loginURL + "]");

        if (loginURL == null) {
            log.error("Unable to continue with form subission, as the URL to submit the form to is not available.  Redirecting to the WebAdvisor main menu at ["
                    + this.mainMenuURI + "]");
            response.sendRedirect(this.mainMenuURI);
            return;
        }
        HttpClient client = new HttpClient();
        GetMethod get = new GetMethod(loginURL);

        try {
            // Forward the browser headers
            if (this.browserHeadersToForward != null) {
                Enumeration headers = request.getHeaderNames();

                while (headers.hasMoreElements()) {
                    String headerName = (String) headers.nextElement();
                    log.debug("Browser sent a header [" + headerName + "]");

                    if (this.browserHeadersToForward.contains(headerName)) {
                        Enumeration headerValues = request.getHeaders(headerName);

                        while (headerValues.hasMoreElements()) {
                            String headerValue = (String) headerValues.nextElement();
                            log.debug("Forwarding browser header [" + headerName + "] with value [" + headerValue + "]");
                            get.addRequestHeader(headerName, headerValue);
                        }
                    }
                }
            }

            //      boolean followRedirects = get.getFollowRedirects();
            get.setFollowRedirects(false);
            //      log.debug("About to execute the HTTP GET with FollowRedirects=" + followRedirects);
            int returnCode = client.executeMethod(get);
            String loginResponse = get.getResponseBodyAsString();
            log.debug("WebAdvisor login returned HTTP response code " + returnCode + " and the following response [" + loginResponse + "]");

            if (this.webAdvisorHeadersToForward != null) {
                Header[] headers = get.getResponseHeaders();

                for (Header header : headers) {
                    log.debug("WebAdvisor send a header [" + header.getName() + "]");
                    if (this.webAdvisorHeadersToForward.contains(header.getName())) {
                        log.debug("Forwarding WebAdvisor header [" + header.getName() + "] with value [" + header.getValue() + "]");
                        response.addHeader(header.getName(), header.getValue());
                    }
                }
            }

            // If WebAdvisor sent a redirect, compel the browser to do the same
            if (returnCode > 300 && returnCode < 400) {
                String locationHeader = get.getResponseHeader("Location").getValue();
                String message = "WebAdvisor redirecting to [" + locationHeader + "] when login form requested.";
                log.debug(message);
                response.sendRedirect(locationHeader);
            } else {
                PrintWriter responseWriter = response.getWriter();
                responseWriter.write(loginResponse);
            }
        } catch (IOException ex) {
            log.error("Exception submitting the WebAdvisor login.", ex);
            throw ex;
        } finally {
            get.releaseConnection();
        }
    }

    private String getWebAdvisorLoginURL(String ssoToken) {
        String redirectURL = this.webadvisorLoginURL + (this.webadvisorLoginURL.contains("?") ? "&" : "?") + "SSOTOKEN=" + ssoToken;
        log.debug("redirectURL=[" + redirectURL + "]");
        return redirectURL;
    }

    private String getSSOToken(String username, String password) {
        String ssoToken = null;

        try {
            HttpClient client = new HttpClient();
            PostMethod post = new PostMethod(this.webadvisorSSOURL);
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\"?><!DOCTYPE Request SYSTEM \"SSORequest.dtd\"><Request><LogOn username=\"");
            sb.append(username.toLowerCase());
            sb.append("\" password=\"");
            sb.append(URLEncoder.encode(password, "UTF-8"));
            sb.append("\" /></Request>");
            log.debug("About to POST the following [" + sb.toString() + "] to WebAdvisor SSO URL: [" + this.webadvisorSSOURL + "]");
            RequestEntity postData = new StringRequestEntity(sb.toString(), "text/xml", "UTF-8");
            post.setRequestEntity(postData);
            int returnCode = client.executeMethod(post);
            String postResponse = post.getResponseBodyAsString();
            log.debug("SSO request to WebAdvisor returned code " + returnCode + " and the following response [" + postResponse + "]");
            String token = postResponse.substring(postResponse.indexOf("token=\"") + "token=\"".length());
            log.debug("token untrimmed: [" + token + "]");
            ssoToken = token.substring(0, token.indexOf('\"'));
            log.debug("SSO Token: [" + ssoToken + "]");
        } catch (Exception ex) {
            log.error("Exception trying to get the SSO token from WebAdvisor", ex);
        }

        return ssoToken;
    }

    protected AttributePrincipal retrievePrincipalFromSessionOrRequest(final ServletRequest servletRequest) {
        final HttpServletRequest request = (HttpServletRequest) servletRequest;
        final HttpSession session = request.getSession(false);
        final Assertion assertion = (Assertion) (session == null ? request.getAttribute(AbstractCasFilter.CONST_CAS_ASSERTION) : session
                .getAttribute(AbstractCasFilter.CONST_CAS_ASSERTION));

        return assertion == null ? null : assertion.getPrincipal();
    }

    protected final String retrievePasswordFromResponse(final String proxyTicket) {
        final String url = this.clearPassURL + (this.clearPassURL.contains("?") ? "&" : "?") + "ticket=" + proxyTicket;
        final String response = CommonUtils.getResponseFromServer(url, "UTF-8");
        final String password = XmlUtils.getTextForElement(response, "credentials");

        if (password != null) {
            return password;
        }

        log.error("Unable to Retrieve Password.  Full Response from ClearPass was [" + response + "]");
        return null;
    }
}
