package vn.hoidanit.jobhunter.domain.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResJobAlertDTO {
    private long id;
    private Long categoryId;
    private String categoryName;
    private String location;
    private String experience;
    private Integer desiredSalary; // Deprecated
    private Integer minSalary;
    private Integer maxSalary;
    private List<SkillSummary> skills;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SkillSummary {
        private long id;
        private String name;
    }
}



