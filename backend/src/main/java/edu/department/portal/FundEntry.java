package edu.department.portal;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;import jakarta.validation.constraints.*;
import java.math.BigDecimal;import java.time.LocalDateTime;
@Entity public class FundEntry{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @NotNull @Enumerated(EnumType.STRING) public Type type;
 @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) @Column(precision=14,scale=2) public BigDecimal amount;
 @NotBlank @Column(length=255) public String description;
 @JsonIgnore @Column(name="idempotency_key",unique=true,length=100) public String idempotencyKey;
 @JsonIgnore @Column(name="request_hash",length=64) public String requestHash;
 public LocalDateTime createdAt=LocalDateTime.now();
 public enum Type{OPENING_BALANCE,ADDITION,DISBURSEMENT}
}
