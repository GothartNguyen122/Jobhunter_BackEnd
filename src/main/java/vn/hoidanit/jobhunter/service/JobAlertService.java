package vn.hoidanit.jobhunter.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

@Service
public class JobAlertService {

    private final JobAlertRepository jobAlertRepository;
    private final JobRepository jobRepository;
    private final EmailService emailService;

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
     */
    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional(readOnly = true)
    public void sendDailyJobAlerts() {
        List<Job> recentJobs = fetchRecentActiveJobs(24);
        if (recentJobs.isEmpty()) {
            // System.out.println(">>> [JobAlertService] Không có job mới trong 24h qua");
            return;
        }

        List<JobAlert> alerts = this.jobAlertRepository.findActiveAlertsWithSkills();
        if (alerts.isEmpty()) {
            // System.out.println(">>> [JobAlertService] Không có job alert đang active");
            return;
        }

        int totalEmails = 0;
        for (JobAlert alert : alerts) {
            List<Job> matchingJobs = findMatchingJobsForAlert(recentJobs, alert);
            if (!matchingJobs.isEmpty() && sendEmailForAlert(alert, matchingJobs)) {
                totalEmails++;
            }
        }
        // System.out.println(">>> [JobAlertService] Hoàn tất gửi email hằng ngày. Tổng email: " + totalEmails);
    }

    @Async
    public void sendNotificationForNewJobAsync(Job newJob) {
        sendNotificationForNewJob(newJob);
    }

    /**
     * Gửi thông báo ngay khi có job mới được tạo
     */
    @Transactional(readOnly = true)
    public void sendNotificationForNewJob(Job newJob) {
        if (newJob == null || !newJob.isActive()) {
            // System.out.println(">>> [JobAlertService] Job null hoặc không active, bỏ qua gửi email");
            return;
        }

        Job jobWithSkills = this.jobRepository.findByIdWithSkills(newJob.getId()).orElse(null);
        if (jobWithSkills == null || jobWithSkills.getSkills() == null || jobWithSkills.getSkills().isEmpty()) {
            // System.out.println(">>> [JobAlertService] Job không có skills, bỏ qua gửi email");
            return;
        }

        List<JobAlert> alerts = this.jobAlertRepository.findActiveAlertsWithSkills();
        int emails = 0;
        for (JobAlert alert : alerts) {
            if (isJobMatchingAlert(jobWithSkills, alert) && sendEmailForAlert(alert, List.of(jobWithSkills))) {
                emails++;
            }
        }
        // System.out.println(">>> [JobAlertService] Gửi email cho job mới ID " + jobWithSkills.getId() + ": " + emails
        //         + " alert phù hợp");
    }

    private List<Job> fetchRecentActiveJobs(int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        Specification<Job> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("active"), true));
            predicates.add(cb.or(
                    cb.isNull(root.get("endDate")),
                    cb.greaterThan(root.get("endDate"), Instant.now())));
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), since));
            query.distinct(true);
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return this.jobRepository.findAll(spec);
    }

    private List<Job> findMatchingJobsForAlert(List<Job> jobs, JobAlert alert) {
        return jobs.stream()
                .filter(job -> isJobMatchingAlert(job, alert))
                .collect(Collectors.toList());
    }

    private boolean isJobMatchingAlert(Job job, JobAlert alert) {
        if (alert == null || !alert.isActive()) {
            return false;
        }

        // Skill matching
        if (alert.getSkills() != null && !alert.getSkills().isEmpty()) {
            if (job.getSkills() == null || job.getSkills().isEmpty()) {
                return false;
            }
            Set<Long> alertSkillIds = alert.getSkills().stream()
                    .map(Skill::getId)
                    .collect(Collectors.toCollection(HashSet::new));
            boolean hasOverlap = job.getSkills().stream()
                    .map(Skill::getId)
                    .anyMatch(alertSkillIds::contains);
            if (!hasOverlap) {
                return false;
            }
        }

        // Location
        if (alert.getLocation() != null && !alert.getLocation().isBlank()) {
            if (job.getLocation() == null
                    || !job.getLocation().equalsIgnoreCase(alert.getLocation().trim())) {
                return false;
            }
        }

        // Category
        if (alert.getCategory() != null) {
            if (job.getCategory() == null
                    || job.getCategory().getId() != alert.getCategory().getId()) {
                return false;
            }
        }

        // Desired salary
        if (alert.getDesiredSalary() != null) {
            if (job.getSalary() < alert.getDesiredSalary()) {
                return false;
            }
        }

        // Experience / level
        if (alert.getExperience() != null && !alert.getExperience().isBlank()) {
            if (job.getLevel() == null
                    || !job.getLevel().name().equalsIgnoreCase(alert.getExperience().trim())) {
                return false;
            }
        }

        return true;
    }

    private boolean sendEmailForAlert(JobAlert alert, List<Job> jobs) {
        String recipientEmail = resolveRecipientEmail(alert);
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return false;
        }

        List<ResEmailJob> emailJobs = jobs.stream()
                .map(this::convertJobToEmailFormat)
                .collect(Collectors.toList());

        try {
            this.emailService.sendEmailFromTemplateSync(
                    recipientEmail,
                    "Việc làm mới phù hợp với tiêu chí của bạn",
                    "job",
                    resolveRecipientName(alert),
                    emailJobs);
            return true;
        } catch (Exception e) {
            // System.err.println(">>> [JobAlertService] Lỗi gửi email tới " + recipientEmail + ": " + e.getMessage());
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
        if (user != null && user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return "Bạn";
    }

    private ResEmailJob convertJobToEmailFormat(Job job) {
        ResEmailJob res = new ResEmailJob();
        res.setName(job.getName());
        res.setSalary(job.getSalary());

        if (job.getCompany() != null) {
            ResEmailJob.CompanyEmail companyEmail = new ResEmailJob.CompanyEmail(job.getCompany().getName());
            res.setCompany(companyEmail);
        }

        if (job.getSkills() != null && !job.getSkills().isEmpty()) {
            List<ResEmailJob.SkillEmail> skillEmails = job.getSkills().stream()
                    .map(skill -> new ResEmailJob.SkillEmail(skill.getName()))
                    .collect(Collectors.toList());
            res.setSkills(skillEmails);
        }

        return res;
    }
}



