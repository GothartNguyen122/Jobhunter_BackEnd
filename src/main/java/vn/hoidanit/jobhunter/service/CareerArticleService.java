package vn.hoidanit.jobhunter.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import vn.hoidanit.jobhunter.domain.CareerArticle;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.repository.CareerArticleRepository;

@Service
public class CareerArticleService {
    private final CareerArticleRepository careerArticleRepository;

    public CareerArticleService(CareerArticleRepository careerArticleRepository) {
        this.careerArticleRepository = careerArticleRepository;
    }

    /**
     * Lấy tất cả articles đang active, sắp xếp theo ngày tạo mới nhất
     * Chỉ hiển thị các bài có active = true
     */
    public ResultPaginationDTO fetchAll(Pageable pageable) {
        // Chỉ lấy articles đang active, sắp xếp theo createdAt DESC
        Page<CareerArticle> page = this.careerArticleRepository.findActiveArticlesOrderByCreatedAtDesc(pageable);

        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());
        mt.setPages(page.getTotalPages());
        mt.setTotal(page.getTotalElements());
        rs.setMeta(mt);
        rs.setResult(page.getContent());
        return rs;
    }
    
    /**
     * Lấy top N articles mới nhất (cho sidebar popular articles)
     */
    public java.util.List<CareerArticle> getTopArticles(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return this.careerArticleRepository.findTopActiveArticles(pageable);
    }
}


