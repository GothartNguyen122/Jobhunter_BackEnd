package vn.hoidanit.jobhunter.controller;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.turkraft.springfilter.boot.Filter;
 
import jakarta.validation.Valid;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.job.ResCreateJobDTO;
import vn.hoidanit.jobhunter.domain.response.job.ResUpdateJobDTO;
import vn.hoidanit.jobhunter.service.JobService;
import vn.hoidanit.jobhunter.service.ResumeService;
import vn.hoidanit.jobhunter.domain.response.resume.ResFetchResumeDTO;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import java.util.Collections;

@RestController
@RequestMapping("/api/v1")
public class JobController {

    private final JobService jobService;
    private final ResumeService resumeService;

    public JobController(JobService jobService, ResumeService resumeService) {
        this.jobService = jobService;
        this.resumeService = resumeService;
    }

    @PostMapping("/jobs")
    @ApiMessage("Create a job")
    public ResponseEntity<ResCreateJobDTO> create(@Valid @RequestBody Job job) {
        // Tạo mới job
        ResCreateJobDTO createdJob = this.jobService.create(job);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdJob);
    }

    @PutMapping("/jobs")
    @ApiMessage("Update a job")
    public ResponseEntity<ResUpdateJobDTO> update(@Valid @RequestBody Job job) throws IdInvalidException {
        // Kiểm tra xem job có tồn tại trong cơ sở dữ liệu không
        Optional<Job> currentJob = this.jobService.fetchJobById(job.getId());
        if (!currentJob.isPresent()) {
            throw new IdInvalidException("Job not found");
        }

        // Cập nhật job
        ResUpdateJobDTO updatedJob = this.jobService.update(job, currentJob.get());

        if (updatedJob != null) {
            return ResponseEntity.status(HttpStatus.OK).body(updatedJob);
        } else {
            // Nếu cập nhật không thành công, trả về lỗi
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @DeleteMapping("/jobs/{id}")
    @ApiMessage("Delete a job by id")
    public ResponseEntity<?> delete(@PathVariable("id") long id) {
        Optional<Job> currentJob = this.jobService.fetchJobById(id);
        if (!currentJob.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("message", "Job không tồn tại"));
        }
        try {
            this.jobService.delete(id);
            return ResponseEntity.ok().body(null);
        } catch (IdInvalidException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @GetMapping("/jobs/{id}")
    @ApiMessage("Get a job by id")
    public ResponseEntity<Job> getJob(@PathVariable("id") long id) throws IdInvalidException {
        Optional<Job> currentJob = this.jobService.fetchJobById(id);
        if (!currentJob.isPresent()) {
            throw new IdInvalidException("Job not found");
        }

        return ResponseEntity.ok().body(currentJob.get());
    }

    @GetMapping("/jobs")
    @ApiMessage("Get job with pagination")
    public ResponseEntity<ResultPaginationDTO> getAllJob(
            @Filter Specification<Job> spec,
            Pageable pageable) {

        return ResponseEntity.ok().body(this.jobService.fetchAll(spec, pageable));
    }

    @GetMapping("/jobs/user-search")
    @ApiMessage("User search and filter jobs")
    public ResponseEntity<ResultPaginationDTO> userSearchAndFilterJobs(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "skills", required = false) String skills,
            @RequestParam(value = "minSalary", required = false) Double minSalary,
            @RequestParam(value = "maxSalary", required = false) Double maxSalary,
            @RequestParam(value = "level", required = false) String level,
            Pageable pageable) {

        return ResponseEntity.ok().body(this.jobService.userSearchAndFilter(
                keyword, location, skills, minSalary, maxSalary, level, pageable));
    }
    @GetMapping("/jobs/resumes/{job_id}")
    @ApiMessage("Get all resumes of Job")
    public ResponseEntity<Map<String, Object>> getResumesByJob(@PathVariable("job_id") Long id) throws IdInvalidException {
        Optional<Job> currentJob = this.jobService.fetchJobById(id);
        if (!currentJob.isPresent()) {
            throw new IdInvalidException("Job not found");
        }
        List<ResFetchResumeDTO> resumes = this.resumeService.fetchResumesByJobId(id);
        Map<String, Object> body = new HashMap<>();
        body.put("count", resumes != null ? resumes.size() : 0);
        body.put("resumes", resumes);
        return ResponseEntity.ok().body(body);
    }    

    @GetMapping("/jobs/company/{companyId}")
    @ApiMessage("Get jobs by company ID")
    public ResponseEntity<ResultPaginationDTO> getJobsByCompany(
            @PathVariable("companyId") long companyId,
            Pageable pageable) {

        return ResponseEntity.ok().body(this.jobService.fetchJobsByCompany(companyId, pageable));
    }

    @GetMapping("/jobs/matching")
    @ApiMessage("Get matching jobs for current user based on skills")
    public ResponseEntity<ResultPaginationDTO> getMatchingJobs(Pageable pageable) {
        return ResponseEntity.ok().body(this.jobService.fetchMatchingJobsForCurrentUser(pageable));
    }

    @GetMapping("/jobs/matching/count")
    @ApiMessage("Get count of matching jobs for current user")
    public ResponseEntity<Long> getMatchingJobsCount() {
        return ResponseEntity.ok().body(this.jobService.countMatchingJobsForCurrentUser());
    }
}
