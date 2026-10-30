package edu.department.portal;
import jakarta.persistence.*;import java.time.LocalDateTime;
@Entity @Table(uniqueConstraints=@UniqueConstraint(columnNames={"student_id","event_id"})) public class Attendance{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(optional=false) public Student student;
 @ManyToOne(optional=false) public DepartmentEvent event;
 public LocalDateTime scannedAt=LocalDateTime.now();
 @Enumerated(EnumType.STRING) public Status status=Status.PRESENT;
 public enum Status{PRESENT,LATE,ABSENT,EXCUSED}
}
