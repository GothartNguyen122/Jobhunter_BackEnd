package vn.hoidanit.jobhunter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.hoidanit.jobhunter.domain.Favorite;
import vn.hoidanit.jobhunter.domain.User;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUser(User user);

    /**
     * Fetch favorites with job, job.skills, and job.company eagerly
     */
    @EntityGraph(attributePaths = { "job", "job.skills", "job.company", "job.category" })
    @Query("SELECT f FROM Favorite f WHERE f.user = :user AND f.job IS NOT NULL")
    List<Favorite> findByUserAndJobIsNotNull(@Param("user") User user);

    /**
     * Fetch favorites with company eagerly
     */
    @EntityGraph(attributePaths = { "company" })
    @Query("SELECT f FROM Favorite f WHERE f.user = :user AND f.company IS NOT NULL")
    List<Favorite> findByUserAndCompanyIsNotNull(@Param("user") User user);

    Optional<Favorite> findByUserAndJobId(User user, long jobId);

    Optional<Favorite> findByUserAndCompanyId(User user, long companyId);

    boolean existsByUserIdAndJobId(long userId, long jobId);

    boolean existsByUserIdAndCompanyId(long userId, long companyId);
}

