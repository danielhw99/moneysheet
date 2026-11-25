package com.danielhw99.auth;
import com.danielhw99.auth.dto.LoginInput;
import com.danielhw99.common.config.ApiJsonConfiguration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
class RememberLoginInputTest {
 final JsonMapper mapper;
 RememberLoginInputTest(){var builder=JsonMapper.builder();new ApiJsonConfiguration().strictApiJson().customize(builder);mapper=builder.build();}
 @Test void omissionDefaultsToFalse(){var input=mapper.readValue("{\"email\":\"u@example.com\",\"password\":\"p\"}",LoginInput.class);assertThat(input.rememberMe()).isFalse();}
 @Test void acceptsOptionalRememberFlagWithoutCoercion(){
  var input=mapper.readValue("{\"email\":\"u@example.com\",\"password\":\"legacy password\",\"rememberMe\":true}",LoginInput.class);
  assertThat(mapper.valueToTree(input).get("rememberMe").asBoolean()).isTrue();
  for(String value:new String[]{"null","\"true\"","1","0.0"})assertThatThrownBy(()->mapper.readValue("{\"email\":\"u@example.com\",\"password\":\"p\",\"rememberMe\":"+value+"}",LoginInput.class)).isInstanceOf(RuntimeException.class);
 }
}
