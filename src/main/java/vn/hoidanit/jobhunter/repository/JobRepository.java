package vn.hoidanit.jobhunter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Skill;

@Repository
public interface JobRepository extends JpaRepository<Job, Long>,
                JpaSpecificationExecutor<Job> {

        List<Job> findBySkillsIn(List<Skill> skills);

        @Query("SELECT j FROM Job j LEFT JOIN FETCH j.skills WHERE j.id = :id")
        Optional<Job> findByIdWithSkills(@Param("id") Long id);

        /**
         * Fetch multiple jobs with skills eagerly to avoid N+1 queries
         */
        @EntityGraph(attributePaths = { "skills", "company", "category" })
        @Query("SELECT DISTINCT j FROM Job j WHERE j.id IN :ids")
        List<Job> findByIdsWithSkills(@Param("ids") List<Long> ids);

        /**
         * Fetch jobs with skills and company eagerly to avoid N+1 queries
         */
        @EntityGraph(attributePaths = { "skills", "company", "category" })
        @Query("SELECT DISTINCT j FROM Job j")
        Page<Job> findAllWithRelations(Pageable pageable);

        /**
         * Fetch jobs with skills and company eagerly using Specification
         * Note: This overrides the default findAll to add EntityGraph
         */
        @Override
        @EntityGraph(attributePaths = { "skills", "company", "category" })
        @NonNull
        Page<Job> findAll(@NonNull Specification<Job> spec, @NonNull Pageable pageable);

        /**
         * Fetch all jobs with skills and company eagerly using Specification (no pagination)
         * Uses EntityGraph to eagerly load relationships
         */
        @EntityGraph(attributePaths = { "skills", "company", "category" })
        @NonNull
        List<Job> findAll(@NonNull Specification<Job> spec);
}
