package vn.hoidanit.jobhunter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.hoidanit.jobhunter.domain.CareerArticle;

@Repository
public interface CareerArticleRepository extends JpaRepository<CareerArticle, Long> {
}


