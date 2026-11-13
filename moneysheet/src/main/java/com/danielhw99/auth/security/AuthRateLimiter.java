package com.danielhw99.auth.security;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.stereotype.Component;
@Component
public class AuthRateLimiter {
    private static final int MAX_IP_KEYS=10000;
    private static final int MAX_ACCOUNT_KEYS=10000;
    private record Window(int count,long expires) { }
    private final Map<String,Window> signupWindows=new HashMap<>();
    private final Map<String,Window> loginWindows=new HashMap<>();
    private final Map<String,Window> accountWindows=new HashMap<>();
    private final Clock clock;
    public AuthRateLimiter(Clock clock) { this.clock=clock; }
    public synchronized void request(String ip,boolean signup) {
        Map<String,Window> windows=signup?signupWindows:loginWindows;
        String key=ip;
        long now=clock.instant().getEpochSecond();
        Window window=active(windows,key,now);
        int limit=signup?10:30;
        if(window!=null && window.count()>=limit) limited(window.expires()-now);
        put(windows,MAX_IP_KEYS,key,new Window(window==null?1:window.count()+1,window==null?now+(signup?3600:60):window.expires()),now);
    }
    public synchronized void checkAccount(String email) {
        long now=clock.instant().getEpochSecond();Window window=active(accountWindows,email,now);
        if(window!=null && window.count()>=5) limited(window.expires()-now);
    }
    public synchronized void failed(String email) {
        long now=clock.instant().getEpochSecond();String key=email;Window old=active(accountWindows,key,now);
        int count=old==null?1:Math.min(5,old.count()+1);
        put(accountWindows,MAX_ACCOUNT_KEYS,key,new Window(count,count==5||old==null?now+900:old.expires()),now);
    }
    public synchronized void succeeded(String email) { accountWindows.remove(email); }
    private Window active(Map<String,Window> windows,String key,long now) {
        Window w=windows.get(key);
        if(w!=null && w.expires()<=now) { windows.remove(key);return null; }
        return w;
    }
    private void put(Map<String,Window> windows,int capacity,String key,Window value,long now) {
        if(!windows.containsKey(key) && windows.size()>=capacity) {
            windows.values().removeIf(w->w.expires()<=now);
            if(windows.size()>=capacity) limited(60);
        }
        windows.put(key,value);
    }
    private void limited(long seconds) { throw new ApiException(ErrorCode.RATE_LIMITED,Math.max(1,seconds)); }
}
