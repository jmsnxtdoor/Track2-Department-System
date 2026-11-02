package edu.department.portal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
interface StudentRepo extends JpaRepository<Student,Long>{
 Optional<Student> findByStudentNumber(String n);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select s from Student s where s.id = :id")
 Optional<Student> findByIdForUpdate(@Param("id") Long id);
 boolean existsByStudentNumber(String studentNumber);
 boolean existsByStudentNumberIgnoreCase(String studentNumber);
 boolean existsByStudentNumberAndIdNot(String studentNumber,Long id);
 boolean existsByStudentNumberIgnoreCaseAndIdNot(String studentNumber,Long id);
 List<Student> findByStudentNumberContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrderByStudentNumberAsc(String studentNumber,String fullName);
 List<Student> findAllByOrderByStudentNumberAsc();
}
interface LedgerRepo extends JpaRepository<LedgerEntry,Long>{
 List<LedgerEntry> findByStudentIdOrderByCreatedAtDesc(Long id);
 List<LedgerEntry> findAllByOrderByCreatedAtDesc();
 boolean existsByDescriptionStartingWith(String prefix);
 boolean existsByReferenceNumber(String referenceNumber);
}
interface FundRepo extends JpaRepository<FundEntry,Long>{
 List<FundEntry> findAllByOrderByCreatedAtDesc();
 @Query(value="SELECT 1 FROM pg_advisory_xact_lock(7284927612836491)",nativeQuery=true)
 Integer acquireFundLedgerLock();
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select f from FundEntry f order by f.id")
 List<FundEntry> findAllForUpdate();
 boolean existsByDescriptionStartingWith(String prefix);
}
interface EventRepo extends JpaRepository<DepartmentEvent,Long>{
 List<DepartmentEvent> findAllByOrderByEventDateDesc();
 List<DepartmentEvent> findByActiveTrue();
}
interface AttendanceRepo extends JpaRepository<Attendance,Long>{
 boolean existsByStudentIdAndEventId(Long studentId,Long eventId);
 List<Attendance> findByStudentId(Long id);
 List<Attendance> findByStudentIdOrderByScannedAtDesc(Long id);
 List<Attendance> findByEventId(Long eventId);
}
interface UserRepo extends JpaRepository<UserAccount,Long>{
 Optional<UserAccount> findByUsername(String username);
 @EntityGraph(attributePaths = "student")
 Optional<UserAccount> findWithStudentByUsername(String username);
 @EntityGraph(attributePaths = "student")
 Optional<UserAccount> findWithStudentById(Long id);
 boolean existsByUsername(String username);
 boolean existsByStudent_IdAndIdNot(Long studentId,Long userId);
 @EntityGraph(attributePaths = "student")
 List<UserAccount> findAllByOrderByUsernameAsc();
 long countByRoleAndEnabledTrue(UserAccount.UserRole role);
}
interface AuditRepo extends JpaRepository<AuditLog,Long>{
 List<AuditLog> findAllByOrderByOccurredAtDesc(org.springframework.data.domain.Pageable pageable);
}
