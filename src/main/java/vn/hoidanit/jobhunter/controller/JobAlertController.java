package vn.hoidanit.jobhunter.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.hoidanit.jobhunter.domain.request.ReqJobAlertDTO;
import vn.hoidanit.jobhunter.domain.response.ResJobAlertDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.service.JobAlertManagementService;
import vn.hoidanit.jobhunter.service.JobService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@RestController
@RequestMapping("/api/v1/job-alerts")
public class JobAlertController {

    private final JobAlertManagementService jobAlertManagementService;
    private final JobService jobService;

    public JobAlertController(JobAlertManagementService jobAlertManagementService, JobService jobService) {
        this.jobAlertManagementService = jobAlertManagementService;
        this.jobService = jobService;
    }

    @GetMapping("/my")
    @ApiMessage("Get my job alerts")
    public ResponseEntity<List<ResJobAlertDTO>> getMyJobAlerts() throws IdInvalidException {
        List<ResJobAlertDTO> alerts = this.jobAlertManagementService.getMyJobAlerts();
        return ResponseEntity.ok(alerts);
    }

    @PostMapping
    @ApiMessage("Create a job alert")
    public ResponseEntity<ResJobAlertDTO> createJobAlert(@Valid @RequestBody ReqJobAlertDTO dto)
            throws IdInvalidException {
        ResJobAlertDTO alert = this.jobAlertManagementService.createJobAlert(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(alert);
    }

    @PutMapping("/{id}")
    @ApiMessage("Update a job alert")
    public ResponseEntity<ResJobAlertDTO> updateJobAlert(
            @PathVariable("id") long id,
            @Valid @RequestBody ReqJobAlertDTO dto) throws IdInvalidException {
        ResJobAlertDTO alert = this.jobAlertManagementService.updateJobAlert(id, dto);
        return ResponseEntity.ok(alert);
    }

    @DeleteMapping("/{id}")
    @ApiMessage("Delete a job alert")
    public ResponseEntity<Void> deleteJobAlert(@PathVariable("id") long id) throws IdInvalidException {
        this.jobAlertManagementService.deleteJobAlert(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/toggle")
    @ApiMessage("Toggle job alert active status")
    public ResponseEntity<ResJobAlertDTO> toggleJobAlert(@PathVariable("id") long id)
            throws IdInvalidException {
        ResJobAlertDTO alert = this.jobAlertManagementService.toggleJobAlert(id);
        return ResponseEntity.ok(alert);
    }

    @GetMapping("/matching-jobs")
    @ApiMessage("Get matching jobs for current user based on job alert criteria")
    public ResponseEntity<ResultPaginationDTO> getMatchingJobsByJobAlert(Pageable pageable) {
        return ResponseEntity.ok().body(this.jobService.fetchMatchingJobsByJobAlert(pageable));
    }
}


