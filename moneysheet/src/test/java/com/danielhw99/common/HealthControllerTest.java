package com.danielhw99.common;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import com.danielhw99.common.health.HealthController;
import org.junit.jupiter.api.Test;
class HealthControllerTest {
 @Test void readinessClosesConnectionAndOnlyReturnsStatus() throws Exception {
  var source=mock(DataSource.class);var connection=mock(Connection.class);when(source.getConnection()).thenReturn(connection);when(connection.isValid(2)).thenReturn(true);
  var result=new HealthController(source).health();assertThat(result.getStatusCode().value()).isEqualTo(200);assertThat(result.getBody()).containsExactly(entry("status","UP"));verify(connection).close();
 }
 @Test void databaseFailureIsSanitized() throws Exception {
  var source=mock(DataSource.class);when(source.getConnection()).thenThrow(new SQLException("sensitive details"));
  var result=new HealthController(source).health();assertThat(result.getStatusCode().value()).isEqualTo(503);assertThat(result.getBody()).containsExactly(entry("status","DOWN"));
 }
 @Test void invalidConnectionIsNotReady() throws Exception {
  var source=mock(DataSource.class);var connection=mock(Connection.class);when(source.getConnection()).thenReturn(connection);
  assertThat(new HealthController(source).health().getStatusCode().value()).isEqualTo(503);verify(connection).close();
 }
}
