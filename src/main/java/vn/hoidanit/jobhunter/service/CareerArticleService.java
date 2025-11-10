package vn.hoidanit.jobhunter.service;

import org.springframework.data.domain.Page;
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

    public ResultPaginationDTO fetchAll(Pageable pageable) {
        Page<CareerArticle> page = this.careerArticleRepository.findAll(pageable);

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

    public java.util.Optional<CareerArticle> fetchBySlug(String slug) {
        return this.careerArticleRepository.findBySlug(slug);
    }
}


