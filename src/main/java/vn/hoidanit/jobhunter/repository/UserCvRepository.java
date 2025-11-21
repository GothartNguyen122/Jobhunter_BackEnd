package vn.hoidanit.jobhunter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.UserCv;

@Repository
public interface UserCvRepository extends JpaRepository<UserCv, Long> {
    List<UserCv> findByUser(User user);

    Optional<UserCv> findByUserIdAndDefaultCvTrue(long userId);
}



