package vn.hoidanit.jobhunter.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.JobAlert;
import vn.hoidanit.jobhunter.domain.Skill;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.email.ResEmailJob;
import vn.hoidanit.jobhunter.repository.JobAlertRepository;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.util.LocationMatcher;

@Service
public class JobAlertService {

    private final JobAlertRepository jobAlertRepository;
    private final JobRepository jobRepository;
    private final EmailService emailService;

    // Map để lưu jobs đã gửi mỗi ngày: key = "userId_date", value = Set<Long> jobIds
    // Tự động reset mỗi ngày khi scheduled job chạy
    private final Map<String, Set<Long>> dailySentJobs = new HashMap<>();

    // Giới hạn số lượng job gửi mỗi ngày
    private static final int MAX_JOBS_PER_DAY = 3;

    public JobAlertService(
            JobAlertRepository jobAlertRepository,
            JobRepository jobRepository,
            EmailService emailService) {
        this.jobAlertRepository = jobAlertRepository;
        this.jobRepository = jobRepository;
        this.emailService = emailService;
    }

    /**
     * Gửi email tổng hợp hàng ngày cho các job alert đang active
     * Chỉ gửi tối đa 3 job có điểm phù hợp cao nhất mỗi ngày
     */
    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional(readOnly = true)
    public void sendDailyJobAlerts() {
        dailySentJobs.clear();

        List<Job> allActiveJobs = fetchAllActiveJobs();
        if (allActiveJobs.isEmpty()) {
            return;
        }

        List<JobAlert> alerts = this.jobAlertRepository.findActiveAlertsWithSkills();
        if (alerts.isEmpty()) {
            return;
        }

        for (JobAlert alert : alerts) {
            List<Job> matchingJobs = findMatchingJobsForAlert(allActiveJobs, alert);
            if (!matchingJobs.isEmpty()) {
                List<Job> topJobs = selectTopJobsForAlert(alert, matchingJobs);
                if (!topJobs.isEmpty() && sendEmailForAlert(alert, topJobs)) {
                    markJobsAsSent(alert, topJobs);
                }
            }
        }
    }

    /**
     * Gửi email cho một job alert cụ thể (khi toggle bật)
     */
    public void sendEmailForAlert(JobAlert alert) {
        if (alert == null || !alert.isActive()) {
            return;
        }

        // Chỉ load lại nếu alert chưa có skills (tránh query không cần thiết)
        JobAlert alertWithSkills = (alert.getSkills() != null && !alert.getSkills().isEmpty()) 
                ? alert 
                : this.jobAlertRepository.findByIdWithSkills(alert.getId()).orElse(null);
        
        if (alertWithSkills == null) {
            System.err.println(">>> [JobAlertService] Cannot load alert with skills for ID: " + alert.getId());
            return;
        }

        List<Job> allActiveJobs = fetchAllActiveJobs();
        if (allActiveJobs.isEmpty()) {
            return;
        }

        List<Job> matchingJobs = findMatchingJobsForAlert(allActiveJobs, alertWithSkills);
        if (!matchingJobs.isEmpty()) {
            List<Job> topJobs = selectTopJobsForAlert(alertWithSkills, matchingJobs);
            if (!topJobs.isEmpty() && sendEmailForAlert(alertWithSkills, topJobs)) {
                markJobsAsSent(alertWithSkills, topJobs);
            }
        }
    }

    @Async
    public void sendNotificationForNewJobAsync(Job newJob) {
        sendNotificationForNewJob(newJob);
    }

    /**
     * Gửi thông báo ngay khi có job mới được tạo
     * Chỉ gửi nếu job mới có điểm cao hơn job thấp nhất đã gửi trong ngày
     */
    @Transactional(readOnly = true)
    public void sendNotificationForNewJob(Job newJob) {
        if (newJob == null || !newJob.isActive()) {
            return;
        }

        Job jobWithSkills = this.jobRepository.findByIdWithSkills(newJob.getId())
                .filter(job -> job.getSkills() != null && !job.getSkills().isEmpty())
                .orElse(null);
        if (jobWithSkills == null) {
            return;
        }

        this.jobAlertRepository.findActiveAlertsWithSkills().stream()
                .filter(alert -> isJobMatchingAlert(jobWithSkills, alert))
                .forEach(alert -> {
                    Set<Long> sentJobIds = getSentJobsForAlert(alert);
                    if (sentJobIds.size() >= MAX_JOBS_PER_DAY) {
                        handleNewJobWhenLimitReached(alert, jobWithSkills, sentJobIds);
                    } else {
                        sendAndMarkJob(alert, jobWithSkills);
                    }
                });
    }

