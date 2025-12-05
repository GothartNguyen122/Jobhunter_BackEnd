package vn.hoidanit.jobhunter.controller;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.turkraft.springfilter.boot.Filter;
import com.turkraft.springfilter.builder.FilterBuilder;
import com.turkraft.springfilter.converter.FilterSpecificationConverter;
import jakarta.validation.Valid;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResCreateResumeDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResFetchResumeDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResUpdateResumeDTO;
import vn.hoidanit.jobhunter.service.ResumeService;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.service.JobService;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import vn.hoidanit.jobhunter.util.constant.ResumeStateEnum;

@RestController
@RequestMapping("/api/v1")
public class ResumeController {

    private final ResumeService resumeService;
    private final UserService userService;
    private final JobService jobService;
    private final JobRepository jobRepository;

    private final FilterBuilder filterBuilder;
    private final FilterSpecificationConverter filterSpecificationConverter;

    public ResumeController(
            ResumeService resumeService,
            UserService userService,
            JobService jobService,
            JobRepository jobRepository,
            FilterBuilder filterBuilder,
            FilterSpecificationConverter filterSpecificationConverter) {
        this.resumeService = resumeService;
        this.userService = userService;
        this.jobService = jobService;
        this.jobRepository = jobRepository;
        this.filterBuilder = filterBuilder;
        this.filterSpecificationConverter = filterSpecificationConverter;
    }

    @PostMapping("/resumes")
    @ApiMessage("Create a resume")
    public ResponseEntity<ResCreateResumeDTO> create(@Valid @RequestBody Resume resume) throws IdInvalidException {
        // check id exists
        boolean isIdExist = this.resumeService.checkResumeExistByUserAndJob(resume);
        if (!isIdExist) {
            throw new IdInvalidException("User id/Job id không tồn tại");
        }

        // create new resume
        return ResponseEntity.status(HttpStatus.CREATED).body(this.resumeService.create(resume));
    }

    @PutMapping("/resumes")
    @ApiMessage("Update a resume")
    @Transactional
    public ResponseEntity<ResUpdateResumeDTO> update(@RequestBody Resume resume) throws IdInvalidException {
        // check id exist
        Optional<Resume> reqResumeOptional = this.resumeService.fetchById(resume.getId());
        if (reqResumeOptional.isEmpty()) {
            throw new IdInvalidException("Resume với id = " + resume.getId() + " không tồn tại");
        }

        Resume reqResume = reqResumeOptional.get();
        ResumeStateEnum oldStatus = reqResume.getStatus();
        ResumeStateEnum newStatus = resume.getStatus();
        
        // Validation: Kiểm tra nếu muốn approve nhưng job đã hết số lượng
        if (oldStatus != ResumeStateEnum.APPROVED && newStatus == ResumeStateEnum.APPROVED) {
            Job job = reqResume.getJob();
            if (job != null) {
                // Lấy job mới nhất từ database để đảm bảo quantity chính xác
                Optional<Job> jobOptional = this.jobService.fetchJobById(job.getId());
                if (jobOptional.isPresent()) {
                    Job currentJob = jobOptional.get();
                    if (currentJob.getQuantity() <= 0) {
                        throw new IdInvalidException("Không thể phê duyệt ứng viên. Công việc đã hết số lượng tuyển dụng.");
                    }
                    if (!currentJob.isActive()) {
                        throw new IdInvalidException("Không thể phê duyệt ứng viên. Công việc đã đóng tuyển dụng.");
                    }
                }
            }
        }
        
        reqResume.setStatus(newStatus);
        ResUpdateResumeDTO result = this.resumeService.update(reqResume);
        
        // Xử lý thay đổi số lượng job dựa trên thay đổi trạng thái
        Job job = reqResume.getJob();
        if (job != null) {
            // Lấy job mới nhất từ database để đảm bảo tính nhất quán
            Optional<Job> jobOptional = this.jobService.fetchJobById(job.getId());
            if (jobOptional.isPresent()) {
                Job currentJob = jobOptional.get();
                boolean jobUpdated = false;
                
                // Trường hợp 1: Chuyển từ khác sang APPROVED - giảm số lượng
                if (oldStatus != ResumeStateEnum.APPROVED && newStatus == ResumeStateEnum.APPROVED) {
                    if (currentJob.getQuantity() > 0) {
                        currentJob.setQuantity(currentJob.getQuantity() - 1);
                        jobUpdated = true;
                        
                        // Nếu số lượng về 0, tự động đóng tuyển
                        if (currentJob.getQuantity() == 0) {
                            currentJob.setActive(false);
                        }
                    }
                }
                // Trường hợp 2: Chuyển từ APPROVED sang REJECTED hoặc PENDING - tăng lại số lượng
                else if (oldStatus == ResumeStateEnum.APPROVED && newStatus != ResumeStateEnum.APPROVED) {
                    // Chỉ tăng lại nếu job đang đóng (quantity = 0) và active = false
                    // Hoặc tăng lại bình thường nếu job vẫn còn active
                    currentJob.setQuantity(currentJob.getQuantity() + 1);
                    jobUpdated = true;
                    
                    // Nếu job đang đóng (quantity = 0, active = false) và giờ có quantity > 0, mở lại
                    if (currentJob.getQuantity() > 0 && !currentJob.isActive()) {
                        currentJob.setActive(true);
                    }
                }
                
                // Cập nhật job nếu có thay đổi
                if (jobUpdated) {
                    this.jobService.update(currentJob, currentJob);
                }
            }
        }
        
        return ResponseEntity.ok().body(result);
    }

