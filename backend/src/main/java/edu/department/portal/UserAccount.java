package edu.department.portal;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import java.time.Instant;

@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
@Check(constraints = "role in ('ADMIN','TREASURER','OFFICER','STUDENT')")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 100)
    public String username;

    @Column(name = "password_hash", nullable = false, length = 60)
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public UserRole role;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", unique = true)
    public Student student;

    @Column(nullable = false)
    @ColumnDefault("TRUE")
    public boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    public Instant updatedAt = Instant.now();

    public UserAccount() {}

    public enum UserRole {
        ADMIN, TREASURER, OFFICER, STUDENT
    }
}
