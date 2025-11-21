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
}

