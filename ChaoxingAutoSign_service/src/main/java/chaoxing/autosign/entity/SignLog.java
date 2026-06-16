package chaoxing.autosign.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_sign_log", indexes = {
    @Index(name = "idx_user_time", columnList = "user_id, created_at")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SignLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "log_type", nullable = false, length = 20)
    private String logType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
