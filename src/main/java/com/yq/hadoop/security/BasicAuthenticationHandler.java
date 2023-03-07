package com.yq.hadoop.security;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.hadoop.security.authentication.client.AuthenticationException;
import org.apache.hadoop.security.authentication.server.AuthenticationHandler;
import org.apache.hadoop.security.authentication.server.AuthenticationToken;
import org.apache.hadoop.security.authentication.server.HttpConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Properties;

public class BasicAuthenticationHandler implements AuthenticationHandler {
    public static final Logger LOG = LoggerFactory.getLogger(
            BasicAuthenticationHandler.class);

    public static final String TYPE = "basic";

    private String username;
    private String password;
    private DBUtil db;


    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void init(Properties config) throws ServletException {
        db = new DBUtil();
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

        if (authorization == null
                || !authorization.startsWith(HttpConstants.BASIC)) {
            // response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(WWW_AUTHENTICATE, HttpConstants.BASIC);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            token = null;
            if (authorization == null) {
                LOG.trace("Basic auth starting");
            } else {
                LOG.warn("'" + HttpConstants.AUTHORIZATION_HEADER
                                + "' does not start with '" + HttpConstants.BASIC + "' :  {}",
                        authorization);
            }
        } else {
            try {
                authorization = authorization.substring(
                        HttpConstants.BASIC.length()).trim();
                final Base64 base64 = new Base64(0);
                String credentials = new String(base64.decode(authorization),
                        StandardCharsets.UTF_8);
                String _username = credentials.split(":")[0].trim();
                String _password = DigestUtils.md5Hex(credentials.split(":")[1].trim());

                Object[] objs = {_username};
                ResultSet resultSet = db.select("select username,password from user where username=?", objs);
                while (resultSet.next()) {
                    this.username = resultSet.getString("username");
                    this.password = resultSet.getString("password");
                }

                if (username.equals(_username) && password.equals(_password)) {
                    token = new AuthenticationToken(_username, _username, TYPE);
                    response.setStatus(HttpServletResponse.SC_OK);
                } else {
                    // response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setHeader(WWW_AUTHENTICATE, HttpConstants.BASIC);
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    token = null;
                    LOG.warn("Error validating user:"
                            + " invalid username or password has been provided");
                }
            } catch (Exception e) {
                throw new AuthenticationException(e);
            }
        }
        db.closeConnection();
        return token;
    }

    private boolean isBlank(final CharSequence cs) {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
