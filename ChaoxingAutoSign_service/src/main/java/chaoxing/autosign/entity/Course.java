package chaoxing.autosign.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_course")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false, length = 50)
    private String courseId;

    @Column(name = "class_id", length = 50)
    private String classId;

    @Column(name = "course_name", nullable = false, length = 200)
    private String courseName;

    @Column(name = "teacher_name", length = 100)
    private String teacherName;

    @Column(name = "school_name", length = 100)
    private String schoolName;

    @Column(name = "folder_name", length = 100)
    private String folderName;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(nullable = false)
    @Builder.Default
    private Integer status = 1;

    @Column(name = "progress_done")
    @Builder.Default
    private Integer progressDone = 0;

    @Column(name = "progress_total")
    @Builder.Default
    private Integer progressTotal = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
