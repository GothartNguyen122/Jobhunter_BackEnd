package vn.hoidanit.jobhunter.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqCompanyReviewDTO;
import vn.hoidanit.jobhunter.domain.response.ResCompanyReviewDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.repository.RoleRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@Service
public class CompanyService {

    private static final String ROLE_HR = "HR";
    private static final String ROLE_HR_PENDING = "HR_PENDING";
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailService emailService;

    public CompanyService(
            CompanyRepository companyRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            EmailService emailService) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.emailService = emailService;
    }

    public Company handleCreateCompany(Company c) {
        return this.companyRepository.save(c);
    }

    public ResultPaginationDTO handleGetCompany(Specification<Company> spec, Pageable pageable) {
        // Check if current user is HR, if so, filter by their company
        Specification<Company> finalSpec = spec;
        try {
            String email = SecurityUtil.getCurrentUserLogin().orElse(null);
            if (email != null) {
                User currentUser = this.userRepository.findByEmail(email);
                if (isHrLike(currentUser)) {
                    // HR chỉ thấy công ty của mình
                    if (currentUser.getCompany() != null) {
                        Specification<Company> hrSpec = (root, query, criteriaBuilder) -> 
                            criteriaBuilder.equal(root.get("id"), currentUser.getCompany().getId());
                        
                        // Combine with existing spec if any
                        if (spec != null) {
                            finalSpec = spec.and(hrSpec);
                        } else {
                            finalSpec = hrSpec;
                        }
                    } else {
                        // HR chưa có company, trả về empty result
                        finalSpec = (root, query, criteriaBuilder) -> criteriaBuilder.disjunction();
                    }
                }
            }
        } catch (Exception e) {
            // If error getting current user, continue with original spec
        }

        Page<Company> pCompany = this.companyRepository.findAll(finalSpec, pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();

        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());

        mt.setPages(pCompany.getTotalPages());
        mt.setTotal(pCompany.getTotalElements());

        rs.setMeta(mt);
        rs.setResult(pCompany.getContent());
        return rs;
    }

    public Company handleUpdateCompany(Company c) {
        Optional<Company> companyOptional = this.companyRepository.findById(c.getId());
        if (companyOptional.isPresent()) {
            Company currentCompany = companyOptional.get();
            currentCompany.setLogo(c.getLogo());
            currentCompany.setName(c.getName());
            currentCompany.setDescription(c.getDescription());
            currentCompany.setAddress(c.getAddress());
            return this.companyRepository.save(currentCompany);
        }
        return null;
    }

    public void handleDeleteCompany(long id) {
        Optional<Company> comOptional = this.companyRepository.findById(id);
        if (comOptional.isPresent()) {
            Company com = comOptional.get();
            // fetch all user belong to this company
            List<User> users = this.userRepository.findByCompany(com);
            this.userRepository.deleteAll(users);
        }

        this.companyRepository.deleteById(id);
    }

    public Optional<Company> findById(long id) {
        return this.companyRepository.findById(id);
    }

    /**
     * HR creates or updates their own company
     * If HR already has a company, update it; otherwise create a new one
     */
    public Company handleCreateOrUpdateMyCompany(Company reqCompany) throws IdInvalidException {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new IdInvalidException("Không tìm thấy thông tin đăng nhập"));
        
        User currentUser = this.userRepository.findByEmail(email);
        if (currentUser == null) {
            throw new IdInvalidException("Không tìm thấy người dùng");
        }

        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "UNKNOWN";
        if (!isHrLike(currentUser)) {
            throw new IdInvalidException("Chỉ nhà tuyển dụng mới có quyền tạo/cập nhật công ty (Role hiện tại: " + roleName + ")");
        }

        // If user already has a company, update it
        if (currentUser.getCompany() != null) {
            Company existingCompany = currentUser.getCompany();
            existingCompany.setName(reqCompany.getName());
            existingCompany.setAddress(reqCompany.getAddress());
            existingCompany.setDescription(reqCompany.getDescription());
            if (reqCompany.getLogo() != null) {
                existingCompany.setLogo(reqCompany.getLogo());
            }
            return this.companyRepository.save(existingCompany);
        } else {
            // Create new company and assign to user
            Company newCompany = new Company();
            newCompany.setName(reqCompany.getName());
            newCompany.setAddress(reqCompany.getAddress());
            newCompany.setDescription(reqCompany.getDescription());
            newCompany.setLogo(reqCompany.getLogo() != null ? reqCompany.getLogo() : "default-logo.png");
            
            Company savedCompany = this.companyRepository.save(newCompany);
            
            // Assign company to user
            currentUser.setCompany(savedCompany);
            this.userRepository.save(currentUser);
            
            return savedCompany;
        }
    }

    /**
     * HR gets their own company
     */
    public Optional<Company> getMyCompany() throws IdInvalidException {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new IdInvalidException("Không tìm thấy thông tin đăng nhập"));
        
        User currentUser = this.userRepository.findByEmail(email);
        if (currentUser == null) {
            throw new IdInvalidException("Không tìm thấy người dùng");
        }

        // Check if user is HR (case-insensitive)
        if (!isHrLike(currentUser)) {
            throw new IdInvalidException("Chỉ nhà tuyển dụng mới có quyền xem công ty");
        }

        if (currentUser.getCompany() != null) {
            return Optional.of(currentUser.getCompany());
        }
        
        return Optional.empty();
    }

    @Transactional
    public ResCompanyReviewDTO handleReviewCompany(long companyId, ReqCompanyReviewDTO request) throws IdInvalidException {
        String reviewerEmail = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new IdInvalidException("Không tìm thấy thông tin đăng nhập"));

        User reviewer = this.userRepository.findByEmail(reviewerEmail);
        if (reviewer == null || reviewer.getRole() == null
                || !ROLE_SUPER_ADMIN.equalsIgnoreCase(reviewer.getRole().getName())) {
            throw new IdInvalidException("Chỉ quản trị viên mới có quyền phê duyệt thông tin công ty");
        }

        Company company = this.companyRepository.findById(companyId)
                .orElseThrow(() -> new IdInvalidException("Không tìm thấy công ty với id = " + companyId));

        List<User> hrUsers = this.userRepository.findByCompany(company);
        if (hrUsers == null || hrUsers.isEmpty()) {
            throw new IdInvalidException("Không tìm thấy HR nào gắn với công ty này");
        }

        String decision = request.normalizedDecision();
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new IdInvalidException("decision phải là APPROVED hoặc REJECTED");
        }

        if ("REJECTED".equals(decision) && !StringUtils.hasText(request.getReason())) {
            throw new IdInvalidException("Vui lòng nhập lý do từ chối");
        }

        Role targetRole = this.getRoleOrThrow("APPROVED".equals(decision) ? ROLE_HR : ROLE_HR_PENDING);

        // Update roles and send emails in one loop
        List<User> usersToPersist = new ArrayList<>();
        for (User hrUser : hrUsers) {
            String currentRoleName = hrUser.getRole() != null ? hrUser.getRole().getName() : "";
            if (!targetRole.getName().equalsIgnoreCase(currentRoleName)) {
                hrUser.setRole(targetRole);
                usersToPersist.add(hrUser);
            }
            // Send email asynchronously to avoid blocking
            this.sendReviewEmail(hrUser, company, decision, request.getReason());
        }
        if (!usersToPersist.isEmpty()) {
            this.userRepository.saveAll(usersToPersist);
        }

        ResCompanyReviewDTO response = new ResCompanyReviewDTO();
        response.setCompanyId(company.getId());
        response.setCompanyName(company.getName());
        response.setDecision(decision);
        response.setNote("REJECTED".equals(decision) ? request.getReason() : "Thông tin công ty đã được phê duyệt");
        response.setHrUsers(hrUsers.stream()
                .map(user -> new ResCompanyReviewDTO.ReviewerSummary(
                        user.getId(),
                        user.getEmail(),
                        user.getName(),
                        user.getRole() != null ? user.getRole().getName() : ""))
                .collect(Collectors.toList()));

        return response;
    }

    private boolean isHrLike(User user) {
        if (user == null || user.getRole() == null || user.getRole().getName() == null) {
            return false;
        }
        String roleName = user.getRole().getName();
        return ROLE_HR.equalsIgnoreCase(roleName) || ROLE_HR_PENDING.equalsIgnoreCase(roleName);
    }

    private Role getRoleOrThrow(String roleName) throws IdInvalidException {
        Role role = this.roleRepository.findByName(roleName);
        if (role == null) {
            throw new IdInvalidException("Role " + roleName + " chưa được cấu hình. Vui lòng tạo role này trước.");
        }
        return role;
    }

    @org.springframework.scheduling.annotation.Async
    private void sendReviewEmail(User hrUser, Company company, String decision, String reason) {
        if (hrUser == null || !StringUtils.hasText(hrUser.getEmail())) {
            return;
        }

        try {
            String subject;
            StringBuilder content = new StringBuilder();
            String companyName = company != null ? company.getName() : "của bạn";

            content.append("Xin chào ").append(hrUser.getName() != null ? hrUser.getName() : "bạn").append(",\n\n");
            if ("APPROVED".equals(decision)) {
                subject = "[JobHunter] Thông tin công ty đã được phê duyệt";
                content.append("Thông tin công ty ").append(companyName)
                        .append(" đã được admin xác thực thành công. Bạn có thể sử dụng đầy đủ chức năng HR (đăng tin, quản lý hồ sơ).\n\n")
                        .append("Chúc bạn tuyển dụng hiệu quả!");
            } else {
                subject = "[JobHunter] Thông tin công ty cần bổ sung";
                content.append("Thông tin công ty ").append(companyName)
                        .append(" chưa được phê duyệt. Vui lòng cập nhật lại theo góp ý bên dưới và gửi lại để được xét duyệt:\n\n")
                        .append(reason == null ? "" : reason.trim()).append("\n\n")
                        .append("Sau khi cập nhật, vui lòng thông báo để admin xem xét lại.");
            }
            content.append("\n\nTrân trọng,\nJobHunter Team");

            this.emailService.sendEmailSync(hrUser.getEmail(), subject, content.toString(), false, false);
        } catch (Exception e) {
            // Log error but don't fail the review operation
            System.err.println("Failed to send review email to " + hrUser.getEmail() + ": " + e.getMessage());
        }
    }
}