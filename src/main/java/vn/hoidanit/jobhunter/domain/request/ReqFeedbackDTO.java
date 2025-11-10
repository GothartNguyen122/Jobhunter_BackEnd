package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ReqFeedbackDTO {
    
    @NotNull(message = "Mức độ hài lòng không được để trống")
    @Min(value = 1, message = "Mức độ hài lòng phải từ 1-5")
    @Max(value = 5, message = "Mức độ hài lòng phải từ 1-5")
    private Integer satisfaction;
    
    @NotBlank(message = "Ý kiến không được để trống")
    @Size(max = 5000, message = "Ý kiến không được vượt quá 5000 ký tự")
    private String feedback;
    
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;
    
    private String timestamp;

    public ReqFeedbackDTO() {}

    public ReqFeedbackDTO(Integer satisfaction, String feedback, String email, String timestamp) {
        this.satisfaction = satisfaction;
        this.feedback = feedback;
        this.email = email;
        this.timestamp = timestamp;
    }

    public Integer getSatisfaction() {
        return satisfaction;
    }

    public void setSatisfaction(Integer satisfaction) {
        this.satisfaction = satisfaction;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}

