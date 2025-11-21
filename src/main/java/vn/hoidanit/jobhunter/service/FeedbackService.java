package vn.hoidanit.jobhunter.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import java.util.Optional;

@Service
public class FeedbackService {
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;

    public FeedbackService(
            FeedbackRepository feedbackRepository,
            UserRepository userRepository,
            JobRepository jobRepository,
            CompanyRepository companyRepository) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Tạo feedback từ DTO (có thể từ user đã đăng nhập hoặc email)
     */
    @Transactional
    public Feedback createFeedback(ReqFeedbackDTO feedbackDTO, Long jobId, Long companyId) throws IdInvalidException {
        Feedback feedback = new Feedback();

        // Tìm user từ email hoặc từ current user
        User user = null;
        if (feedbackDTO.getEmail() != null && !feedbackDTO.getEmail().isBlank()) {
            user = this.userRepository.findByEmail(feedbackDTO.getEmail());
        }
        
        // Nếu không tìm thấy từ email, thử lấy từ current user
        if (user == null) {
            Optional<String> currentUserEmail = SecurityUtil.getCurrentUserLogin();
            if (currentUserEmail.isPresent() && !currentUserEmail.get().isEmpty()) {
                user = this.userRepository.findByEmail(currentUserEmail.get());
            }
        }

        // Nếu vẫn không có user, tạo user mới hoặc throw exception
        // Ở đây ta cho phép feedback không có user (guest feedback)
        // Nhưng DB yêu cầu user_id NOT NULL, nên cần xử lý
        if (user == null) {
            throw new IdInvalidException("Không tìm thấy người dùng. Vui lòng đăng nhập hoặc cung cấp email hợp lệ.");
        }

        feedback.setUser(user);
        feedback.setRating(feedbackDTO.getSatisfaction());
        feedback.setContent(feedbackDTO.getFeedback());

        // Set job hoặc company (một trong hai phải có)
        if (jobId != null) {
            Job job = this.jobRepository.findById(jobId)
                    .orElseThrow(() -> new IdInvalidException("Job không tồn tại"));
            feedback.setJob(job);
            feedback.setCompany(null);
        } else if (companyId != null) {
            Company company = this.companyRepository.findById(companyId)
                    .orElseThrow(() -> new IdInvalidException("Company không tồn tại"));
            feedback.setCompany(company);
            feedback.setJob(null);
        } else {
            // Nếu không có jobId và companyId, có thể là general feedback
            // Nhưng DB constraint yêu cầu một trong hai phải có
            // Có thể tạo một "general" company hoặc job, hoặc bỏ constraint
            throw new IdInvalidException("Phải cung cấp jobId hoặc companyId");
        }

        return this.feedbackRepository.save(feedback);
    }

    /**
     * Tạo feedback đơn giản từ DTO (không cần job/company - general feedback)
     * Note: Cần điều chỉnh DB constraint nếu muốn cho phép cả job và company đều null
     */
    @Transactional
    public Feedback createGeneralFeedback(ReqFeedbackDTO feedbackDTO) throws IdInvalidException {
        // Tìm user từ email
        User user = null;
        if (feedbackDTO.getEmail() != null && !feedbackDTO.getEmail().isBlank()) {
            user = this.userRepository.findByEmail(feedbackDTO.getEmail());
        }
        
        if (user == null) {
            Optional<String> currentUserEmail = SecurityUtil.getCurrentUserLogin();
            if (currentUserEmail.isPresent() && !currentUserEmail.get().isEmpty()) {
                user = this.userRepository.findByEmail(currentUserEmail.get());
            }
        }

        if (user == null) {
            throw new IdInvalidException("Không tìm thấy người dùng. Vui lòng đăng nhập hoặc cung cấp email hợp lệ.");
        }

        Feedback feedback = new Feedback();
        feedback.setUser(user);
        feedback.setRating(feedbackDTO.getSatisfaction());
        feedback.setContent(feedbackDTO.getFeedback());
        feedback.setJob(null);
        feedback.setCompany(null);

        // Note: DB constraint yêu cầu một trong hai (job hoặc company) phải có
        // Nếu muốn cho phép general feedback, cần sửa DB constraint
        // Tạm thời throw exception
        throw new IdInvalidException("General feedback chưa được hỗ trợ. Vui lòng cung cấp jobId hoặc companyId.");
    }
}

