package com.danielhw99.auth;
import java.time.LocalDate;
import java.lang.reflect.Proxy;
import java.util.Map;
import com.danielhw99.common.api.ApiEnum;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.common.api.ApiFormats;
import com.danielhw99.common.config.ApiJsonConfiguration;
import com.danielhw99.common.web.ApiEnumBinding;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.beans.MutablePropertyValues;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
class ApiBoundaryTest {
 record Input(ApiId id,Long amount,LocalDate date,Boolean consent) { }
 enum Kind implements ApiEnum { EXTRA_EXPENSE;public String getValue(){return "extraExpense";} }
 public static class Form {
  private Kind kind;
  public Kind getKind(){return kind;}public void setKind(Kind value){kind=value;}
 }
 public void endpoint(Form form) { }
 @Test void strictJsonKeepsStringIdIntegerMoneyAndDateRules() {
  var config=new ApiJsonConfiguration();var builder=JsonMapper.builder().addModule(config.apiTemporalModule());
  config.strictApiJson().customize(builder);var mapper=builder.build();
  var input=mapper.readValue("{\"id\":\"1\",\"amount\":9000,\"date\":\"2028-02-29\"}",Input.class);
  assertThat(mapper.writeValueAsString(input)).contains("\"id\":\"1\"","\"amount\":9000");
  for(String json: new String[]{"{\"id\":1}","{\"id\":\"01\"}","{\"amount\":1.5}","{\"amount\":\"9000\"}","{\"date\":\"2026-02-29\"}","{\"extra\":1}","{\"consent\":\"true\"}","{\"consent\":1}"}) {
   assertThatThrownBy(()->mapper.readValue(json,Input.class)).isInstanceOf(RuntimeException.class);
  }
  assertThatThrownBy(()->ApiFormats.parseTime("10:30:00")).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void dtoApiEnumAcceptsContractValueAndRejectsJavaEnumName() throws Exception {
  var handler=new HandlerMethod(this,ApiBoundaryTest.class.getMethod("endpoint",Form.class));
  var request=(HttpServletRequest)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{HttpServletRequest.class},
    (p,m,a)->m.getName().equals("getAttribute")?handler:null);
  for(String value:new String[]{"extraExpense","EXTRA_EXPENSE","unknown"}) {
   var form=new Form();var binder=new WebDataBinder(form);new ApiEnumBinding().bindApiEnums(binder,request);
   binder.bind(new MutablePropertyValues(Map.of("kind",value)));
   assertThat(binder.getBindingResult().hasErrors()).isEqualTo(!value.equals("extraExpense"));
  }
 }
}
