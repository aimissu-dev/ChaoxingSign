package chaoxing.autosign.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class SignConfigRequest {

    @NotBlank(message = "课程名称不能为空")
    private String courseName;

    private String signCode;

    private String address;

    private BigDecimal latitude;

    private BigDecimal longitude;

    /** 兼容旧字段：单时间窗口开始时间 HH:mm */
    @Deprecated
    private String signStartTime;

    /** 兼容旧字段：单时间窗口结束时间 HH:mm */
    @Deprecated
    private String signEndTime;

    /** 多时间窗口列表（优先使用此字段） */
    private List<TimeWindowItem> timeWindows = new ArrayList<>();

    @Data
    public static class TimeWindowItem {
        /** HH:mm */
        @NotBlank(message = "开始时间不能为空")
        private String startTime;
        /** HH:mm */
        @NotBlank(message = "结束时间不能为空")
        private String endTime;
    }
}
