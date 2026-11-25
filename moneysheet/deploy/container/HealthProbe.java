import java.net.URI;
import java.net.HttpURLConnection;
public final class HealthProbe {
 public static void main(String[] args) {
  try {
   var connection=(HttpURLConnection)URI.create("http://127.0.0.1:8080/internal/health").toURL().openConnection();
   connection.setConnectTimeout(2000);connection.setReadTimeout(5000);
   try {if(connection.getResponseCode()!=200)System.exit(1);}finally{connection.disconnect();}
  }catch(Exception unavailable){System.exit(1);}
 }
}
