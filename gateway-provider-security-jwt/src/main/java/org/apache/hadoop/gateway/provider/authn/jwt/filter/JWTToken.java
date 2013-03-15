  /**
   * Licensed to the Apache Software Foundation (ASF) under one
   * or more contributor license agreements.  See the NOTICE file
   * distributed with this work for additional information
   * regarding copyright ownership.  The ASF licenses this file
   * to you under the Apache License, Version 2.0 (the
   * "License"); you may not use this file except in compliance
   * with the License.  You may obtain a copy of the License at
   *
   *     http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software
   * distributed under the License is distributed on an "AS IS" BASIS,
   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   * See the License for the specific language governing permissions and
   * limitations under the License.
   */
package org.apache.hadoop.gateway.provider.authn.jwt.filter;

import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;

import org.apache.commons.codec.binary.Base64;

import com.jayway.jsonpath.JsonPath;

public class JWTToken {
  private static final String headerTemplate = "'{'\"alg\": \"{0}\"'}'";
  private static final String claimTemplate = "'{'\"iss\": \"{0}\", \"prn\": \"{1}\", \"aud\": \"{2}\", \"exp\": \"{3}\"'}'";
  public static final String PRINCIPAL = "prn";
  public static final String ISSUER = "iss";
  public static final String AUDIENCE = "aud";
  public static final String EXPIRES = "exp";

  public String header = null;
  public String claims = null;
  
  byte[] payload = null;
  
  public JWTToken(byte[] header, byte[] claims, byte[] signature) {
    try {
      this.header = new String(header, "UTF-8");
      this.claims = new String(claims, "UTF-8");
      this.payload = signature;
    } catch (UnsupportedEncodingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public JWTToken(String alg, String[] claimsArray) {
    MessageFormat headerFormatter = new MessageFormat(headerTemplate);
    String[] algArray = new String[1];
    algArray[0] = alg;
    header = headerFormatter.format(algArray);

    MessageFormat claimsFormatter = new MessageFormat(claimTemplate);
    claims = claimsFormatter.format(claimsArray);
  }
  
  public String getPayloadToSign() {
    StringBuffer sb = new StringBuffer();
    try {
      sb.append(Base64.encodeBase64URLSafeString(header.getBytes("UTF-8")));
      sb.append(".");
      sb.append(Base64.encodeBase64URLSafeString(claims.getBytes("UTF-8")));
    } catch (UnsupportedEncodingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    return sb.toString();
  }

  public String toString() {
    System.out.println("Rendering JWT token for the wire");
    StringBuffer sb = new StringBuffer();
    try {
      sb.append(Base64.encodeBase64URLSafeString(header.getBytes("UTF-8")));
      sb.append(".");
      sb.append(Base64.encodeBase64URLSafeString(claims.getBytes("UTF-8")));
      sb.append(".");
      sb.append(Base64.encodeBase64URLSafeString(payload));
    } catch (UnsupportedEncodingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    System.out.println("Returning JWT token for the wire: " + sb.toString());
    return sb.toString();
  }
  
  public void setSignaturePayload(byte[] payload) {
    this.payload = payload;
  }
  
  public byte[] getSignaturePayload() {
    return this.payload;
  }

  public static JWTToken parseToken(String wireToken) {
    JWTToken token = null;
    System.out.println("token off the wire: " + wireToken);
    String[] parts = wireToken.split("\\.");
    token = new JWTToken(Base64.decodeBase64(parts[0]), Base64.decodeBase64(parts[1]), Base64.decodeBase64(parts[2]));
    System.out.println("header: " + token.header);
    System.out.println("claims: " + token.claims);
    System.out.println("payload: " + new String(token.payload));
    
    return token;
  }
  
  public String getClaim(String claimName) {
    String claim = null;
    
    claim = JsonPath.read(claims, "$." + claimName);
    
    return claim;
  }

  public String getPrincipal() {
    return getClaim(JWTToken.PRINCIPAL);
  }

  public String getIssuer() {
    return getClaim(JWTToken.ISSUER);
  }

  public String getAudience() {
    return getClaim(JWTToken.AUDIENCE);
  }

  public String getExpires() {
    return getClaim(JWTToken.EXPIRES);
  }
}
