package vn.hoidanit.jobhunter.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.service.CareerArticleService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;

@RestController
@RequestMapping("/api/v1")
public class CareerArticleController {
    private final CareerArticleService careerArticleService;

    public CareerArticleController(CareerArticleService careerArticleService) {
        this.careerArticleService = careerArticleService;
    }

    @GetMapping("/career-articles")
    @ApiMessage("Get career articles with pagination")
    public ResponseEntity<ResultPaginationDTO> fetchAll(Pageable pageable) {
        return ResponseEntity.ok().body(this.careerArticleService.fetchAll(pageable));
    }

    @GetMapping("/career-articles/{slug}")
    @ApiMessage("Get career article by slug")
    public ResponseEntity<vn.hoidanit.jobhunter.domain.CareerArticle> fetchBySlug(@PathVariable("slug") String slug) {
        java.util.Optional<vn.hoidanit.jobhunter.domain.CareerArticle> article = this.careerArticleService.fetchBySlug(slug);
        if (article.isPresent()) {
            return ResponseEntity.ok().body(article.get());
        }
        return ResponseEntity.notFound().build();
    }
}


