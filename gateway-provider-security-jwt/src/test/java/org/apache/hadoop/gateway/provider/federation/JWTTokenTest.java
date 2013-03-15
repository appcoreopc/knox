package org.apache.hadoop.gateway.provider.federation;

import org.apache.hadoop.gateway.provider.federation.jwt.filter.JWTToken;
import org.junit.Test;

import junit.framework.TestCase;

public class JWTTokenTest extends TestCase {

  private static final String JWT_TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiAiZ2F0ZXdheSIsICJwcm4iOiAiam9obi5kb2VAZXhhbXBsZS5jb20iLCAiYXVkIjogImh0dHBzOi8vbG9naW4uZXhhbXBsZS5jb20iLCAiZXhwIjogIjEzNjMzNjA5MTMifQ.AUecCHfxT84-zllHs6_XvQuIx8186Y9s5waNOILVBoV11b4RINvknVDhIyR-j35LUn2ayQ9J2e1psey3-slWCs9B40_W-VeG5mPdtT6Job9c6ZX_eIgwSh-d88MlYoSXNt2oWcabMi6HmKeOxc6MfX__R4AMKdgXx5Jido5RRiw";
  private static final String HEADER = "{\"alg\":\"RS256\"}";
  private static final String CLAIMS = "{\"iss\": \"gateway\", \"prn\": \"john.doe@example.com\", \"aud\": \"https://login.example.com\", \"exp\": \"1363360913\"}";
  
  @Test
  public void testTokenParsing() throws Exception {
    JWTToken token = JWTToken.parseToken(JWT_TOKEN);
    
    assertEquals(token.header, HEADER);
    assertEquals(token.claims, CLAIMS);
    
    assertEquals(token.getIssuer(), "gateway");
    assertEquals(token.getPrincipal(), "john.doe@example.com");
    assertEquals(token.getAudience(), "https://login.example.com");
    assertEquals(token.getExpires(), "1363360913");
  }
  
  @Test 
  public void testTokenCreation() throws Exception {
    String[] claims = new String[4];
    claims[0] = "3MVG99OxTyEMCQ3gNp2PjkqeZKxnmAiG1xV4oHh9AKL_rSK.BoSVPGZHQukXnVjzRgSuQqGn75NL7yfkQcyy7";
    claims[1] = "john.doe@example.com";
    claims[2] = "https://login.example.com";
    claims[3] = Long.toString( ( System.currentTimeMillis()/1000 ) + 300);
    JWTToken token = new JWTToken("RS256", claims);

    assertEquals(token.getIssuer(), "3MVG99OxTyEMCQ3gNp2PjkqeZKxnmAiG1xV4oHh9AKL_rSK.BoSVPGZHQukXnVjzRgSuQqGn75NL7yfkQcyy7");
    assertEquals(token.getPrincipal(), "john.doe@example.com");
    assertEquals(token.getAudience(), "https://login.example.com");
  }
}
