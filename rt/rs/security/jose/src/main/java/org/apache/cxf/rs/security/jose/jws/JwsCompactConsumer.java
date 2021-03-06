/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.rs.security.jose.jws;

import java.io.UnsupportedEncodingException;

import org.apache.cxf.common.util.Base64Exception;
import org.apache.cxf.common.util.Base64UrlUtility;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.rs.security.jose.JoseHeaders;
import org.apache.cxf.rs.security.jose.JoseHeadersReader;
import org.apache.cxf.rs.security.jose.JoseHeadersReaderWriter;
import org.apache.cxf.rs.security.jose.jwk.JsonWebKey;

public class JwsCompactConsumer {
    private JoseHeadersReader reader = new JoseHeadersReaderWriter();
    private String encodedSequence;
    private String encodedSignature;
    private String headersJson;
    private String jwsPayload;
    public JwsCompactConsumer(String encodedJws) {
        this(encodedJws, null);
    }
    public JwsCompactConsumer(String encodedJws, JoseHeadersReader r) {
        if (r != null) {
            this.reader = r;
        }
        if (encodedJws.startsWith("\"") && encodedJws.endsWith("\"")) {
            encodedJws = encodedJws.substring(1, encodedJws.length() - 1);
        }
        String[] parts = encodedJws.split("\\.");
        if (parts.length != 3) {
            if (parts.length == 2 && encodedJws.endsWith(".")) {
                encodedSignature = "";
            } else {
                throw new SecurityException("Invalid JWS Compact sequence");
            }
        } else {
            encodedSignature = parts[2];
        }
        headersJson = decodeToString(parts[0]);
        jwsPayload = decodeToString(parts[1]);
        encodedSequence = parts[0] + "." + parts[1];
        
    }
    public String getUnsignedEncodedPayload() {
        return encodedSequence;
    }
    public String getEncodedSignature() {
        return encodedSignature;
    }
    public String getDecodedJsonHeaders() {
        return headersJson;
    }
    public String getDecodedJwsPayload() {
        return jwsPayload;
    }
    public byte[] getDecodedJwsPayloadBytes() {
        return StringUtils.toBytesUTF8(jwsPayload);
    }
    public byte[] getDecodedSignature() {
        return encodedSignature.isEmpty() ? new byte[]{} : decode(encodedSignature);
    }
    public JwsHeaders getJwsHeaders() {
        JoseHeaders joseHeaders = reader.fromJsonHeaders(headersJson);
        if (joseHeaders.getHeaderUpdateCount() != null) { 
            throw new SecurityException();
        }
        return new JwsHeaders(joseHeaders);
    }
    public boolean verifySignatureWith(JwsSignatureVerifier validator) {
        try {
            if (validator.verify(getJwsHeaders(), getUnsignedEncodedPayload(), getDecodedSignature())) {
                return true;
            }
        } catch (SecurityException ex) {
            // ignore
        }
        return false;
    }
    public boolean verifySignatureWith(JsonWebKey key) {
        return verifySignatureWith(JwsUtils.getSignatureVerifier(key));
    }
    private static String decodeToString(String encoded) {
        try {
            return new String(decode(encoded), "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            throw new SecurityException(ex);
        }
        
    }
    protected JoseHeadersReader getReader() {
        return reader;
    }
    private static byte[] decode(String encoded) {
        try {
            return Base64UrlUtility.decode(encoded);
        } catch (Base64Exception ex) {
            throw new SecurityException(ex);
        }
    }
}
