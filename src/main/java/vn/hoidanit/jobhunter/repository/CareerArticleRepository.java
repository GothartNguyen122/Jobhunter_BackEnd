package vn.hoidanit.jobhunter.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import vn.hoidanit.jobhunter.domain.CareerArticle;

@Repository
public interface CareerArticleRepository extends JpaRepository<CareerArticle, Long> {
    
    /**
     * Lấy tất cả articles đang active, sắp xếp theo ngày tạo mới nhất
     */
    @Query("SELECT ca FROM CareerArticle ca WHERE ca.active = true ORDER BY ca.createdAt DESC")
    Page<CareerArticle> findActiveArticlesOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * Lấy tất cả articles đang active (không phân trang)
     */
    @Query("SELECT ca FROM CareerArticle ca WHERE ca.active = true ORDER BY ca.createdAt DESC")
    List<CareerArticle> findAllActiveArticles();
    
    /**
     * Lấy top N articles mới nhất (cho sidebar)
     */
    @Query("SELECT ca FROM CareerArticle ca WHERE ca.active = true ORDER BY ca.createdAt DESC")
    List<CareerArticle> findTopActiveArticles(Pageable pageable);
}


