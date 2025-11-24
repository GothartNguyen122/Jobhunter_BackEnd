package vn.hoidanit.jobhunter.controller;

import java.util.List;

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
import vn.hoidanit.jobhunter.domain.request.ReqUserCvDTO;
import vn.hoidanit.jobhunter.domain.response.ResUserCvDTO;
import vn.hoidanit.jobhunter.service.UserCvService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@RestController
@RequestMapping("/api/v1/user-cvs")
public class UserCvController {

    private final UserCvService userCvService;

    public UserCvController(UserCvService userCvService) {
        this.userCvService = userCvService;
    }

    @GetMapping("/my")
    @ApiMessage("Get my CVs")
    public ResponseEntity<List<ResUserCvDTO>> getMyCvs() throws IdInvalidException {
        List<ResUserCvDTO> cvs = this.userCvService.getMyCvs();
        return ResponseEntity.ok(cvs);
    }

    @GetMapping("/{id}")
    @ApiMessage("Get CV by ID")
    public ResponseEntity<ResUserCvDTO> getCvById(@PathVariable("id") long id) throws IdInvalidException {
        ResUserCvDTO cv = this.userCvService.getCvById(id);
        return ResponseEntity.ok(cv);
    }

    @PostMapping
    @ApiMessage("Create a CV")
    public ResponseEntity<ResUserCvDTO> createCv(@Valid @RequestBody ReqUserCvDTO dto) throws IdInvalidException {
        ResUserCvDTO cv = this.userCvService.createCv(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(cv);
    }

    @PutMapping("/{id}")
    @ApiMessage("Update a CV")
    public ResponseEntity<ResUserCvDTO> updateCv(
            @PathVariable("id") long id,
            @Valid @RequestBody ReqUserCvDTO dto) throws IdInvalidException {
        ResUserCvDTO cv = this.userCvService.updateCv(id, dto);
        return ResponseEntity.ok(cv);
    }

    @DeleteMapping("/{id}")
    @ApiMessage("Delete a CV")
    public ResponseEntity<Void> deleteCv(@PathVariable("id") long id) throws IdInvalidException {
        this.userCvService.deleteCv(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/set-default")
    @ApiMessage("Set CV as default")
    public ResponseEntity<ResUserCvDTO> setDefaultCv(@PathVariable("id") long id) throws IdInvalidException {
        ResUserCvDTO cv = this.userCvService.setDefaultCv(id);
        return ResponseEntity.ok(cv);
    }
}