    @DeleteMapping("/resumes/{id}")
    @ApiMessage("Xóa resume thành công")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) throws IdInvalidException {
        Optional<Resume> reqResumeOptional = this.resumeService.fetchById(id);
        if (reqResumeOptional.isEmpty()) {
            throw new IdInvalidException("Resume với id = " + id + " không tồn tại");
        }

        Resume resume = reqResumeOptional.get();
        String currentUserEmail = SecurityUtil.getCurrentUserLogin().orElse("");

        User currentUser = currentUserEmail.isEmpty()
                ? null
                : this.userService.handleGetUserByUsername(currentUserEmail);

        boolean isSuperAdmin = hasSuperAdminRole(currentUser);
        ensureValidStatus(resume);

        if (!isSuperAdmin) {
            if (currentUserEmail.isEmpty()) {
                throw new IdInvalidException("Không thể xác định người dùng hiện tại");
            }
            validateResumeOwnership(currentUserEmail, resume);
            ensurePendingStatus(resume);
        }

        this.resumeService.delete(id);
        return ResponseEntity.ok().body(null);
    }

    @GetMapping("/resumes/{id}")
    @ApiMessage("Fetch a resume by id")
    public ResponseEntity<ResFetchResumeDTO> fetchById(@PathVariable("id") long id) throws IdInvalidException {
        Optional<Resume> reqResumeOptional = this.resumeService.fetchById(id);
        if (reqResumeOptional.isEmpty()) {
            throw new IdInvalidException("Resume với id = " + id + " không tồn tại");
        }

        return ResponseEntity.ok().body(this.resumeService.getResume(reqResumeOptional.get()));
    }

    @GetMapping("/resumes")
    @ApiMessage("Fetch all resume with paginate")
    public ResponseEntity<ResultPaginationDTO> fetchAll(
            @Filter Specification<Resume> spec,
            Pageable pageable) {

        String email = SecurityUtil.getCurrentUserLogin().isPresent() == true
                ? SecurityUtil.getCurrentUserLogin().get()
                : "";
        User currentUser = this.userService.handleGetUserByUsername(email);

        // Nếu là SUPER_ADMIN thì không filter theo job/company
        if (currentUser != null && currentUser.getRole() != null
                && "SUPER_ADMIN".equals(currentUser.getRole().getName())) {
            return ResponseEntity.ok().body(this.resumeService.fetchAllResume(spec, pageable));
        }

        List<Long> arrJobIds = null;
        if (currentUser != null) {
            Company userCompany = currentUser.getCompany();
            if (userCompany != null) {
                // Query jobs từ repository thay vì dùng lazy loading
                List<Job> companyJobs = this.jobRepository.findAll((root, query, criteriaBuilder) -> 
                    criteriaBuilder.equal(root.get("company").get("id"), userCompany.getId())
                );
                if (companyJobs != null && companyJobs.size() > 0) {
                    arrJobIds = companyJobs.stream().map(x -> x.getId())
                            .collect(Collectors.toList());
                }
            }
        }

        Specification<Resume> jobInSpec = filterSpecificationConverter.convert(filterBuilder.field("job")
                .in(filterBuilder.input(arrJobIds != null ? arrJobIds : List.of())).get());

        Specification<Resume> finalSpec = jobInSpec.and(spec);

        return ResponseEntity.ok().body(this.resumeService.fetchAllResume(finalSpec, pageable));
    }

    @PostMapping("/resumes/by-user")
    @ApiMessage("Get list resumes by user")
    public ResponseEntity<ResultPaginationDTO> fetchResumeByUser(Pageable pageable) {

        return ResponseEntity.ok().body(this.resumeService.fetchResumeByUser(pageable));
    }

    @GetMapping("/resumes/check-applied/{jobId}")
    @ApiMessage("Check if user has applied to job")
    public ResponseEntity<Boolean> checkUserAppliedToJob(@PathVariable("jobId") long jobId) {
        boolean hasApplied = this.resumeService.hasUserAppliedToJob(jobId);
        return ResponseEntity.ok().body(hasApplied);
    }

    @GetMapping("/resumes/by-job/{jobId}")
    @ApiMessage("Fetch resumes by job with pagination and matching score")
    public ResponseEntity<ResultPaginationDTO> fetchResumesByJob(
            @PathVariable("jobId") long jobId,
            @Filter Specification<Resume> spec,
            Pageable pageable) throws IdInvalidException {
        
        // Kiểm tra job có tồn tại không
        Optional<Job> jobOptional = this.jobService.fetchJobById(jobId);
        if (jobOptional.isEmpty()) {
            throw new IdInvalidException("Job với id = " + jobId + " không tồn tại");
        }

        // Kiểm tra quyền truy cập: HR chỉ xem được resume của job thuộc công ty mình
        String email = SecurityUtil.getCurrentUserLogin().isPresent() == true
                ? SecurityUtil.getCurrentUserLogin().get()
                : "";
        User currentUser = this.userService.handleGetUserByUsername(email);

        // Nếu là SUPER_ADMIN thì không cần check
        if (currentUser != null && currentUser.getRole() != null
                && "SUPER_ADMIN".equals(currentUser.getRole().getName())) {
            // SUPER_ADMIN có thể xem tất cả
        } else {
            // HR chỉ xem được job của công ty mình
            if (currentUser != null && currentUser.getCompany() != null) {
                Job job = jobOptional.get();
                Long jobCompanyId = job.getCompany() != null ? job.getCompany().getId() : null;
                Long userCompanyId = currentUser.getCompany().getId();
                if (jobCompanyId == null || !jobCompanyId.equals(userCompanyId)) {
                    throw new IdInvalidException("Bạn không có quyền xem resume của job này");
                }
            }
        }

        // Tạo specification filter theo job sử dụng Criteria API
        Specification<Resume> jobSpec = (root, query, criteriaBuilder) -> 
            criteriaBuilder.equal(root.get("job").get("id"), jobId);

        // Combine với spec từ query params (status filter, etc.)
        Specification<Resume> finalSpec = jobSpec.and(spec);

        return ResponseEntity.ok().body(this.resumeService.fetchAllResume(finalSpec, pageable));
    }

    private boolean hasSuperAdminRole(User user) {
        return user != null
                && user.getRole() != null
                && "SUPER_ADMIN".equalsIgnoreCase(user.getRole().getName());
    }

    private void validateResumeOwnership(String currentUserEmail, Resume resume) throws IdInvalidException {
        if (!currentUserEmail.equals(resume.getEmail())) {
            throw new IdInvalidException("Bạn không có quyền xóa CV này");
        }
    }

    private void ensureValidStatus(Resume resume) throws IdInvalidException {
        if (resume.getStatus() == null) {
            throw new IdInvalidException("Trạng thái CV không hợp lệ");
        }
    }

    private void ensurePendingStatus(Resume resume) throws IdInvalidException {
        if (resume.getStatus() != ResumeStateEnum.PENDING) {
            throw new IdInvalidException("Chỉ có thể rút CV với trạng thái 'Chờ xử lý'");
        }
    }
}
