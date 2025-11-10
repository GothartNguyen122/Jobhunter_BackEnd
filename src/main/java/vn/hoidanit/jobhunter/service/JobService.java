package vn.hoidanit.jobhunter.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Arrays;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Skill;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.job.ResCreateJobDTO;
import vn.hoidanit.jobhunter.domain.response.job.ResUpdateJobDTO;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.repository.SkillRepository;
import vn.hoidanit.jobhunter.util.constant.LevelEnum;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final SkillRepository skillRepository;
    private final CompanyRepository companyRepository;
    private final ResumeRepository resumeRepository;

    public JobService(JobRepository jobRepository,
            SkillRepository skillRepository,
            CompanyRepository companyRepository,
            ResumeRepository resumeRepository) {
        this.jobRepository = jobRepository;
        this.skillRepository = skillRepository;
        this.companyRepository = companyRepository;
        this.resumeRepository = resumeRepository;
    }

    public Optional<Job> fetchJobById(long id) {
        Optional<Job> jobOptional = this.jobRepository.findById(id);
        if (jobOptional.isPresent()) {
            Job job = jobOptional.get();
            // Tự động cập nhật trạng thái active dựa trên endDate
            updateJobActiveStatus(job);
            return Optional.of(job);
        }
        return jobOptional;
    }

    private void updateJobActiveStatus(Job job) {
        if (job.getEndDate() != null) {
            java.time.Instant now = java.time.Instant.now();
            boolean shouldBeActive = job.getEndDate().isAfter(now);

            // Chỉ cập nhật nếu trạng thái hiện tại khác với trạng thái mong muốn
            if (job.isActive() != shouldBeActive) {
                job.setActive(shouldBeActive);
                this.jobRepository.save(job);
            }
        }
    }

    public ResCreateJobDTO create(Job j) {
        // check skills
        if (j.getSkills() != null) {
            List<Long> reqSkills = j.getSkills()
                    .stream().map(x -> x.getId())
                    .collect(Collectors.toList());

            List<Skill> dbSkills = this.skillRepository.findByIdIn(reqSkills);
            j.setSkills(dbSkills);
        }

        // check company
        if (j.getCompany() != null) {
            Optional<Company> cOptional = this.companyRepository.findById(j.getCompany().getId());
            if (cOptional.isPresent()) {
                j.setCompany(cOptional.get());
            }
        }

        // create job
        Job currentJob = this.jobRepository.save(j);

        // convert response
        ResCreateJobDTO dto = new ResCreateJobDTO();
        dto.setId(currentJob.getId());
        dto.setName(currentJob.getName());
        dto.setSalary(currentJob.getSalary());
        dto.setQuantity(currentJob.getQuantity());
        dto.setLocation(currentJob.getLocation());
        dto.setLevel(currentJob.getLevel());
        dto.setStartDate(currentJob.getStartDate());
        dto.setEndDate(currentJob.getEndDate());
        dto.setActive(currentJob.isActive());
        dto.setCreatedAt(currentJob.getCreatedAt());
        dto.setCreatedBy(currentJob.getCreatedBy());

        if (currentJob.getSkills() != null) {
            List<String> skills = currentJob.getSkills()
                    .stream().map(item -> item.getName())
                    .collect(Collectors.toList());
            dto.setSkills(skills);
        }
        return dto;
    }

    public ResUpdateJobDTO update(Job j, Job jobInDB) {

        // check skills
        if (j.getSkills() != null) {
            List<Long> reqSkills = j.getSkills()
                    .stream().map(x -> x.getId())
                    .collect(Collectors.toList());

            List<Skill> dbSkills = this.skillRepository.findByIdIn(reqSkills);
            jobInDB.setSkills(dbSkills);
        }

        // check company
        if (j.getCompany() != null) {
            Optional<Company> cOptional = this.companyRepository.findById(j.getCompany().getId());
            if (cOptional.isPresent()) {
                jobInDB.setCompany(cOptional.get());
            }
        }

        // update correct info
        jobInDB.setName(j.getName());
        jobInDB.setSalary(j.getSalary());
        jobInDB.setQuantity(j.getQuantity());
        jobInDB.setLocation(j.getLocation());
        jobInDB.setLevel(j.getLevel());
        jobInDB.setStartDate(j.getStartDate());
        jobInDB.setEndDate(j.getEndDate());
        jobInDB.setActive(j.isActive());

        // update job
        Job currentJob = this.jobRepository.save(jobInDB);

        // convert response
        ResUpdateJobDTO dto = new ResUpdateJobDTO();
        dto.setId(currentJob.getId());
        dto.setName(currentJob.getName());
        dto.setSalary(currentJob.getSalary());
        dto.setQuantity(currentJob.getQuantity());
        dto.setLocation(currentJob.getLocation());
        dto.setLevel(currentJob.getLevel());
        dto.setStartDate(currentJob.getStartDate());
        dto.setEndDate(currentJob.getEndDate());
        dto.setActive(currentJob.isActive());
        dto.setUpdatedAt(currentJob.getUpdatedAt());
        dto.setUpdatedBy(currentJob.getUpdatedBy());

        if (currentJob.getSkills() != null) {
            List<String> skills = currentJob.getSkills()
                    .stream().map(item -> item.getName())
                    .collect(Collectors.toList());
            dto.setSkills(skills);
        }

        return dto;
    }

    public void delete(long id) throws IdInvalidException {
        if (this.resumeRepository.existsByJobId(id)) {
            throw new IdInvalidException("Công việc đã có ứng viên đăng ký");
        }
        this.jobRepository.deleteById(id);
    }

    public ResultPaginationDTO fetchAll(Specification<Job> spec, Pageable pageable) {
        Page<Job> pageUser = this.jobRepository.findAll(spec, pageable);

        // Tự động cập nhật trạng thái active cho tất cả job trong kết quả
        pageUser.getContent().forEach(this::updateJobActiveStatus);

        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();

        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());

        mt.setPages(pageUser.getTotalPages());
        mt.setTotal(pageUser.getTotalElements());

        rs.setMeta(mt);

        rs.setResult(pageUser.getContent());

        return rs;
    }

    public ResultPaginationDTO userSearchAndFilter(
            String keyword, String location, String skills,
            Double minSalary, Double maxSalary, String level, String companyName, Pageable pageable) {

        try {
            System.out.println("Search parameters - keyword: " + keyword +
                    ", location: " + location + ", skills: " + skills +
                    ", minSalary: " + minSalary + ", maxSalary: " + maxSalary + ", level: " + level +
                    ", companyName: " + companyName);

            // Xây dựng specification cho search và filter
            Specification<Job> spec = (root, query, criteriaBuilder) -> {
                query.distinct(true);
                List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

                // Search logic - tìm kiếm theo tên job
                if (keyword != null && !keyword.trim().isEmpty()) {
                    predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")),
                            "%" + keyword.toLowerCase() + "%"));
                }

                // Filter logic - lọc theo các tiêu chí cụ thể
                if (location != null && !location.trim().isEmpty()) {
                    String[] locationArray = location.split(",");
                    predicates.add(root.get("location").in(Arrays.asList(locationArray)));
                }

                if (skills != null && !skills.trim().isEmpty()) {
                    String[] skillArray = skills.split(",");
                    System.out.println("Adding skills filter: " + Arrays.toString(skillArray));
                    // Sử dụng join đơn giản
                    predicates.add(root.join("skills").get("name").in(Arrays.asList(skillArray)));
                }

                if (minSalary != null) {
                    System.out.println("Adding minSalary filter: " + minSalary);
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("salary"), minSalary));
                }

                if (maxSalary != null) {
                    System.out.println("Adding maxSalary filter: " + maxSalary);
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("salary"), maxSalary));
                }

                if (level != null && !level.trim().isEmpty()) {
                    String[] levelArray = level.split(",");
                    List<LevelEnum> levelEnums = Arrays.stream(levelArray)
                            .map(LevelEnum::valueOf)
                            .collect(Collectors.toList());
                    System.out.println("Adding level filter: " + levelEnums);
                    predicates.add(root.get("level").in(levelEnums));
                }

                // Search by company name
                if (companyName != null && !companyName.trim().isEmpty()) {
                    predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.join("company").get("name")),
                        "%" + companyName.toLowerCase() + "%"
                    ));
                }

                // Debug: In ra tất cả predicates
                System.out.println("Total predicates: " + predicates.size());
                for (int i = 0; i < predicates.size(); i++) {
                    System.out.println("Predicate " + i + ": " + predicates.get(i));
                }

                return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            };

            return fetchAll(spec, pageable);

        } catch (Exception e) {
            System.err.println("Error in userSearchAndFilter: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    // Backward-compatible overload (keeps existing callers working)
    public ResultPaginationDTO userSearchAndFilter(
            String keyword, String location, String skills,
            Double minSalary, Double maxSalary, String level, Pageable pageable) {
        return userSearchAndFilter(keyword, location, skills, minSalary, maxSalary, level, null, pageable);
    }
    public ResultPaginationDTO fetchJobsByCompany(long companyId, Pageable pageable) {
        // Tạo specification để filter theo company ID
        Specification<Job> spec = (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get("company").get("id"), companyId);
        };

        Page<Job> pageJobs = this.jobRepository.findAll(spec, pageable);

        // Tự động cập nhật trạng thái active cho tất cả job trong kết quả
        pageJobs.getContent().forEach(this::updateJobActiveStatus);

        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();

        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());
        mt.setPages(pageJobs.getTotalPages());
        mt.setTotal(pageJobs.getTotalElements());

        rs.setMeta(mt);
        rs.setResult(pageJobs.getContent());

        return rs;
    }
}
