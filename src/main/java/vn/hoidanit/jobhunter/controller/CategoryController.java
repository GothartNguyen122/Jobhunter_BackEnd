package vn.hoidanit.jobhunter.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.hoidanit.jobhunter.domain.Category;
import vn.hoidanit.jobhunter.service.CategoryService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;

@RestController
@RequestMapping("/api/v1")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/categories")
    @ApiMessage("Get all categories")
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok().body(this.categoryService.fetchAllCategories());
    }
}

