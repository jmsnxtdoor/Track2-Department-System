package edu.department.portal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface StudentRepo extends JpaRepository<Student,Long>{
 Optional<Student> findByStudentNumber(String n);
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
 boolean existsByUsername(String username);
 boolean existsByStudent_IdAndIdNot(Long studentId,Long userId);
 List<UserAccount> findAllByOrderByUsernameAsc();
 long countByRoleAndEnabledTrue(UserAccount.UserRole role);
}
interface AuditRepo extends JpaRepository<AuditLog,Long>{
 List<AuditLog> findAllByOrderByOccurredAtDesc(org.springframework.data.domain.Pageable pageable);
}
