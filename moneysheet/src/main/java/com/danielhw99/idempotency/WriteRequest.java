package com.danielhw99.idempotency;

import java.time.LocalDate;
import com.danielhw99.common.persistence.BaseEntity;
import com.danielhw99.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name="write_requests")
public class WriteRequest extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="user_id",nullable=false)
    private User user;
    @Column(name="request_key",nullable=false,length=36)
    private String requestKey;
    @Column(name="operation",nullable=false,length=32)
    private String operation;
    @Column(name="month_start",nullable=false)
    private LocalDate monthStart;
    @Column(name="request_hash",nullable=false,length=64)
    private String requestHash;
    @Column(name="resource_id",nullable=false)
    private Long resourceId;

    protected WriteRequest() {}
    public WriteRequest(User user,String key,String operation,LocalDate month,String hash,Long resourceId) {
        this.user=user;this.requestKey=key;this.operation=operation;this.monthStart=month;
        this.requestHash=hash;this.resourceId=resourceId;
    }
    public String getOperation() { return operation; }
    public LocalDate getMonthStart() { return monthStart; }
    public String getRequestHash() { return requestHash; }
    public Long getResourceId() { return resourceId; }
}
