package com.yhsoft.hadoop.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.security.authentication.client.AuthenticationException;
import org.apache.hadoop.security.authentication.server.AuthenticationHandler;
import org.apache.hadoop.security.authentication.server.AuthenticationToken;
import org.apache.hadoop.security.authentication.server.HttpConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicAuthenticationHandler implements AuthenticationHandler {
    public static final Logger LOG = LoggerFactory.getLogger(
            BasicAuthenticationHandler.class);

    public static final String TYPE = "basic";

    private String credentialUsername;
    private String credentialPassword;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void init(Properties config) throws ServletException {
        // username : password
        String credential = config.getProperty(TYPE + ".credential", "");
        LOG.trace("YHBasicAuthentication: value of " + TYPE + ".credential: " + credential);
        String[] credentials =  credential.split(":");
        credentialUsername = credentials.length > 0 ? credentials[0].trim() : "";
        credentialPassword = credentials.length > 1 ? credentials[1].trim() : "";
    }

    @Override
    public void destroy() {

    }

    @Override
    public boolean managementOperation(AuthenticationToken token, HttpServletRequest request, HttpServletResponse response)
            throws IOException, AuthenticationException {
        return true;
    }

    @Override
    public AuthenticationToken authenticate(HttpServletRequest request, HttpServletResponse response)
            throws IOException, AuthenticationException {

        AuthenticationToken token = null;
        String authorization = request.getHeader(HttpConstants.AUTHORIZATION_HEADER);

        // Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
        if (authorization == null || !authorization.startsWith(HttpConstants.BASIC)) {
            // response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            // WWW-Authenticate: Basic
            response.setHeader(WWW_AUTHENTICATE, HttpConstants.BASIC);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            token = null;
            if (authorization == null) {
                LOG.trace("YHBasicAuthentication: First request, start basic authentication");
            } else {
                LOG.warn("YHBasicAuthentication: '" + HttpConstants.AUTHORIZATION_HEADER + "' does not start with '" + HttpConstants.BASIC + "' : {}", authorization);
            }
        } else {
            try {
                authorization = authorization.substring(HttpConstants.BASIC.length()).trim();
                final Base64 base64 = new Base64(0);
                String credential = new String(base64.decode(authorization),
                        StandardCharsets.UTF_8);
                String[] credentials =  credential.split(":");
                String username = credentials.length > 0 ? credentials[0].trim() : "";
                String password = credentials.length > 1 ? credentials[1].trim() : "";

                if (credentialUsername.equals(username) && credentialPassword.equals(password)) {
                    token = new AuthenticationToken(username, username, TYPE);
                    response.setStatus(HttpServletResponse.SC_OK);
                } else {
                    // response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setHeader(WWW_AUTHENTICATE, HttpConstants.BASIC);
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    token = null;
                    LOG.warn("YHBasicAuthentication: invalid username or password has been provided : " + username + "=" + password);
                }
            } catch (Exception e) {
                throw new AuthenticationException(e);
            }
        }

        return token;
    }

}
