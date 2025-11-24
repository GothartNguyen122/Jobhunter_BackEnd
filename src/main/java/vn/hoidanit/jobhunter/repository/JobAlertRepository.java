package vn.hoidanit.jobhunter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import vn.hoidanit.jobhunter.domain.JobAlert;
import vn.hoidanit.jobhunter.domain.User;

@Repository
public interface JobAlertRepository extends JpaRepository<JobAlert, Long> {
    List<JobAlert> findByUser(User user);

    List<JobAlert> findByActiveTrue();

    @EntityGraph(attributePaths = { "skills", "user", "category" })
    @Query("SELECT DISTINCT ja FROM JobAlert ja WHERE ja.active = true")
    List<JobAlert> findActiveAlertsWithSkills();

    @EntityGraph(attributePaths = { "skills", "user", "category" })
    @Query("SELECT DISTINCT ja FROM JobAlert ja WHERE ja.id = :id")
    java.util.Optional<JobAlert> findByIdWithSkills(@org.springframework.data.repository.query.Param("id") Long id);

    /**
     * Find active job alert for a user with skills loaded
     */
    @EntityGraph(attributePaths = { "skills", "user", "category" })
    @Query("SELECT DISTINCT ja FROM JobAlert ja WHERE ja.user = :user AND ja.active = true")
    java.util.Optional<JobAlert> findActiveByUserWithSkills(@org.springframework.data.repository.query.Param("user") User user);
}

