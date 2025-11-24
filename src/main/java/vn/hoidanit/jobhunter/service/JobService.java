package vn.hoidanit.jobhunter.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

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
import vn.hoidanit.jobhunter.repository.JobAlertRepository;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.repository.SkillRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.JobAlert;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.constant.LevelEnum;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.service.JobAlertService;

@Service
public class JobService {

    private static final String ROLE_HR = "HR";
    private static final String ROLE_HR_PENDING = "HR_PENDING";

    private final JobRepository jobRepository;
    private final SkillRepository skillRepository;
    private final CompanyRepository companyRepository;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final JobAlertService jobAlertService;
    private final JobAlertRepository jobAlertRepository;

    public JobService(JobRepository jobRepository,
            SkillRepository skillRepository,
            CompanyRepository companyRepository,
            ResumeRepository resumeRepository,
            UserRepository userRepository,
            JobAlertService jobAlertService,
            JobAlertRepository jobAlertRepository) {
        this.jobRepository = jobRepository;
        this.skillRepository = skillRepository;
        this.companyRepository = companyRepository;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.jobAlertService = jobAlertService;
        this.jobAlertRepository = jobAlertRepository;
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

    public ResCreateJobDTO create(Job j) throws IdInvalidException {
        // Get current user
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new IdInvalidException("Không tìm thấy thông tin đăng nhập"));
        User currentUser = this.userRepository.findByEmail(email);
        
        // Check if user is HR (including pending) and has company
        if (currentUser != null && isHrLike(currentUser)) {
            if (currentUser.getCompany() == null) {
                throw new IdInvalidException("Vui lòng hoàn tất thông tin công ty trước khi đăng tin tuyển dụng");
            }
            // Only approved HR can create jobs
            if (ROLE_HR_PENDING.equalsIgnoreCase(currentUser.getRole().getName())) {
                throw new IdInvalidException("Vui lòng chờ admin phê duyệt thông tin công ty trước khi đăng tin tuyển dụng");
            }
            // Auto-assign HR's company to job
            j.setCompany(currentUser.getCompany());
        }

        // check skills
        if (j.getSkills() != null) {
            List<Long> reqSkills = j.getSkills()
                    .stream().map(x -> x.getId())
                    .collect(Collectors.toList());

            List<Skill> dbSkills = this.skillRepository.findByIdIn(reqSkills);
            j.setSkills(dbSkills);
        }

        // check company (for admin users who can specify company)
        if (j.getCompany() != null && j.getCompany().getId() > 0) {
            Optional<Company> cOptional = this.companyRepository.findById(j.getCompany().getId());
            if (cOptional.isPresent()) {
                j.setCompany(cOptional.get());
            }
        }

        // create job
        Job currentJob = this.jobRepository.save(j);

        // Gửi email thông báo cho users có skills phù hợp (async - không block)
        // Sử dụng @Async để không block request
        // Debug logs disabled
        // System.out.println(">>> [JobService] Job created - ID: " + currentJob.getId() + ", Active: " + currentJob.isActive());
        // System.out.println(">>> [JobService] Job skills count: " + (currentJob.getSkills() != null ? currentJob.getSkills().size() : 0));
        
        if (currentJob.isActive() && currentJob.getSkills() != null && !currentJob.getSkills().isEmpty()) {
            try {
                // System.out.println(">>> [JobService] Triggering email notification for job ID: " + currentJob.getId());
                // Gọi async để không block transaction
                this.jobAlertService.sendNotificationForNewJobAsync(currentJob);
                // System.out.println(">>> [JobService] Email notification triggered successfully");
            } catch (Exception e) {
                // System.err.println(">>> [JobService] Lỗi khi trigger gửi email thông báo: " + e.getMessage());
                // e.printStackTrace();
                // Không throw exception để không ảnh hưởng đến việc tạo job
            }
        }
        // else {
        //     System.out.println(">>> [JobService] Không gửi email - Job không active hoặc không có skills");
        // }

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

    public ResUpdateJobDTO update(Job j, Job jobInDB) throws IdInvalidException {
        // Get current user
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new IdInvalidException("Không tìm thấy thông tin đăng nhập"));
        User currentUser = this.userRepository.findByEmail(email);
        
        // Check if user is HR (case-insensitive)
        if (isHrLike(currentUser)) {
            if (currentUser.getCompany() == null) {
                throw new IdInvalidException("Vui lòng hoàn tất thông tin công ty trước khi cập nhật tin tuyển dụng");
            }
            // Only approved HR can update jobs
            if (ROLE_HR_PENDING.equalsIgnoreCase(currentUser.getRole().getName())) {
                throw new IdInvalidException("Vui lòng chờ admin phê duyệt thông tin công ty trước khi cập nhật tin tuyển dụng");
            }
            // HR can only update jobs from their own company
            if (jobInDB.getCompany() == null || jobInDB.getCompany().getId() != currentUser.getCompany().getId()) {
                throw new IdInvalidException("Bạn chỉ có quyền cập nhật tin tuyển dụng của công ty mình");
            }
            // Ensure job stays with HR's company
            jobInDB.setCompany(currentUser.getCompany());
        }

        // check skills
        if (j.getSkills() != null) {
            List<Long> reqSkills = j.getSkills()
                    .stream().map(x -> x.getId())
                    .collect(Collectors.toList());

            List<Skill> dbSkills = this.skillRepository.findByIdIn(reqSkills);
            jobInDB.setSkills(dbSkills);
        }

        // check company (for admin users who can specify company)
        if (j.getCompany() != null && j.getCompany().getId() > 0) {
            Optional<Company> cOptional = this.companyRepository.findById(j.getCompany().getId());
            if (cOptional.isPresent()) {
                // Only allow admin to change company, HR's company is already set above
                if (currentUser == null || !isHrLike(currentUser)) {
                    jobInDB.setCompany(cOptional.get());
                }
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
        // Check if current user là HR (kể cả đang chờ duyệt), nếu vậy chỉ xem job của công ty mình
        Specification<Job> finalSpec = spec;
        try {
            String email = SecurityUtil.getCurrentUserLogin().orElse(null);
            if (email != null) {
                User currentUser = this.userRepository.findByEmail(email);
                if (isHrLike(currentUser)) {
                    if (currentUser.getCompany() != null) {
                        Specification<Job> hrSpec = (root, query, criteriaBuilder) -> 
                                criteriaBuilder.equal(root.get("company").get("id"), currentUser.getCompany().getId());

                        // Combine with existing spec if any
                        if (spec != null) {
                            finalSpec = spec.and(hrSpec);
                        } else {
                            finalSpec = hrSpec;
                        }
                    } else {
                        // HR chưa có company, trả về empty result
                        finalSpec = (root, query, criteriaBuilder) -> criteriaBuilder.disjunction();
                    }
                }
            }
        } catch (Exception e) {
            // If error getting current user, continue with original spec
        }

        Page<Job> pageUser = this.jobRepository.findAll(finalSpec, pageable);

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
            Double minSalary, Double maxSalary, String level, String companyName, String categories, Pageable pageable) {

        try {
            System.out.println("Search parameters - keyword: " + keyword +
                    ", location: " + location + ", skills: " + skills +
                    ", minSalary: " + minSalary + ", maxSalary: " + maxSalary + ", level: " + level +
                    ", companyName: " + companyName + ", categories: " + categories);

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

                // Filter by categories
                if (categories != null && !categories.trim().isEmpty()) {
                    String[] categoryArray = categories.split(",");
                    System.out.println("Adding categories filter: " + Arrays.toString(categoryArray));
                    // Filter by category name or slug
                    predicates.add(root.join("category").get("name").in(Arrays.asList(categoryArray)));
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
        return userSearchAndFilter(keyword, location, skills, minSalary, maxSalary, level, null, null, pageable);
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

    /**
     * Tìm công việc phù hợp với kỹ năng của user hiện tại
     */
    public ResultPaginationDTO fetchMatchingJobsForCurrentUser(Pageable pageable) {
        String email = SecurityUtil.getCurrentUserLogin().orElse("");
        if (email.isEmpty()) {
            ResultPaginationDTO rs = new ResultPaginationDTO();
            ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
            mt.setPage(1);
            mt.setPageSize(pageable.getPageSize());
            mt.setPages(0);
            mt.setTotal(0);
            rs.setMeta(mt);
            rs.setResult(Collections.emptyList());
            return rs;
        }

        User user = this.userRepository.findByEmail(email);
        if (user == null || user.getSkills() == null || user.getSkills().isEmpty()) {
            ResultPaginationDTO rs = new ResultPaginationDTO();
            ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
            mt.setPage(1);
            mt.setPageSize(pageable.getPageSize());
            mt.setPages(0);
            mt.setTotal(0);
            rs.setMeta(mt);
            rs.setResult(Collections.emptyList());
            return rs;
        }

        // Tìm các job có chứa ít nhất một skill của user
        Specification<Job> spec = (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            
            // Job phải active
            predicates.add(criteriaBuilder.equal(root.get("active"), true));
            
            // Job phải có endDate trong tương lai hoặc null
            predicates.add(
                criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("endDate")),
                    criteriaBuilder.greaterThan(root.get("endDate"), java.time.Instant.now())
                )
            );
            
            // Job phải có ít nhất một skill trùng với skills của user
            jakarta.persistence.criteria.Join<Job, Skill> skillsJoin = root.join("skills");
            predicates.add(skillsJoin.in(user.getSkills()));
            
            query.distinct(true);
            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Job> pageJobs = this.jobRepository.findAll(spec, pageable);
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

    /**
     * Đếm số lượng công việc phù hợp với user hiện tại
     */
    public long countMatchingJobsForCurrentUser() {
        String email = SecurityUtil.getCurrentUserLogin().orElse("");
        if (email.isEmpty()) {
            return 0;
        }

        User user = this.userRepository.findByEmail(email);
        if (user == null || user.getSkills() == null || user.getSkills().isEmpty()) {
            return 0;
        }

        Specification<Job> spec = (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            
            predicates.add(criteriaBuilder.equal(root.get("active"), true));
            predicates.add(
                criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("endDate")),
                    criteriaBuilder.greaterThan(root.get("endDate"), java.time.Instant.now())
                )
            );
            
            jakarta.persistence.criteria.Join<Job, Skill> skillsJoin = root.join("skills");
            predicates.add(skillsJoin.in(user.getSkills()));
            
            query.distinct(true);
            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return this.jobRepository.count(spec);
    }

    /**
     * Tìm công việc phù hợp với job alert criteria của user hiện tại
     * Tối ưu: Sử dụng database pagination thay vì load tất cả vào memory
     */
    public ResultPaginationDTO fetchMatchingJobsByJobAlert(Pageable pageable) {
        String email = SecurityUtil.getCurrentUserLogin().orElse("");
        if (email.isEmpty()) {
            return createEmptyResult(pageable);
        }

        User user = this.userRepository.findByEmail(email);
        if (user == null) {
            return createEmptyResult(pageable);
        }

        // Tối ưu: Query trực tiếp active alert với skills (tránh query 2 lần)
        JobAlert activeAlert = this.jobAlertRepository.findActiveByUserWithSkills(user).orElse(null);

        // Base specification cho active jobs
        Specification<Job> baseSpec = (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("active"), true));
            predicates.add(
                criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("endDate")),
                    criteriaBuilder.greaterThan(root.get("endDate"), java.time.Instant.now())
                )
            );
            query.distinct(true);
            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        // Tối ưu: Nếu không có alert hoặc alert không có criteria, dùng pagination trực tiếp
        if (activeAlert == null || !this.jobAlertService.hasAnyCriteria(activeAlert)) {
            Page<Job> pageJobs = this.jobRepository.findAll(baseSpec, pageable);
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

        // Có alert với criteria - tối ưu bằng cách thêm một số filter vào query trước
        Specification<Job> enhancedSpec = buildJobAlertSpecification(baseSpec, activeAlert);
        
        // Tối ưu: Load một batch lớn hơn để filter (ví dụ: 5x page size)
        // Sau đó filter và paginate trong memory cho batch nhỏ này
        int batchSize = Math.max(pageable.getPageSize() * 5, 100); // Tối thiểu 100, tối đa 5x page size
        Pageable batchPageable = org.springframework.data.domain.PageRequest.of(0, batchSize, pageable.getSort());
        
        Page<Job> batchJobs = this.jobRepository.findAll(enhancedSpec, batchPageable);
        List<Job> matchingJobs = batchJobs.getContent().stream()
                .filter(job -> this.jobAlertService.isJobMatchingAlert(job, activeAlert))
                .collect(Collectors.toList());

        // Nếu batch không đủ, cần load thêm (nhưng giới hạn để tránh OOM)
        int maxBatches = 10; // Giới hạn tối đa 10 batches
        int currentBatch = 1;
        while (matchingJobs.size() < pageable.getPageSize() && 
               currentBatch < maxBatches && 
               (currentBatch * batchSize) < batchJobs.getTotalElements()) {
            
            Pageable nextBatchPageable = org.springframework.data.domain.PageRequest.of(
                currentBatch, batchSize, pageable.getSort());
            Page<Job> nextBatch = this.jobRepository.findAll(enhancedSpec, nextBatchPageable);
            
            List<Job> nextMatching = nextBatch.getContent().stream()
                    .filter(job -> this.jobAlertService.isJobMatchingAlert(job, activeAlert))
                    .collect(Collectors.toList());
            matchingJobs.addAll(nextMatching);
            
            if (nextBatch.getContent().isEmpty()) break;
            currentBatch++;
        }

        // Manual pagination trên kết quả đã filter
        int total = matchingJobs.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);
        List<Job> pagedJobs = start < total ? matchingJobs.subList(start, end) : Collections.emptyList();

        // Tối ưu: Chỉ update active status cho jobs trong page
        pagedJobs.forEach(this::updateJobActiveStatus);

        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());
        // Note: Total có thể không chính xác nếu chưa load hết, nhưng đủ cho pagination
        mt.setTotal(total);
        mt.setPages((int) Math.ceil((double) total / pageable.getPageSize()));

        rs.setMeta(mt);
        rs.setResult(pagedJobs);

        return rs;
    }

