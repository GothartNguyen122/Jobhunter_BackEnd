package vn.hoidanit.jobhunter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.hoidanit.jobhunter.domain.CvTemplate;

@Repository
public interface CvTemplateRepository extends JpaRepository<CvTemplate, Long> {
    List<CvTemplate> findByActiveTrue();
}



