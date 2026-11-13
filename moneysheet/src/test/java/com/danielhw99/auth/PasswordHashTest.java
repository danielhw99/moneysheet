package com.danielhw99.auth;
import com.danielhw99.auth.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PasswordHashTest {
 @Test void usesApprovedArgon2ParametersAndRandomSalt() {
  var encoder=new SecurityConfiguration().passwordEncoder();
  long start=System.nanoTime();
  String first=encoder.encode("a unique password for hash checks");
  long encodeMs=(System.nanoTime()-start)/1000000;
  String second=encoder.encode("a unique password for hash checks");
  assertThat(first).startsWith("{argon2id}$argon2id$v=19$m=19456,t=2,p=1$");
  assertThat(first).isNotEqualTo(second);
  assertThat(encoder.matches("a unique password for hash checks",first)).isTrue();
  System.out.println("Argon2id single encode (informational, not a performance threshold): "+encodeMs+" ms");
 }
}
