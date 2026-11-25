package com.danielhw99.common.health;
import java.util.Map;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
/** Internal readiness; the public proxy must not expose this endpoint. */
@RestController
public class HealthController {
 private final DataSource source;
 public HealthController(DataSource source){this.source=source;}
 @GetMapping("/internal/health")
 public ResponseEntity<Map<String,String>> health(){
  try(var connection=source.getConnection()){
   if(connection.isValid(2))return ResponseEntity.ok(Map.of("status","UP"));
  }catch(SQLException unavailable){/* Never return connection details. */}
  return ResponseEntity.status(503).body(Map.of("status","DOWN"));
 }
}
