package vn.hoidanit.jobhunter.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.Category;
import vn.hoidanit.jobhunter.domain.JobAlert;
import vn.hoidanit.jobhunter.domain.Skill;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqJobAlertDTO;
import vn.hoidanit.jobhunter.domain.response.ResJobAlertDTO;
import vn.hoidanit.jobhunter.repository.CategoryRepository;
import vn.hoidanit.jobhunter.repository.JobAlertRepository;
import vn.hoidanit.jobhunter.repository.SkillRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@Service
public class JobAlertManagementService {

    private static final int MAX_SALARY = 100_000_000; // 100 triệu VND

    private final JobAlertRepository jobAlertRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final SkillRepository skillRepository;
    private final JobAlertService jobAlertService;

    public JobAlertManagementService(
            JobAlertRepository jobAlertRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            SkillRepository skillRepository,
            JobAlertService jobAlertService) {
        this.jobAlertRepository = jobAlertRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.skillRepository = skillRepository;
        this.jobAlertService = jobAlertService;
    }

    private User getCurrentUser() throws IdInvalidException {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new IdInvalidException("Vui lòng đăng nhập để sử dụng chức năng này"));
        User user = this.userRepository.findByEmail(email);
        if (user == null) {
            throw new IdInvalidException("Không tìm thấy thông tin người dùng");
        }
        return user;
    }

    public List<ResJobAlertDTO> getMyJobAlerts() throws IdInvalidException {
        User currentUser = getCurrentUser();
        List<JobAlert> alerts = this.jobAlertRepository.findByUser(currentUser);
        return alerts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public ResJobAlertDTO createJobAlert(ReqJobAlertDTO dto) throws IdInvalidException {
        User currentUser = getCurrentUser();

        // Validate salary range
        validateSalaryRange(dto.getMinSalary(), dto.getMaxSalary());

        JobAlert alert = new JobAlert();
        alert.setUser(currentUser);
        alert.setEmail(currentUser.getEmail());

        if (dto.getCategoryId() != null) {
            Category category = this.categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new IdInvalidException("Không tìm thấy danh mục"));
            alert.setCategory(category);
        }

        setAlertFields(alert, dto);

        JobAlert saved = this.jobAlertRepository.save(alert);
        
        // Nếu tạo mới với active = true, gửi email ngay
        if (saved.isActive()) {
            this.jobAlertService.sendEmailForAlert(saved);
        }
        
        return convertToDTO(saved);
    }

    @Transactional
    public ResJobAlertDTO updateJobAlert(long id, ReqJobAlertDTO dto) throws IdInvalidException {
        User currentUser = getCurrentUser();
        JobAlert alert = this.jobAlertRepository.findById(id)
                .orElseThrow(() -> new IdInvalidException("Không tìm thấy thông báo việc làm"));

        // Check ownership
        if (alert.getUser() == null || alert.getUser().getId() != currentUser.getId()) {
            throw new IdInvalidException("Bạn không có quyền chỉnh sửa thông báo này");
        }

        // Validate salary range (merge với giá trị hiện tại nếu không có trong DTO)
        Integer minSalary = dto.getMinSalary() != null ? dto.getMinSalary() : alert.getMinSalary();
        Integer maxSalary = dto.getMaxSalary() != null ? dto.getMaxSalary() : alert.getMaxSalary();
        validateSalaryRange(minSalary, maxSalary);

        if (dto.getCategoryId() != null) {
            Category category = this.categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new IdInvalidException("Không tìm thấy danh mục"));
            alert.setCategory(category);
        } else {
            alert.setCategory(null);
        }

        setAlertFields(alert, dto);

        JobAlert saved = this.jobAlertRepository.save(alert);
        
        // Nếu alert đang active sau khi cập nhật, gửi email với tiêu chí mới
        if (saved.isActive()) {
            this.jobAlertService.sendEmailForAlert(saved);
        }
        
        return convertToDTO(saved);
    }

    @Transactional
    public void deleteJobAlert(long id) throws IdInvalidException {
        User currentUser = getCurrentUser();
        JobAlert alert = this.jobAlertRepository.findById(id)
                .orElseThrow(() -> new IdInvalidException("Không tìm thấy thông báo việc làm"));

        // Check ownership
        if (alert.getUser() == null || alert.getUser().getId() != currentUser.getId()) {
            throw new IdInvalidException("Bạn không có quyền xóa thông báo này");
        }

        this.jobAlertRepository.delete(alert);
    }

    @Transactional
    public ResJobAlertDTO toggleJobAlert(long id) throws IdInvalidException {
        User currentUser = getCurrentUser();
        JobAlert alert = this.jobAlertRepository.findById(id)
                .orElseThrow(() -> new IdInvalidException("Không tìm thấy thông báo việc làm"));

        // Check ownership
        if (alert.getUser() == null || alert.getUser().getId() != currentUser.getId()) {
            throw new IdInvalidException("Bạn không có quyền thay đổi trạng thái thông báo này");
        }

        boolean wasActive = alert.isActive();
        alert.setActive(!alert.isActive());
        JobAlert saved = this.jobAlertRepository.save(alert);
        
        // Nếu toggle thành active, gửi email ngay lập tức
        if (!wasActive && saved.isActive()) {
            this.jobAlertService.sendEmailForAlert(saved);
        }
        
        return convertToDTO(saved);
    }

    private ResJobAlertDTO convertToDTO(JobAlert alert) {
        ResJobAlertDTO dto = new ResJobAlertDTO();
        dto.setId(alert.getId());
        dto.setLocation(alert.getLocation());
        dto.setExperience(alert.getExperience());
        dto.setDesiredSalary(alert.getDesiredSalary()); // Deprecated
        dto.setMinSalary(alert.getMinSalary());
        dto.setMaxSalary(alert.getMaxSalary());
        dto.setActive(alert.isActive());
        dto.setCreatedAt(alert.getCreatedAt());
        dto.setUpdatedAt(alert.getUpdatedAt());

        if (alert.getCategory() != null) {
            dto.setCategoryId(alert.getCategory().getId());
            dto.setCategoryName(alert.getCategory().getName());
        }

        if (alert.getSkills() != null && !alert.getSkills().isEmpty()) {
            List<ResJobAlertDTO.SkillSummary> skillSummaries = alert.getSkills().stream()
                    .map(skill -> new ResJobAlertDTO.SkillSummary(skill.getId(), skill.getName()))
                    .collect(Collectors.toList());
            dto.setSkills(skillSummaries);
        }

        return dto;
    }

    /**
     * Validate salary range
     * @param minSalary Minimum salary in VND
     * @param maxSalary Maximum salary in VND
     * @throws IdInvalidException if validation fails
     */
    private void validateSalaryRange(Integer minSalary, Integer maxSalary) throws IdInvalidException {
        if (minSalary != null && minSalary < 0) {
            throw new IdInvalidException("Mức lương tối thiểu phải lớn hơn hoặc bằng 0");
        }
        if (maxSalary != null && maxSalary > MAX_SALARY) {
            throw new IdInvalidException("Mức lương tối đa không được vượt quá 100 triệu VND");
        }
        if (minSalary != null && maxSalary != null && minSalary > maxSalary) {
            throw new IdInvalidException("Mức lương tối thiểu không được lớn hơn mức lương tối đa");
        }
    }

    /**
     * Set alert fields from DTO
     */
    private void setAlertFields(JobAlert alert, ReqJobAlertDTO dto) {
        alert.setLocation(dto.getLocation());
        alert.setExperience(dto.getExperience());
        alert.setDesiredSalary(dto.getDesiredSalary()); // Deprecated, keep for backward compatibility
        alert.setMinSalary(dto.getMinSalary());
        alert.setMaxSalary(dto.getMaxSalary());
        
        if (dto.getActive() != null) {
            alert.setActive(dto.getActive());
        } else if (alert.getId() == 0) { // New alert, default to true
            alert.setActive(true);
        }

        if (dto.getSkillIds() != null) {
            if (dto.getSkillIds().isEmpty()) {
                alert.setSkills(new ArrayList<>());
            } else {
                List<Skill> skills = this.skillRepository.findByIdIn(dto.getSkillIds());
                alert.setSkills(new ArrayList<>(skills)); // Đảm bảo là mutable list
            }
        } else if (alert.getId() == 0) { // New alert, default to empty list
            alert.setSkills(new ArrayList<>());
        }
    }
}

