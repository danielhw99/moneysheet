package com.danielhw99.idempotency;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class IdempotentWritesTest {
    final UserRepository users=mock(UserRepository.class);
    final WriteRequestRepository receipts=mock(WriteRequestRepository.class);
    final IdempotentWrites writes=new IdempotentWrites(users,receipts,JsonMapper.builder().build());
    final YearMonth month=YearMonth.of(2026,11);
    final String key=UUID.randomUUID().toString();
    record Input(String title,long amount) {}
    record Result(long id,String title) {}
    @BeforeEach void setup() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        var owner=new User("a@example.com","a@example.com","A","hash","v1",LocalDateTime.now());
        ReflectionTestUtils.setField(owner,"id",7L);
        when(users.findWithLockingById(7L)).thenReturn(Optional.of(owner));
        when(receipts.findByUser_IdAndRequestKey(anyLong(),anyString())).thenReturn(Optional.empty());
    }
    Result execute(String token,String operation,YearMonth selected,Input input,Supplier<Result> create) {
        return writes.execute(7L,token,operation,selected,input,create,id->new Result(id,"current"),Result::id);
    }
    WriteRequest first() {
        execute(key,"transactions",month,new Input(" Lunch ",9000),()->new Result(11,"Lunch"));
        var saved=org.mockito.ArgumentCaptor.forClass(WriteRequest.class);
        verify(receipts).saveAndFlush(saved.capture());
        when(receipts.findByUser_IdAndRequestKey(7L,key)).thenReturn(Optional.of(saved.getValue()));
        return saved.getValue();
    }
    @Test void firstSaveLocksOwnerBeforeReceiptAndStoresOnlyHashAndIdentity() {
        var receipt=first();assertThat(receipt.getResourceId()).isEqualTo(11);
        assertThat(receipt.getRequestHash()).matches("[0-9a-f]{64}");
        var order=inOrder(users,receipts);order.verify(users).findWithLockingById(7L);
        order.verify(receipts).findByUser_IdAndRequestKey(7L,key);order.verify(receipts).saveAndFlush(any());
    }
    @Test void repeatReusesIdentityAndReadsCurrentResourceWithoutCreating() {
        first();Supplier<Result> create=mock(Supplier.class);
        assertThat(execute(key,"transactions",month,new Input("Lunch",9000),create)).isEqualTo(new Result(11,"current"));
        verifyNoInteractions(create);verify(receipts,times(1)).saveAndFlush(any());
    }
    @Test void changedBodyMonthOrOperationConflictsWithoutMutation() {
        first();Supplier<Result> create=mock(Supplier.class);
        for(Runnable attempt:List.<Runnable>of(
            ()->execute(key,"transactions",month,new Input("Lunch",9001),create),
            ()->execute(key,"memos",month,new Input("Lunch",9000),create),
            ()->execute(key,"transactions",month.minusMonths(1),new Input("Lunch",9000),create)))
            assertThatThrownBy(attempt::run).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verifyNoInteractions(create);
    }
    @Test void malformedOrBlankKeyIsValidationButAbsentKeyPreservesLegacyCall() {
        for(String value:List.of(""," ","abc","1-1-1-1-1",key+" "))
            assertThatThrownBy(()->execute(value,"transactions",month,new Input("x",1),()->new Result(1,"x")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.VALIDATION);
        assertThat(execute(null,"transactions",month,new Input("x",1),()->new Result(8,"x")).id()).isEqualTo(8);
        verifyNoInteractions(receipts);
    }
    @Test void failedCreationNeverStoresReceipt() {
        assertThatThrownBy(()->execute(key,"transactions",month,new Input("x",1),()->{throw new ApiException(ErrorCode.VALIDATION);}))
            .isInstanceOf(ApiException.class);verify(receipts,never()).saveAndFlush(any());
    }
    @Test void missingOwnerPreventsReceiptReads() {
        when(users.findWithLockingById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->execute(key,"transactions",month,new Input("x",1),()->new Result(1,"x")))
            .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.UNAUTHENTICATED);
        verifyNoInteractions(receipts);
    }
    @Test void deletedResourceDoesNotRecreateIt() {
        first();Supplier<Result> create=mock(Supplier.class);
        assertThatThrownBy(()->writes.execute(7L,key,"transactions",month,new Input("Lunch",9000),create,
            id->{throw new ApiException(ErrorCode.NOT_FOUND);},Result::id))
            .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        verifyNoInteractions(create);
    }
}
