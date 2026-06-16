package chaoxing.autosign.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_user")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String username;

    @Column(name = "password_enc", nullable = false, length = 500)
    private String passwordEnc;

    @Column(length = 50)
    private String uid;

    @Column(name = "real_name", length = 50)
    private String realName;

    @Column(length = 100)
    private String school;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(name = "student_no", length = 50)
    private String studentNo;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "cookies_raw", columnDefinition = "TEXT")
    private String cookiesRaw;

    @Column(name = "cookie_expire")
    private LocalDateTime cookieExpire;

    @Column(nullable = false)
    @Builder.Default
    private Integer status = 1;

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
