package vn.hoidanit.jobhunter.domain.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResCompanyReviewDTO {
    private long companyId;
    private String companyName;
    private String decision;
    private String note;
    private List<ReviewerSummary> hrUsers;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReviewerSummary {
        private long userId;
        private String email;
        private String name;
        private String role;
    }
}

