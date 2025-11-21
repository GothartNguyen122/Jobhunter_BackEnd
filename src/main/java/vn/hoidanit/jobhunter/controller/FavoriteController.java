package vn.hoidanit.jobhunter.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.hoidanit.jobhunter.domain.response.favorite.FavoriteCompanyDTO;
import vn.hoidanit.jobhunter.domain.response.favorite.FavoriteJobDTO;
import vn.hoidanit.jobhunter.service.FavoriteService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@RestController
@RequestMapping("/api/v1/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/jobs/{jobId}")
    @ApiMessage("Save job to favorites")
    public ResponseEntity<FavoriteJobDTO> saveJobFavorite(@PathVariable("jobId") long jobId) throws IdInvalidException {
        FavoriteJobDTO dto = this.favoriteService.saveJobToFavorites(jobId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/jobs/{jobId}")
    @ApiMessage("Remove job from favorites")
    public ResponseEntity<Void> removeJobFavorite(@PathVariable("jobId") long jobId) throws IdInvalidException {
        this.favoriteService.removeJobFromFavorites(jobId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/jobs")
    @ApiMessage("Get favorite jobs for current user")
    public ResponseEntity<List<FavoriteJobDTO>> getFavoriteJobs() throws IdInvalidException {
        return ResponseEntity.ok(this.favoriteService.getFavoriteJobsForCurrentUser());
    }

    @GetMapping("/jobs/check/{jobId}")
    @ApiMessage("Check if job is favorited")
    public ResponseEntity<Map<String, Boolean>> checkJobFavorite(@PathVariable("jobId") long jobId)
            throws IdInvalidException {
        boolean saved = this.favoriteService.isJobFavorited(jobId);
        return ResponseEntity.ok(Collections.singletonMap("saved", saved));
    }

    @PostMapping("/companies/{companyId}")
    @ApiMessage("Save company to favorites")
    public ResponseEntity<FavoriteCompanyDTO> saveCompanyFavorite(@PathVariable("companyId") long companyId)
            throws IdInvalidException {
        FavoriteCompanyDTO dto = this.favoriteService.saveCompanyToFavorites(companyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/companies/{companyId}")
    @ApiMessage("Remove company from favorites")
    public ResponseEntity<Void> removeCompanyFavorite(@PathVariable("companyId") long companyId)
            throws IdInvalidException {
        this.favoriteService.removeCompanyFromFavorites(companyId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/companies")
    @ApiMessage("Get favorite companies for current user")
    public ResponseEntity<List<FavoriteCompanyDTO>> getFavoriteCompanies() throws IdInvalidException {
        return ResponseEntity.ok(this.favoriteService.getFavoriteCompaniesForCurrentUser());
    }

    @GetMapping("/companies/check/{companyId}")
    @ApiMessage("Check if company is favorited")
    public ResponseEntity<Map<String, Boolean>> checkCompanyFavorite(@PathVariable("companyId") long companyId)
            throws IdInvalidException {
        boolean saved = this.favoriteService.isCompanyFavorited(companyId);
        return ResponseEntity.ok(Collections.singletonMap("saved", saved));
    }
}



