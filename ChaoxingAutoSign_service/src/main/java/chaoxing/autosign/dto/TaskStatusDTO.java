package chaoxing.autosign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusDTO {

    private String status;
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;
    private String message;
}
