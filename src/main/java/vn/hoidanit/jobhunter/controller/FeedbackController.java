package vn.hoidanit.jobhunter.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.hoidanit.jobhunter.domain.request.ReqFeedbackDTO;
import vn.hoidanit.jobhunter.domain.response.ResFeedbackDTO;
import vn.hoidanit.jobhunter.service.FeedbackService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@RestController
@RequestMapping("/api/v1")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /**
     * Submit feedback - có thể gửi kèm jobId hoặc companyId
     * Nếu không có jobId/companyId, sẽ là general feedback (cần sửa DB constraint)
     */
    @PostMapping("/feedback")
    @ApiMessage("Submit feedback")
    public ResponseEntity<ResFeedbackDTO> submitFeedback(
            @Valid @RequestBody ReqFeedbackDTO feedbackDTO,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) Long companyId) {
        try {
            // Lưu feedback vào database
            if (jobId != null || companyId != null) {
                this.feedbackService.createFeedback(feedbackDTO, jobId, companyId);
            } else {
                // General feedback - tạm thời không hỗ trợ do DB constraint
                // Có thể tạo một "general" company hoặc job để lưu
                throw new IdInvalidException("Vui lòng cung cấp jobId hoặc companyId");
            }

            ResFeedbackDTO response = new ResFeedbackDTO();
            response.setMessage("Cảm ơn bạn đã gửi phản hồi! Chúng tôi sẽ xem xét và cải thiện dịch vụ.");
            response.setSuccess(true);

            return ResponseEntity.ok(response);
        } catch (IdInvalidException e) {
            ResFeedbackDTO response = new ResFeedbackDTO();
            response.setMessage(e.getMessage());
            response.setSuccess(false);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            ResFeedbackDTO response = new ResFeedbackDTO();
            response.setMessage("Có lỗi xảy ra khi gửi phản hồi. Vui lòng thử lại sau.");
            response.setSuccess(false);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}

