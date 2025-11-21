package vn.hoidanit.jobhunter.domain.response.favorite;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FavoriteJobDTO {
    private long favoriteId;
    private long jobId;
    private String jobName;
    private String companyName;
    private String companyLogo;
    private String location;
    private Double salary;
    private String level;
    private boolean active;
    private Instant savedAt;
}



