package com.danielhw99.balance.entity;
import java.time.LocalDate;
import com.danielhw99.common.persistence.BaseEntity;
import com.danielhw99.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
@Entity @Table(name="daily_balance_checks")
public class DailyBalanceCheck extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
 @Column(name="balance_date",nullable=false) private LocalDate date;
 @Column(name="actual_balance") private Long actualBalance;
 @Column(nullable=false) private boolean locked;
 protected DailyBalanceCheck() {}
 public DailyBalanceCheck(User user,LocalDate date) {this.user=user;this.date=date;}
 public void update(Long amount,boolean locked) {this.actualBalance=amount;this.locked=locked;}
 public LocalDate getDate() {return date;}
 public Long getActualBalance() {return actualBalance;}
 public boolean isLocked() {return locked;}
}