    /**
     * Build specification với một số filter từ job alert (tối ưu database query)
     */
    private Specification<Job> buildJobAlertSpecification(Specification<Job> baseSpec, JobAlert alert) {
        Specification<Job> alertSpec = baseSpec;
        
        // Thêm filter category nếu có (dễ filter trong database)
        if (alert.getCategory() != null) {
            Specification<Job> categorySpec = (root, query, criteriaBuilder) -> 
                criteriaBuilder.equal(root.get("category").get("id"), alert.getCategory().getId());
            alertSpec = alertSpec.and(categorySpec);
        }
        
        // Thêm filter skills nếu có (ít nhất một skill match)
        if (alert.getSkills() != null && !alert.getSkills().isEmpty()) {
            Specification<Job> skillsSpec = (root, query, criteriaBuilder) -> {
                jakarta.persistence.criteria.Join<Job, Skill> skillsJoin = root.join("skills");
                List<Long> skillIds = alert.getSkills().stream()
                        .map(Skill::getId)
                        .collect(Collectors.toList());
                return skillsJoin.in(skillIds);
            };
            alertSpec = alertSpec.and(skillsSpec);
        }
        
        // Thêm filter level nếu có
        if (alert.getExperience() != null && !alert.getExperience().isBlank()) {
            try {
                LevelEnum level = LevelEnum.valueOf(alert.getExperience().trim().toUpperCase());
                Specification<Job> levelSpec = (root, query, criteriaBuilder) -> 
                    criteriaBuilder.equal(root.get("level"), level);
                alertSpec = alertSpec.and(levelSpec);
            } catch (IllegalArgumentException e) {
                // Invalid level, skip
            }
        }
        
        // Thêm filter salary range nếu có
        if (alert.getMinSalary() != null || alert.getMaxSalary() != null) {
            Specification<Job> salarySpec = (root, query, criteriaBuilder) -> {
                List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
                if (alert.getMinSalary() != null) {
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("salary"), alert.getMinSalary()));
                }
                if (alert.getMaxSalary() != null) {
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("salary"), alert.getMaxSalary()));
                }
                return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            };
            alertSpec = alertSpec.and(salarySpec);
        }
        
        return alertSpec;
    }

    /**
     * Create empty ResultPaginationDTO
     */
    private ResultPaginationDTO createEmptyResult(Pageable pageable) {
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(1);
        mt.setPageSize(pageable.getPageSize());
        mt.setPages(0);
        mt.setTotal(0);
        rs.setMeta(mt);
        rs.setResult(Collections.emptyList());
        return rs;
    }

    /**
     * Check if user is HR or HR_PENDING (case-insensitive)
     */
    private boolean isHrLike(User user) {
        if (user == null || user.getRole() == null || user.getRole().getName() == null) {
            return false;
        }
        String roleName = user.getRole().getName();
        return ROLE_HR.equalsIgnoreCase(roleName) || ROLE_HR_PENDING.equalsIgnoreCase(roleName);
    }
}
