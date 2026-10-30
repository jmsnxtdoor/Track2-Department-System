package edu.department.portal;
import jakarta.persistence.*;import jakarta.validation.constraints.*;import java.time.LocalDate;
@Entity public class DepartmentEvent{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @NotBlank @Size(max=255) public String title;
 @NotNull public LocalDate eventDate;
 public boolean active=true;
}
