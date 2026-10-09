package com.nani.internship;
import jakarta.persistence.*;
import java.time.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional; import java.util.List;

@Entity @Table(name="users") class User {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
  String fullName; @Column(unique=true) String email; String passwordHash;
  Integer gradYear; String branch; String role="USER";
}
@Entity @Table(name="resumes") class Resume {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
  @Column(unique=true) Long userId; String fileName; int score;
  @Column(columnDefinition="LONGTEXT") String text;
  @Column(columnDefinition="LONGTEXT") String analysisJson;
  @Column(columnDefinition="TEXT") String skills; @Column(columnDefinition="LONGBLOB") byte[] fileData;
  LocalDateTime uploadedAt=LocalDateTime.now();
}
@Entity @Table(name="opportunities") class Opportunity {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
  @Column(unique=true) String sourceId;
  String company, title, category, location, workMode, stipend, experience, applyUrl, sourceName, status="ACTIVE";
  @Column(columnDefinition="TEXT") String description, requiredSkills, gradYears, branches;
  Boolean ppo, verified=false; LocalDate closingDate; LocalDateTime updatedAt=LocalDateTime.now();
}
interface UserRepo extends JpaRepository<User,Long>{ Optional<User> findByEmail(String e); boolean existsByEmail(String e); }
interface ResumeRepo extends JpaRepository<Resume,Long>{ Optional<Resume> findByUserId(Long id); }
interface OppRepo extends JpaRepository<Opportunity,Long>{ Optional<Opportunity> findBySourceId(String s); List<Opportunity> findByCompanyIgnoreCaseAndStatus(String c,String s); }
