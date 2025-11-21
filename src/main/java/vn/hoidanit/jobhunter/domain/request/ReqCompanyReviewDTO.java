package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqCompanyReviewDTO {

    @NotBlank(message = "decision không được để trống (APPROVED hoặc REJECTED)")
    private String decision;

    private String reason;

    public String normalizedDecision() {
        return this.decision == null ? "" : this.decision.trim().toUpperCase();
    }

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(this.normalizedDecision());
    }

    public boolean isRejected() {
        return "REJECTED".equalsIgnoreCase(this.normalizedDecision());
    }
}

