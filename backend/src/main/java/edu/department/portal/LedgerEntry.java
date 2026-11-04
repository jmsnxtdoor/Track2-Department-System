package edu.department.portal;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;import java.time.LocalDateTime;
@Entity public class LedgerEntry{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(optional=false) public Student student;
 @NotNull @Enumerated(EnumType.STRING) public Kind kind;
 @NotNull @DecimalMin("0.01") @Column(precision=14,scale=2) public BigDecimal amount;
 @NotBlank public String description;
 @Column(unique=true) public String referenceNumber;
 @JsonIgnore @Column(name="idempotency_key",unique=true,length=100) public String idempotencyKey;
 @JsonIgnore @Column(name="request_hash",length=64) public String requestHash;
 public LocalDateTime createdAt=LocalDateTime.now();
 public enum Kind{FINE,PAYMENT}
 public LedgerEntry(){}
}
