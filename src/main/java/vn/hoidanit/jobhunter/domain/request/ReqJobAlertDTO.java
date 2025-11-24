package vn.hoidanit.jobhunter.domain.request;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqJobAlertDTO {
    private Long categoryId;
    private String location;
    private String experience;
    private Integer desiredSalary; // Deprecated, use minSalary and maxSalary instead
    private Integer minSalary;
    private Integer maxSalary;
    private List<Long> skillIds;
    private Boolean active;
}



