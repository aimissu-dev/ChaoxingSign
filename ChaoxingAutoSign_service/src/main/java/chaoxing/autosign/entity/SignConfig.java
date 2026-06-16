package chaoxing.autosign.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "t_sign_config", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "course_name"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SignConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_name", nullable = false, length = 200)
    private String courseName;

    @Column(name = "sign_code", length = 50)
    private String signCode;

    @Column(length = 300)
    private String address;

    @Column(precision = 12, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 12, scale = 8)
    private BigDecimal longitude;

    @Column(name = "sign_start_time", length = 30)
    private String signStartTime;

    @Column(name = "sign_end_time", length = 30)
    private String signEndTime;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 多时间窗口列表（不持久化到本表，通过 SignTimeWindowRepository 管理） */
    @Transient
    @Builder.Default
    private List<SignTimeWindow> timeWindows = new ArrayList<>();

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
