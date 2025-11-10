package vn.hoidanit.jobhunter.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.hoidanit.jobhunter.domain.request.ReqFeedbackDTO;
import vn.hoidanit.jobhunter.domain.response.ResFeedbackDTO;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;

@RestController
@RequestMapping("/api/v1")
public class FeedbackController {

    @PostMapping("/feedback")
    @ApiMessage("Submit feedback")
    public ResponseEntity<ResFeedbackDTO> submitFeedback(@Valid @RequestBody ReqFeedbackDTO feedbackDTO) {
        try {
            // Log feedback for now (you can save to database later)
            System.out.println("Received feedback:");
            System.out.println("Satisfaction: " + feedbackDTO.getSatisfaction());
            System.out.println("Feedback: " + feedbackDTO.getFeedback());
            System.out.println("Email: " + feedbackDTO.getEmail());
            System.out.println("Timestamp: " + feedbackDTO.getTimestamp());

            ResFeedbackDTO response = new ResFeedbackDTO();
            response.setMessage("Cảm ơn bạn đã gửi phản hồi! Chúng tôi sẽ xem xét và cải thiện dịch vụ.");
            response.setSuccess(true);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ResFeedbackDTO response = new ResFeedbackDTO();
            response.setMessage("Có lỗi xảy ra khi gửi phản hồi. Vui lòng thử lại sau.");
            response.setSuccess(false);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}