    private void handleNewJobWhenLimitReached(JobAlert alert, Job newJob, Set<Long> sentJobIds) {
        // Fix N+1 query: fetch all jobs in one query
        List<Job> currentTopJobs = this.jobRepository.findByIdsWithSkills(new ArrayList<>(sentJobIds));

        if (currentTopJobs.isEmpty()) {
            return;
        }

        // Tối ưu: Pre-calculate alert skill IDs một lần
        Set<Long> alertSkillIds = (alert.getSkills() != null && !alert.getSkills().isEmpty())
                ? alert.getSkills().stream().map(Skill::getId).collect(Collectors.toSet())
                : Set.of();

        double newJobScore = calculateJobScoreOptimized(newJob, alert, alertSkillIds);
        double minScore = currentTopJobs.stream()
                .mapToDouble(job -> calculateJobScoreOptimized(job, alert, alertSkillIds))
                .min()
                .orElse(0.0);

        if (newJobScore > minScore && sendEmailForAlert(alert, List.of(newJob))) {
            updateSentJobs(alert, currentTopJobs, newJob, minScore);
        }
    }

    private void sendAndMarkJob(JobAlert alert, Job job) {
        if (sendEmailForAlert(alert, List.of(job))) {
            markJobsAsSent(alert, List.of(job));
        }
    }

