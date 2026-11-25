package com.danielhw99.auth.remember;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
/** Short-lived transport object; never log bearer material. */
public final class RememberProof {
 private static final SecureRandom RANDOM=new SecureRandom();
 private static final Base64.Encoder ENCODER=Base64.getUrlEncoder().withoutPadding();
 private final byte[] selector, validator;
 private RememberProof(byte[] selector,byte[] validator){this.selector=selector.clone();this.validator=validator.clone();}
 public static RememberProof create(){byte[] s=new byte[16],v=new byte[32];RANDOM.nextBytes(s);RANDOM.nextBytes(v);return new RememberProof(s,v);}
 public static Optional<RememberProof> parse(String value){
  if(value==null || !value.matches("[A-Za-z0-9_-]{22}\\.[A-Za-z0-9_-]{43}"))return Optional.empty();
  try{String[] parts=value.split("\\.");byte[] s=Base64.getUrlDecoder().decode(parts[0]),v=Base64.getUrlDecoder().decode(parts[1]);
   var proof=new RememberProof(s,v);return proof.cookie().equals(value)?Optional.of(proof):Optional.empty();
  }catch(IllegalArgumentException invalid){return Optional.empty();}
 }
 public static Optional<byte[]> selectorBytes(String value){
  if(value==null||!value.matches("[A-Za-z0-9_-]{22}"))return Optional.empty();
  try{byte[] decoded=Base64.getUrlDecoder().decode(value);return ENCODER.encodeToString(decoded).equals(value)?Optional.of(decoded):Optional.empty();}
  catch(IllegalArgumentException invalid){return Optional.empty();}
 }
 public RememberProof next(){byte[] v=new byte[32];RANDOM.nextBytes(v);return new RememberProof(selector,v);}
 public byte[] selector(){return selector.clone();}
 public String selectorText(){return ENCODER.encodeToString(selector);}
 public byte[] hash(){try{return MessageDigest.getInstance("SHA-256").digest(validator);}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 public String cookie(){return selectorText()+"."+ENCODER.encodeToString(validator);}
 @Override public String toString(){return "RememberProof[REDACTED]";}
}
