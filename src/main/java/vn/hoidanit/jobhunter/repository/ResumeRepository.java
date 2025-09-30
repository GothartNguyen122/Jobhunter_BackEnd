package vn.hoidanit.jobhunter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.User;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long>,
                JpaSpecificationExecutor<Resume> {

        boolean existsByJobId(Long jobId);

        boolean existsByUserAndJobId(User user, Long jobId);

}
