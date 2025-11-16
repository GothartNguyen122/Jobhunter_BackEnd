package vn.hoidanit.jobhunter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.transaction.Transactional;
import vn.hoidanit.jobhunter.service.EmailService;
import vn.hoidanit.jobhunter.service.SubscriberService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;

@RestController
@RequestMapping("/api/v1")
public class EmailController {

    private final EmailService emailService;
    private final SubscriberService subscriberService;

    public EmailController(EmailService emailService, SubscriberService subscriberService) {
        this.emailService = emailService;
        this.subscriberService = subscriberService;
    }

    /**
     * Endpoint để test gửi email thông báo công việc mới (chỉ dùng cho testing)
     * Gửi email cho tất cả users có skills phù hợp với các job mới trong 24h qua
     */
    @GetMapping("/email/test-notifications")
    @ApiMessage("Test send job notifications email")
    @Transactional
    public String testSendJobNotifications() {
        try {
            this.subscriberService.sendJobNotificationsToUsers();
            return "Đã gửi email thông báo thành công!";
        } catch (Exception e) {
            return "Lỗi khi gửi email: " + e.getMessage();
        }
    }
}