    private List<Job> fetchAllActiveJobs() {
        Specification<Job> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("active"), true));
            predicates.add(cb.or(
                    cb.isNull(root.get("endDate")),
                    cb.greaterThan(root.get("endDate"), Instant.now())));
            query.distinct(true);
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return this.jobRepository.findAll(spec);
    }

    private List<Job> findMatchingJobsForAlert(List<Job> jobs, JobAlert alert) {
        return jobs.stream()
                .filter(job -> isJobMatchingAlert(job, alert))
                .toList();
    }

    private List<Job> selectTopJobsForAlert(JobAlert alert, List<Job> matchingJobs) {
        Set<Long> sentJobIds = getSentJobsForAlert(alert);
        // Tối ưu: Pre-calculate alert skill IDs một lần để reuse
        Set<Long> alertSkillIds = (alert.getSkills() != null && !alert.getSkills().isEmpty())
                ? alert.getSkills().stream().map(Skill::getId).collect(Collectors.toSet())
                : Set.of();
        
        return matchingJobs.stream()
                .filter(job -> !sentJobIds.contains(job.getId()))
                .map(job -> new JobWithScore(job, calculateJobScoreOptimized(job, alert, alertSkillIds)))
                .sorted(Comparator.comparingDouble(JobWithScore::score).reversed())
                .limit(MAX_JOBS_PER_DAY)
                .map(JobWithScore::job)
                .toList();
    }

    /**
     * Tính điểm phù hợp của job với alert (tối đa 100 điểm)
     */
    private double calculateJobScore(Job job, JobAlert alert) {
        double score = 0;
        score += calculateSkillScore(job, alert);      // 40 điểm
        score += calculateLocationScore(job, alert);   // 20 điểm
        score += calculateCategoryScore(job, alert);   // 15 điểm
        score += calculateSalaryScore(job, alert);     // 15 điểm
        score += calculateLevelScore(job, alert);      // 10 điểm
        score += calculateNewJobBonus(job);             // 5 điểm
        return score;
    }

    /**
     * Optimized version với pre-calculated skill IDs (tránh tính lại nhiều lần)
     */
    private double calculateJobScoreOptimized(Job job, JobAlert alert, Set<Long> alertSkillIds) {
        double score = 0;
        score += calculateSkillScoreOptimized(job, alert, alertSkillIds);  // 40 điểm
        score += calculateLocationScore(job, alert);   // 20 điểm
        score += calculateCategoryScore(job, alert);   // 15 điểm
        score += calculateSalaryScore(job, alert);     // 15 điểm
        score += calculateLevelScore(job, alert);      // 10 điểm
        score += calculateNewJobBonus(job);             // 5 điểm
        return score;
    }

    private double calculateSkillScore(Job job, JobAlert alert) {
        if (alert.getSkills() == null || alert.getSkills().isEmpty() 
                || job.getSkills() == null || job.getSkills().isEmpty()) {
            return 0;
        }
        Set<Long> alertSkillIds = alert.getSkills().stream()
                .map(Skill::getId)
                .collect(Collectors.toSet());
        long matchedSkills = job.getSkills().stream()
                .map(Skill::getId)
                .filter(alertSkillIds::contains)
                .count();
        return (matchedSkills * 40.0) / alert.getSkills().size();
    }

    /**
     * Optimized version với pre-calculated skill IDs
     */
    private double calculateSkillScoreOptimized(Job job, JobAlert alert, Set<Long> alertSkillIds) {
        if (alertSkillIds.isEmpty() || job.getSkills() == null || job.getSkills().isEmpty()) {
            return 0;
        }
        long matchedSkills = job.getSkills().stream()
                .map(Skill::getId)
                .filter(alertSkillIds::contains)
                .count();
        return (matchedSkills * 40.0) / alert.getSkills().size();
    }

    private double calculateLocationScore(Job job, JobAlert alert) {
        if (alert.getLocation() == null || alert.getLocation().isBlank()
                || job.getLocation() == null || job.getLocation().isBlank()) {
            return 0;
        }
        return LocationMatcher.matches(alert.getLocation(), job.getLocation()) ? 20 : 0;
    }

    private double calculateCategoryScore(Job job, JobAlert alert) {
        if (alert.getCategory() == null || job.getCategory() == null) {
            return 0;
        }
        return alert.getCategory().getId() == job.getCategory().getId() ? 15 : 0;
    }

    private double calculateSalaryScore(Job job, JobAlert alert) {
        if (job.getSalary() <= 0) {
            return 0;
        }
        double jobSalary = job.getSalary();
        
        // Ưu tiên sử dụng minSalary và maxSalary (khoảng lương)
        if (alert.getMinSalary() != null || alert.getMaxSalary() != null) {
            boolean inRange = true;
            if (alert.getMinSalary() != null && jobSalary < alert.getMinSalary()) {
                inRange = false;
            }
            if (alert.getMaxSalary() != null && jobSalary > alert.getMaxSalary()) {
                inRange = false;
            }
            return inRange ? 15 : 0;
        }
        
        // Fallback: sử dụng desiredSalary (deprecated)
        if (alert.getDesiredSalary() != null) {
            return jobSalary >= alert.getDesiredSalary() ? 15 : 0;
        }
        
        return 0;
    }

    private double calculateLevelScore(Job job, JobAlert alert) {
        if (alert.getExperience() == null || alert.getExperience().isBlank() || job.getLevel() == null) {
            return 0;
        }
        return job.getLevel().name().equalsIgnoreCase(alert.getExperience().trim()) ? 10 : 0;
    }

    private double calculateNewJobBonus(Job job) {
        if (job.getCreatedAt() == null) {
            return 0;
        }
        long hoursSinceCreation = ChronoUnit.HOURS.between(job.getCreatedAt(), Instant.now());
        return hoursSinceCreation <= 24 ? 5 : 0;
    }

    private Set<Long> getSentJobsForAlert(JobAlert alert) {
        return dailySentJobs.getOrDefault(getDailyKey(alert), new HashSet<>());
    }

    private void markJobsAsSent(JobAlert alert, List<Job> jobs) {
        Set<Long> sentJobIds = dailySentJobs.computeIfAbsent(getDailyKey(alert), k -> new HashSet<>());
        jobs.forEach(job -> sentJobIds.add(job.getId()));
    }

    private void updateSentJobs(JobAlert alert, List<Job> currentTopJobs, Job newJob, double minScore) {
        String key = getDailyKey(alert);
        Set<Long> sentJobIds = dailySentJobs.getOrDefault(key, new HashSet<>());
        
        // Tối ưu: Pre-calculate alert skill IDs một lần
        Set<Long> alertSkillIds = (alert.getSkills() != null && !alert.getSkills().isEmpty())
                ? alert.getSkills().stream().map(Skill::getId).collect(Collectors.toSet())
                : Set.of();
        
        currentTopJobs.stream()
                .filter(job -> Math.abs(calculateJobScoreOptimized(job, alert, alertSkillIds) - minScore) < 0.001)
                .findFirst()
                .ifPresent(job -> sentJobIds.remove(job.getId()));
        
        sentJobIds.add(newJob.getId());
    }

    private String getDailyKey(JobAlert alert) {
        long userId = alert.getUser() != null ? alert.getUser().getId() : 0;
        return userId + "_" + LocalDate.now(ZoneId.systemDefault());
    }

    public boolean hasAnyCriteria(JobAlert alert) {
        if (alert == null) {
            return false;
        }
        return (alert.getLocation() != null && !alert.getLocation().isBlank())
                || alert.getCategory() != null
                || (alert.getExperience() != null && !alert.getExperience().isBlank())
                || alert.getDesiredSalary() != null // Deprecated
                || alert.getMinSalary() != null
                || alert.getMaxSalary() != null
                || (alert.getSkills() != null && !alert.getSkills().isEmpty());
    }

    public boolean isJobMatchingAlert(Job job, JobAlert alert) {
        if (alert == null || !alert.isActive()) {
            return false;
        }
        if (!hasAnyCriteria(alert)) {
            return true;
        }
        
        return matchesSkills(job, alert)
                && matchesLocation(job, alert)
                && matchesCategory(job, alert)
                && matchesSalary(job, alert)
                && matchesLevel(job, alert);
    }

    private boolean matchesSkills(Job job, JobAlert alert) {
        if (alert.getSkills() == null || alert.getSkills().isEmpty()) {
            return true; // Không có tiêu chí skills, match tất cả
        }
        if (job.getSkills() == null || job.getSkills().isEmpty()) {
            return false;
        }
        Set<Long> alertSkillIds = alert.getSkills().stream()
                .map(Skill::getId)
                .collect(Collectors.toSet());
        return job.getSkills().stream()
                .map(Skill::getId)
                .anyMatch(alertSkillIds::contains);
    }

    private boolean matchesLocation(Job job, JobAlert alert) {
        if (alert.getLocation() == null || alert.getLocation().isBlank()) {
            return true;
        }
        if (job.getLocation() == null || job.getLocation().isBlank()) {
            return false;
        }
        try {
            return LocationMatcher.matches(alert.getLocation(), job.getLocation());
        } catch (Exception e) {
            System.err.println(">>> [JobAlertService] Error matching location: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean matchesCategory(Job job, JobAlert alert) {
        if (alert.getCategory() == null) {
            return true;
        }
        if (job.getCategory() == null) {
            return false;
        }
        try {
            return alert.getCategory().getId() == job.getCategory().getId();
        } catch (Exception e) {
            System.err.println(">>> [JobAlertService] Error matching category: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean matchesSalary(Job job, JobAlert alert) {
        // Ưu tiên sử dụng minSalary và maxSalary (khoảng lương)
        if (alert.getMinSalary() != null || alert.getMaxSalary() != null) {
            try {
                double jobSalary = job.getSalary();
                boolean minOk = alert.getMinSalary() == null || jobSalary >= alert.getMinSalary();
                boolean maxOk = alert.getMaxSalary() == null || jobSalary <= alert.getMaxSalary();
                return minOk && maxOk;
            } catch (Exception e) {
                System.err.println(">>> [JobAlertService] Error matching salary range: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        // Fallback: sử dụng desiredSalary (deprecated) để backward compatibility
        if (alert.getDesiredSalary() != null) {
            try {
                return job.getSalary() >= alert.getDesiredSalary();
            } catch (Exception e) {
                System.err.println(">>> [JobAlertService] Error matching desired salary: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        return true; // Không có tiêu chí salary, match tất cả
    }

    private boolean matchesLevel(Job job, JobAlert alert) {
        if (alert.getExperience() == null || alert.getExperience().isBlank()) {
            return true;
        }
        return job.getLevel() != null 
                && job.getLevel().name().equalsIgnoreCase(alert.getExperience().trim());
    }

    private boolean sendEmailForAlert(JobAlert alert, List<Job> jobs) {
        String recipientEmail = resolveRecipientEmail(alert);
        
        if (recipientEmail == null || recipientEmail.isBlank()) {
            System.err.println(">>> [JobAlertService] ERROR: No recipient email found for alert ID: " + alert.getId());
            return false;
        }

        List<ResEmailJob> emailJobs = jobs.stream()
                .map(this::convertJobToEmailFormat)
                .toList();

        try {
            this.emailService.sendEmailFromTemplateSync(
                    recipientEmail,
                    "Việc làm mới phù hợp với tiêu chí của bạn",
                    "job",
                    resolveRecipientName(alert),
                    emailJobs);
            return true;
        } catch (Exception e) {
            System.err.println(">>> [JobAlertService] ERROR sending email for alert ID " + alert.getId() + " to " + recipientEmail + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private String resolveRecipientEmail(JobAlert alert) {
        User user = alert.getUser();
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return alert.getEmail();
    }

    private String resolveRecipientName(JobAlert alert) {
        User user = alert.getUser();
        return (user != null && user.getName() != null && !user.getName().isBlank()) 
                ? user.getName() : "Bạn";
    }

    private ResEmailJob convertJobToEmailFormat(Job job) {
        ResEmailJob res = new ResEmailJob();
        res.setName(job.getName());
        res.setSalary(job.getSalary());

        if (job.getCompany() != null) {
            res.setCompany(new ResEmailJob.CompanyEmail(job.getCompany().getName()));
        }

        if (job.getSkills() != null && !job.getSkills().isEmpty()) {
            res.setSkills(job.getSkills().stream()
                    .map(skill -> new ResEmailJob.SkillEmail(skill.getName()))
                    .toList());
        }

        return res;
    }

    private record JobWithScore(Job job, double score) {
    }
}
