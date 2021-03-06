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
package org.apache.cxf.rs.security.oauth2.tokens.hawk;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cxf.common.util.Base64Exception;
import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.common.util.crypto.HmacUtils;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.rs.security.oauth2.client.HttpRequestProperties;
import org.apache.cxf.rs.security.oauth2.common.AccessTokenValidation;
import org.apache.cxf.rs.security.oauth2.common.ServerAccessToken;
import org.apache.cxf.rs.security.oauth2.provider.AccessTokenValidator;
import org.apache.cxf.rs.security.oauth2.provider.OAuthDataProvider;
import org.apache.cxf.rs.security.oauth2.provider.OAuthServiceException;
import org.apache.cxf.rs.security.oauth2.utils.AuthorizationUtils;
import org.apache.cxf.rs.security.oauth2.utils.OAuthConstants;

public class HawkAccessTokenValidator implements AccessTokenValidator {
    private OAuthDataProvider dataProvider;
    private NonceVerifier nonceVerifier;
    
    public List<String> getSupportedAuthorizationSchemes() {
        return Collections.singletonList(OAuthConstants.HAWK_AUTHORIZATION_SCHEME);
    }

    public AccessTokenValidation validateAccessToken(MessageContext mc,
                                                     String authScheme, 
                                                     String authSchemeData) throws OAuthServiceException {
        HttpRequestProperties httpProps = new HttpRequestProperties(mc.getUriInfo().getRequestUri(),
                                                                    mc.getHttpServletRequest().getMethod()); 
        Map<String, String> schemeParams = getSchemeParameters(authSchemeData);
        HawkAuthorizationScheme macAuthInfo = new HawkAuthorizationScheme(httpProps, schemeParams);
        
        HawkAccessToken macAccessToken = validateSchemeData(macAuthInfo,
                                                           schemeParams.get(OAuthConstants.HAWK_TOKEN_SIGNATURE));
        validateTimestampNonce(macAccessToken, macAuthInfo.getTimestamp(), macAuthInfo.getNonce());
        return new AccessTokenValidation(macAccessToken);
    }
    
    private static Map<String, String> getSchemeParameters(String authData) {
        String[] attributePairs = authData.split(",");
        Map<String, String> attributeMap = new HashMap<String, String>();
        for (String pair : attributePairs) {
            String[] pairValues = pair.trim().split("=", 2);
            attributeMap.put(pairValues[0].trim(), pairValues[1].trim().replaceAll("\"", ""));
        }
        return attributeMap;
    }
    
    protected void validateTimestampNonce(HawkAccessToken token, String ts, String nonce) {
        if (nonceVerifier != null) {
            nonceVerifier.verifyNonce(token.getTokenKey(), nonce, ts);
        }
    }
    
    private HawkAccessToken validateSchemeData(HawkAuthorizationScheme macAuthInfo,
                                              String clientMacString) {
        String macKey = macAuthInfo.getMacKey();
        
        ServerAccessToken accessToken = dataProvider.getAccessToken(macKey);
        if (!(accessToken instanceof HawkAccessToken)) {
            throw new OAuthServiceException(OAuthConstants.SERVER_ERROR);
        }
        HawkAccessToken macAccessToken = (HawkAccessToken)accessToken;
        
        String normalizedString = macAuthInfo.getNormalizedRequestString();
        try {
            HmacAlgorithm hmacAlgo = HmacAlgorithm.toHmacAlgorithm(macAccessToken.getMacAlgorithm());
            byte[] serverMacData = HmacUtils.computeHmac(
                macAccessToken.getMacKey(), hmacAlgo.getJavaName(), normalizedString); 
                                                         
            byte[] clientMacData = Base64Utility.decode(clientMacString);
            boolean validMac = Arrays.equals(serverMacData, clientMacData);
            if (!validMac) {
                AuthorizationUtils.throwAuthorizationFailure(Collections
                    .singleton(OAuthConstants.HAWK_AUTHORIZATION_SCHEME));
            }
        } catch (Base64Exception e) {
            throw new OAuthServiceException(OAuthConstants.SERVER_ERROR, e);
        }
        return macAccessToken;
    }
    
    public void setDataProvider(OAuthDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    public void setNonceVerifier(NonceVerifier nonceVerifier) {
        this.nonceVerifier = nonceVerifier;
    }
}
