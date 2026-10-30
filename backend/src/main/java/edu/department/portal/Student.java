package edu.department.portal;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
@Entity public class Student{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @NotBlank @Size(max=255) @Column(nullable=false,unique=true) public String studentNumber;
 @NotBlank @Size(max=255) public String fullName;
 @Size(max=255) public String course;@Size(max=255) public String yearLevel;@Size(max=255) public String section;
 public Student(){}
}
