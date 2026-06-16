package chaoxing.autosign.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "t_sign_time_window")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SignTimeWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_id", nullable = false)
    private Long configId;

    /** 开始时间，格式 HH:mm */
    @Column(name = "start_time", nullable = false, length = 5)
    private String startTime;

    /** 结束时间，格式 HH:mm */
    @Column(name = "end_time", nullable = false, length = 5)
    private String endTime;
}
