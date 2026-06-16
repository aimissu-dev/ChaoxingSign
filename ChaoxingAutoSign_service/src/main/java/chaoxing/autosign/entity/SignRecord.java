package chaoxing.autosign.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_sign_record", indexes = {
    @Index(name = "idx_user_active", columnList = "user_id, active_id"),
    @Index(name = "idx_sign_time", columnList = "sign_time")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SignRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", length = 50)
    private String courseId;

    @Column(name = "course_name", length = 200)
    private String courseName;

    @Column(name = "active_id", nullable = false, length = 50)
    private String activeId;

    @Column(name = "sign_type", length = 20)
    private String signType;

    @Column(name = "sign_time", nullable = false)
    private LocalDateTime signTime;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "result_msg", length = 500)
    private String resultMsg;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
