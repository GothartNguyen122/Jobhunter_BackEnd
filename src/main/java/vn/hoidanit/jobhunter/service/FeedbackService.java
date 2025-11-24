package vn.hoidanit.jobhunter.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import vn.hoidanit.jobhunter.domain.Feedback;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqFeedbackDTO;
import vn.hoidanit.jobhunter.repository.FeedbackRepository;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

import java.util.List;
import java.util.Optional;

@Service
public class FeedbackService {
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final EmailService emailService;

    public FeedbackService(
            FeedbackRepository feedbackRepository,
            UserRepository userRepository,
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            EmailService emailService) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.emailService = emailService;
    }

    /**
     * Tạo feedback từ DTO (có thể từ user đã đăng nhập hoặc email)
     * Gửi email cho HR nếu feedback về job
     */
    @Transactional
    public Feedback createFeedback(ReqFeedbackDTO feedbackDTO, Long jobId, Long companyId) throws IdInvalidException {
        User user = findOrGetCurrentUser(feedbackDTO);
        if (user == null) {
            throw new IdInvalidException("Không tìm thấy người dùng. Vui lòng đăng nhập hoặc cung cấp email hợp lệ.");
        }

        Feedback feedback = new Feedback();
        feedback.setUser(user);
        feedback.setRating(feedbackDTO.getSatisfaction());
        feedback.setContent(feedbackDTO.getFeedback());

        Job job = null;
        Company company = null;

        // Set job hoặc company (một trong hai phải có)
        if (jobId != null) {
            job = this.jobRepository.findByIdWithSkills(jobId)
                    .orElseThrow(() -> new IdInvalidException("Job không tồn tại"));
            feedback.setJob(job);
            feedback.setCompany(null);
            company = job.getCompany(); // Lấy company từ job để gửi email
        } else if (companyId != null) {
            company = this.companyRepository.findById(companyId)
                    .orElseThrow(() -> new IdInvalidException("Company không tồn tại"));
            feedback.setCompany(company);
            feedback.setJob(null);
        } else {
            throw new IdInvalidException("Phải cung cấp jobId hoặc companyId");
        }

        Feedback savedFeedback = this.feedbackRepository.save(feedback);

        // Gửi email cho HR nếu feedback về job (async để không block)
        if (job != null && company != null) {
            sendFeedbackEmailToHR(savedFeedback, job, company, user);
        }

        return savedFeedback;
    }

    /**
     * Tìm user từ email hoặc current user (extract common logic)
     */
    private User findOrGetCurrentUser(ReqFeedbackDTO feedbackDTO) {
        if (StringUtils.hasText(feedbackDTO.getEmail())) {
            User user = this.userRepository.findByEmail(feedbackDTO.getEmail());
            if (user != null) {
                return user;
            }
        }

        Optional<String> currentUserEmail = SecurityUtil.getCurrentUserLogin();
        if (currentUserEmail.isPresent() && StringUtils.hasText(currentUserEmail.get())) {
            return this.userRepository.findByEmail(currentUserEmail.get());
        }

        return null;
    }

    /**
     * Gửi email cho HR về feedback (async để không block transaction)
     */
    @Async
    private void sendFeedbackEmailToHR(Feedback feedback, Job job, Company company, User candidate) {
        try {
            // Lấy danh sách HR của công ty
            List<User> hrUsers = this.userRepository.findByCompany(company);
            if (hrUsers == null || hrUsers.isEmpty()) {
                return;
            }

            String candidateName = candidate.getName() != null ? candidate.getName() : "Ứng viên";
            String candidateEmail = candidate.getEmail() != null ? candidate.getEmail() : "N/A";
            String jobName = job.getName() != null ? job.getName() : "N/A";
            String companyName = company.getName() != null ? company.getName() : "N/A";
            String rating = feedback.getRating() != null ? getRatingText(feedback.getRating()) : "N/A";
            String content = feedback.getContent() != null ? feedback.getContent() : "Không có nội dung";

            // Gửi email cho từng HR
            for (User hrUser : hrUsers) {
                if (hrUser.getEmail() == null || hrUser.getEmail().isBlank()) {
                    continue;
                }

                String hrName = hrUser.getName() != null ? hrUser.getName() : "Nhà tuyển dụng";
                String emailSubject = "[JobHunter] Phản hồi mới về công việc: " + jobName;
                String emailContent = buildFeedbackEmailContent(
                        hrName, candidateName, candidateEmail, jobName, companyName, rating, content);

                this.emailService.sendEmailSync(hrUser.getEmail(), emailSubject, emailContent, false, true);
            }
        } catch (Exception e) {
            System.err.println("ERROR sending feedback email to HR: " + e.getMessage());
            // Không throw exception để không ảnh hưởng đến việc lưu feedback
        }
    }

    /**
     * Build email content cho feedback
     */
    private String buildFeedbackEmailContent(String hrName, String candidateName, String candidateEmail,
            String jobName, String companyName, String rating, String content) {
        StringBuilder email = new StringBuilder();
        email.append("<html><body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>");
        email.append("<div style='max-width: 600px; margin: 0 auto; padding: 20px;'>");
        email.append("<h2 style='color: #1890ff;'>Phản hồi mới về công việc</h2>");
        email.append("<p>Xin chào <strong>").append(hrName).append("</strong>,</p>");
        email.append("<p>Bạn có phản hồi mới về công việc từ ứng viên:</p>");
        email.append("<div style='background: #f5f5f5; padding: 15px; border-radius: 5px; margin: 20px 0;'>");
        email.append("<p><strong>Công ty:</strong> ").append(companyName).append("</p>");
        email.append("<p><strong>Công việc:</strong> ").append(jobName).append("</p>");
        email.append("<p><strong>Ứng viên:</strong> ").append(candidateName).append("</p>");
        email.append("<p><strong>Email ứng viên:</strong> ").append(candidateEmail).append("</p>");
        email.append("<p><strong>Đánh giá:</strong> ").append(rating).append("</p>");
        email.append("</div>");
        email.append("<div style='background: #fff; padding: 15px; border-left: 4px solid #1890ff; margin: 20px 0;'>");
        email.append("<p><strong>Nội dung phản hồi:</strong></p>");
        email.append("<p style='white-space: pre-wrap;'>").append(content).append("</p>");
        email.append("</div>");
        email.append("<p style='margin-top: 30px; color: #666; font-size: 12px;'>");
        email.append("Trân trọng,<br>JobHunter Team");
        email.append("</p>");
        email.append("</div></body></html>");
        return email.toString();
    }

    /**
     * Convert rating number to text
     */
    private String getRatingText(Integer rating) {
        if (rating == null) {
            return "N/A";
        }
        return switch (rating) {
            case 1 -> "😡 Ghét";
            case 2 -> "😞 Không thích";
            case 3 -> "😐 Không ý kiến";
            case 4 -> "😊 Hài lòng";
            case 5 -> "😍 Rất hài lòng";
            default -> rating + " sao";
        };
    }

}
