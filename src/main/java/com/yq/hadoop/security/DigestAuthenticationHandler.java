package com.yq.hadoop.security;

import org.apache.hadoop.security.authentication.client.AuthenticationException;
import org.apache.hadoop.security.authentication.server.AuthenticationHandler;
import org.apache.hadoop.security.authentication.server.AuthenticationToken;
import org.apache.hadoop.security.authentication.server.HttpConstants;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

public class DigestAuthenticationHandler implements AuthenticationHandler {
    public static final String TYPE = "digest";
    public static final String DIGEST_ALGORITHM_PROPERTY = TYPE + ".algorithm";
    public static final String DIGEST_REALM_PROPERTY = TYPE + ".realm";
    public static final String DIGEST_NONCE_LIFETIME_PROPERTY = TYPE + ".nonceLifetime";


    private String realm;
    private long nonceLifetime;
    private String nonce;
    private MessageDigest digest;
    private DBUtil db;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void init(Properties config) throws ServletException {
        String algorithm = config.getProperty(DIGEST_ALGORITHM_PROPERTY, "MD5");
        realm = config.getProperty(DIGEST_REALM_PROPERTY, "default");
        nonceLifetime = Long.parseLong(config.getProperty(DIGEST_NONCE_LIFETIME_PROPERTY, "600"));
        db = new DBUtil();

        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new ServletException("Could not find digest algorithm: " + algorithm, e);
        }
    }

    @Override
    public void destroy() {

    }

    @Override
    public boolean managementOperation(AuthenticationToken token, HttpServletRequest request, HttpServletResponse response) throws IOException, AuthenticationException {
        return true;
    }

    @Override
    public AuthenticationToken authenticate(HttpServletRequest request, HttpServletResponse response) throws IOException, AuthenticationException {
        String authorization = request.getHeader(HttpConstants.AUTHORIZATION_HEADER);
        String username = null;

        if (authorization == null || !authorization.startsWith("Digest ")) {
            response.setHeader(HttpConstants.WWW_AUTHENTICATE_HEADER,
                    "Digest realm=\"" + realm + "\", qop=\"auth\", nonce=\""
                            + generateNonce() + "\", opaque=\""
                            + generateOpaque() + "\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            // throw new AuthenticationException("Authentication required");
        } else {
            String[] tokens = authorization.substring(7).split(",");
            String realm = null;
            String nonce = null;
            String uri = null;
            String responseValue = null;
            String qop = null;
            String nc = null;
            String cnonce = null;

            for (String token : tokens) {
                String[] keyValue = token.trim().split("=");
                String key = keyValue[0];
                String value = keyValue[1].replace("\"", "");

                if ("username".equals(key)) {
                    username = value;
                } else if ("realm".equals(key)) {
                    realm = value;
                } else if ("nonce".equals(key)) {
                    nonce = value;
                } else if ("uri".equals(key)) {
                    uri = value;
                } else if ("response".equals(key)) {
                    responseValue = value;
                } else if ("qop".equals(key)) {
                    qop = value;
                } else if ("nc".equals(key)) {
                    nc = value;
                } else if ("cnonce".equals(key)) {
                    cnonce = value;
                }
            }
            if (username == null || realm == null || nonce == null || uri == null || responseValue == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                throw new AuthenticationException("Invalid authentication token");
            }

            if (!realm.equals(this.realm)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                throw new AuthenticationException("Invalid realm");
            }

            if (!validateNonce(nonce)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                throw new AuthenticationException("Invalid nonce");
            }

            String password = getPassword(username);
            if (password == null) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                throw new AuthenticationException("User not found");
            }

            String ha1 = calculateHA1(username, realm, password);
            String ha2 = null;
            if ("auth".equals(qop)) {
                ha2 = calculateHA2(request.getMethod(), uri, null);
            } else if ("auth-int".equals(qop)) {
                ha2 = calculateHA2(request.getMethod(), uri, qop);
            }

            String expectedResponse = calculateExpectedResponse(ha1, nonce, nc, cnonce, qop, ha2);
            if (!responseValue.equals(expectedResponse)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            }
        }

        return new AuthenticationToken(username, this.realm, getType());
    }

    private String getPassword(String username) {
        String password = null;
        Object[] objs = {username};
        ResultSet resultSet = db.select("select password from hadoop_web_user where username=?", objs);
        try {
            while (resultSet.next()) {
                password = resultSet.getString("password");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return password;
    }

    private String generateNonce() {
        String format = "%08x";
        String nonce = String.format(format, (int) (Math.random() * 0xffffffffL))
                + String.format(format, (int) (System.currentTimeMillis() / 1000));
        this.nonce = nonce;
        return nonce;
    }

    private String generateOpaque() {
        return String.format("%08x", (int) (Math.random() * 0xffffffffL));
    }

    private boolean validateNonce(String nonce) {
        // implement your own logic to validate the nonce
        if (nonce.equals(this.nonce)) {
            return true;
        } else {
            return false;
        }
    }

    private String calculateHA1(String username, String realm, String password) {
        String colonSeparated = username + ":" + realm + ":" + password;
        byte[] digestBytes = digest.digest(colonSeparated.getBytes());
        return bytesToHex(digestBytes);
    }

    private String calculateHA2(String method, String uri, String qop) {
        String colonSeparated = method + ":" + uri;
        if (qop != null && qop.equals("auth-int")) {
            // TODO: handle message body for qop=auth-int
        }
        byte[] digestBytes = digest.digest(colonSeparated.getBytes());
        return bytesToHex(digestBytes);
    }

    private String calculateExpectedResponse(String ha1, String nonce, String nc,
                                             String cnonce, String qop, String ha2) {
        String responseStr = null;
        if (qop == null) {
            responseStr = ha1 + ":" + nonce + ":" + ha2;
        } else {
            responseStr = ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2;
        }
        return bytesToHex(digest.digest((responseStr).getBytes()));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
