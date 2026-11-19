package com.danielhw99.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import com.danielhw99.auth.security.ActiveAccount;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.StringNode;

@Service
public class IdempotentWrites {
    private final UserRepository users;
    private final WriteRequestRepository requests;
    private final JsonMapper mapper;
    public IdempotentWrites(UserRepository users,WriteRequestRepository requests,JsonMapper mapper) {
        this.users=users;this.requests=requests;this.mapper=mapper;
    }

    @Transactional
    public <T> T execute(Long owner,String rawKey,String operation,YearMonth month,Object input,
            Supplier<T> create,Function<Long,T> replay,ToLongFunction<T> identify) {
        // Legacy clients keep their existing transaction/service validation path.
        if(rawKey==null) return create.get();
        String key=key(rawKey);
        var user=ActiveAccount.requireCurrent(users.findWithLockingById(owner)
                .orElseThrow(()->new ApiException(ErrorCode.UNAUTHENTICATED)));
        String hash=hash(input);
        var previous=requests.findByUser_IdAndRequestKey(owner,key);
        if(previous.isPresent()) {
            var receipt=previous.get();
            if(!receipt.getOperation().equals(operation) || !receipt.getMonthStart().equals(month.atDay(1))
                    || !receipt.getRequestHash().equals(hash))
                throw new ApiException(ErrorCode.CONFLICT,"Idempotency-Key","같은 요청 키를 다른 저장 내용에 사용할 수 없어요.");
            return replay.apply(receipt.getResourceId());
        }
        T result=create.get();
        requests.saveAndFlush(new WriteRequest(user,key,operation,month.atDay(1),hash,identify.applyAsLong(result)));
        return result;
    }

    private String key(String value) {
        try {
            if(value.length()!=36 || !UUID.fromString(value).toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return value.toLowerCase(Locale.ROOT);
        } catch(IllegalArgumentException invalid) {
            throw new ApiException(ErrorCode.VALIDATION,"Idempotency-Key","요청 키는 UUID 형식이어야 해요.");
        }
    }

    private String hash(Object input) {
        if(input==null) throw new ApiException(ErrorCode.VALIDATION);
        JsonNode tree=mapper.valueToTree(input);
        var canonical=new TreeMap<String,JsonNode>();
        for(String name:tree.propertyNames()) {
            JsonNode value=tree.get(name);
            if((name.equals("title") || name.equals("name")) && value.isString())
                value=StringNode.valueOf(value.asString().strip());
            canonical.put(name,value);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8)));
        } catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
