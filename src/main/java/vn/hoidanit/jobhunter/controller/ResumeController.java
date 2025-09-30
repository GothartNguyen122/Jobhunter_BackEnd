package vn.hoidanit.jobhunter.controller;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private final FilterBuilder filterBuilder;
    private final FilterSpecificationConverter filterSpecificationConverter;

    public ResumeController(
            ResumeService resumeService,
            UserService userService,
            FilterBuilder filterBuilder,
            FilterSpecificationConverter filterSpecificationConverter) {
        this.resumeService = resumeService;
        this.userService = userService;
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
    public ResponseEntity<ResUpdateResumeDTO> update(@RequestBody Resume resume) throws IdInvalidException {
        // check id exist
        Optional<Resume> reqResumeOptional = this.resumeService.fetchById(resume.getId());
        if (reqResumeOptional.isEmpty()) {
            throw new IdInvalidException("Resume với id = " + resume.getId() + " không tồn tại");
        }

        Resume reqResume = reqResumeOptional.get();
        reqResume.setStatus(resume.getStatus());

        return ResponseEntity.ok().body(this.resumeService.update(reqResume));
    }

    @DeleteMapping("/resumes/{id}")
    @ApiMessage("Delete a resume by id")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) throws IdInvalidException {
        System.out.println("=== DELETE RESUME DEBUG ===");
        System.out.println("Resume ID: " + id);

        Optional<Resume> reqResumeOptional = this.resumeService.fetchById(id);
        if (reqResumeOptional.isEmpty()) {
            System.out.println("Resume not found with ID: " + id);
            throw new IdInvalidException("Resume với id = " + id + " không tồn tại");
        }

        Resume resume = reqResumeOptional.get();
        System.out.println("Found resume - Email: " + resume.getEmail() + ", Status: " + resume.getStatus());

        // Kiểm tra quyền sở hữu - chỉ cho phép user xóa CV của chính mình
        String currentUserEmail = SecurityUtil.getCurrentUserLogin().orElse("");
        System.out.println("Current user email: " + currentUserEmail);
        System.out.println("Resume email: " + resume.getEmail());
        System.out.println("Email match: " + currentUserEmail.equals(resume.getEmail()));

        if (currentUserEmail.isEmpty()) {
            System.out.println("Current user email is empty");
            throw new IdInvalidException("Không thể xác định người dùng hiện tại");
        }

        if (!currentUserEmail.equals(resume.getEmail())) {
            System.out.println("Permission denied - Email mismatch");
            throw new IdInvalidException("Bạn không có quyền xóa CV này");
        }

        // Kiểm tra trạng thái - chỉ cho phép xóa CV có trạng thái PENDING
        System.out.println("Status check - Expected: PENDING, Actual: " + resume.getStatus());
        System.out.println("Status equals: " + (resume.getStatus() == ResumeStateEnum.PENDING));

        if (resume.getStatus() == null) {
            System.out.println("Resume status is null");
            throw new IdInvalidException("Trạng thái CV không hợp lệ");
        }

        // So sánh enum thay vì string
        if (resume.getStatus() != ResumeStateEnum.PENDING) {
            System.out.println("Status check failed - Current status: " + resume.getStatus());
            throw new IdInvalidException("Chỉ có thể rút CV với trạng thái 'Chờ xử lý'");
        }

        System.out.println("All checks passed - Deleting resume");
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
                List<Job> companyJobs = userCompany.getJobs();
                if (companyJobs != null && companyJobs.size() > 0) {
                    arrJobIds = companyJobs.stream().map(x -> x.getId())
                            .collect(Collectors.toList());
                }
            }
        }

        Specification<Resume> jobInSpec = filterSpecificationConverter.convert(filterBuilder.field("job")
                .in(filterBuilder.input(arrJobIds)).get());

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
}
