package vn.hoidanit.jobhunter.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Skill;
import vn.hoidanit.jobhunter.domain.Subscriber;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.email.ResEmailJob;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.SkillRepository;
import vn.hoidanit.jobhunter.repository.SubscriberRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;

@Service
public class SubscriberService {

    private final SubscriberRepository subscriberRepository;
    private final SkillRepository skillRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public SubscriberService(
            SubscriberRepository subscriberRepository,
            SkillRepository skillRepository,
            JobRepository jobRepository,
            UserRepository userRepository,
            EmailService emailService) {
        this.subscriberRepository = subscriberRepository;
        this.skillRepository = skillRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    // ========== Subscriber CRUD Methods ==========

    public boolean isExistsByEmail(String email) {
        return this.subscriberRepository.existsByEmail(email);
    }

    public Subscriber create(Subscriber subs) {
        // check skills
        if (subs.getSkills() != null) {
            List<Long> reqSkills = subs.getSkills()
                    .stream().map(x -> x.getId())
                    .collect(Collectors.toList());

            List<Skill> dbSkills = this.skillRepository.findByIdIn(reqSkills);
            subs.setSkills(dbSkills);
        }

        return this.subscriberRepository.save(subs);
    }

    public Subscriber update(Subscriber subsDB, Subscriber subsRequest) {
        // check skills
        if (subsRequest.getSkills() != null) {
            List<Long> reqSkills = subsRequest.getSkills()
                    .stream().map(x -> x.getId())
                    .collect(Collectors.toList());

            List<Skill> dbSkills = this.skillRepository.findByIdIn(reqSkills);
            subsDB.setSkills(dbSkills);
        }
        return this.subscriberRepository.save(subsDB);
    }

    public Subscriber findById(long id) {
        Optional<Subscriber> subsOptional = this.subscriberRepository.findById(id);
        if (subsOptional.isPresent())
            return subsOptional.get();
        return null;
    }

    public Subscriber findByEmail(String email) {
        return this.subscriberRepository.findByEmail(email);
    }

    // ========== Job Notification Methods (moved from JobNotificationService) ==========

    /**
     * Gửi email thông báo công việc mới cho users có skills phù hợp
     * Chạy mỗi ngày lúc 8:00 AM
     */
    @Scheduled(cron = "0 0 8 * * ?") // 8:00 AM mỗi ngày
    @Transactional
    public void sendJobNotificationsToUsers() {
        System.out.println(">>> [SubscriberService] Bắt đầu gửi thông báo công việc mới cho users...");
        
        // Lấy các job mới được tạo trong 24 giờ qua
        Instant yesterday = Instant.now().minus(24, ChronoUnit.HOURS);
        
        Specification<Job> jobSpec = (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            
            // Job phải active
            predicates.add(criteriaBuilder.equal(root.get("active"), true));
            
            // Job phải có endDate trong tương lai hoặc null
            predicates.add(
                criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("endDate")),
                    criteriaBuilder.greaterThan(root.get("endDate"), Instant.now())
                )
            );
            
            // Job được tạo trong 24 giờ qua
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), yesterday));
            
            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Job> newJobs = this.jobRepository.findAll(jobSpec, PageRequest.of(0, 100));
        
        if (newJobs.isEmpty()) {
            System.out.println(">>> [SubscriberService] Không có công việc mới trong 24 giờ qua");
            return;
        }

        System.out.println(">>> [SubscriberService] Tìm thấy " + newJobs.getTotalElements() + " công việc mới");

        // Lấy tất cả users có skills
        List<User> allUsers = this.userRepository.findAllWithSkills();
        
        int emailSentCount = 0;
        
        for (User user : allUsers) {
            if (user.getEmail() == null || user.getEmail().isEmpty()) {
                continue;
            }
            
            if (user.getSkills() == null || user.getSkills().isEmpty()) {
                continue;
            }

            // Tìm các job phù hợp với skills của user
            List<Job> matchingJobs = findMatchingJobsForUser(newJobs.getContent(), user.getSkills());
            
            if (!matchingJobs.isEmpty()) {
                // Convert jobs to email format
                List<ResEmailJob> emailJobs = matchingJobs.stream()
                    .map(this::convertJobToEmailFormat)
                    .collect(Collectors.toList());
                
                // Gửi email
                try {
                    this.emailService.sendEmailFromTemplateSync(
                        user.getEmail(),
                        "Cơ hội việc làm hot đang chờ đón bạn, khám phá ngay!",
                        "job",
                        user.getName() != null ? user.getName() : "Bạn",
                        emailJobs
                    );
                    emailSentCount++;
                    System.out.println(">>> [SubscriberService] Đã gửi email cho: " + user.getEmail() + " (" + matchingJobs.size() + " công việc)");
                } catch (Exception e) {
                    System.err.println(">>> [SubscriberService] Lỗi khi gửi email cho " + user.getEmail() + ": " + e.getMessage());
                }
            }
        }

        System.out.println(">>> [SubscriberService] Hoàn thành! Đã gửi " + emailSentCount + " email thông báo");
    }

    /**
     * Tìm các job phù hợp với skills của user
     */
    private List<Job> findMatchingJobsForUser(List<Job> jobs, List<Skill> userSkills) {
        List<Long> userSkillIds = userSkills.stream()
            .map(Skill::getId)
            .collect(Collectors.toList());

        return jobs.stream()
            .filter(job -> {
                if (job.getSkills() == null || job.getSkills().isEmpty()) {
                    return false;
                }
                // Kiểm tra xem job có ít nhất một skill trùng với user skills không
                return job.getSkills().stream()
                    .anyMatch(jobSkill -> userSkillIds.contains(jobSkill.getId()));
            })
            .collect(Collectors.toList());
    }

    /**
     * Convert Job entity sang ResEmailJob để gửi email
     */
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

    /**
     * Gửi email thông báo ngay khi có job mới được tạo (async - không block)
     * Có thể gọi từ JobService khi tạo job mới
     */
    @Async
    public void sendNotificationForNewJobAsync(Job newJob) {
        System.out.println(">>> [SubscriberService] Async method called for job ID: " + (newJob != null ? newJob.getId() : "null"));
        sendNotificationForNewJob(newJob);
    }

    /**
     * Gửi email thông báo ngay khi có job mới được tạo (manual trigger)
     * Có thể gọi từ JobService khi tạo job mới
     */
    @Transactional
    public void sendNotificationForNewJob(Job newJob) {
        System.out.println(">>> [SubscriberService] Starting email notification process...");
        
        if (newJob == null) {
            System.out.println(">>> [SubscriberService] Job is null, skipping...");
            return;
        }
        
        if (!newJob.isActive()) {
            System.out.println(">>> [SubscriberService] Job is not active, skipping...");
            return;
        }

        // Reload job với skills bằng JOIN FETCH để tránh LazyInitializationException
        Job jobWithSkills = this.jobRepository.findByIdWithSkills(newJob.getId()).orElse(null);
        if (jobWithSkills == null) {
            System.out.println(">>> [SubscriberService] Cannot reload job from database, skipping...");
            return;
        }

        // Skills đã được load bằng JOIN FETCH, không cần force load nữa
        List<Skill> jobSkills = jobWithSkills.getSkills();
        if (jobSkills == null || jobSkills.isEmpty()) {
            System.out.println(">>> [SubscriberService] Job has no skills, skipping...");
            return;
        }
        
        System.out.println(">>> [SubscriberService] Loaded " + jobSkills.size() + " skills for job");

        System.out.println(">>> [SubscriberService] Job has " + jobWithSkills.getSkills().size() + " skills");
        System.out.println(">>> [SubscriberService] Job skills: " + 
            jobWithSkills.getSkills().stream().map(Skill::getName).collect(Collectors.joining(", ")));

        // Lấy tất cả users có skills bằng JOIN FETCH để tránh LazyInitializationException
        List<User> allUsers = this.userRepository.findAllWithSkills();
        System.out.println(">>> [SubscriberService] Total users in database: " + allUsers.size());
        
        int usersWithEmail = 0;
        int usersWithSkills = 0;
        int matchingUsers = 0;
        int emailsSent = 0;
        
        for (User user : allUsers) {
            if (user.getEmail() == null || user.getEmail().isEmpty()) {
                continue;
            }
            usersWithEmail++;
            
            if (user.getSkills() == null || user.getSkills().isEmpty()) {
                continue;
            }
            usersWithSkills++;
            
            System.out.println(">>> [SubscriberService] Checking user: " + user.getEmail() + " with " + user.getSkills().size() + " skills");

            // Kiểm tra xem job có phù hợp với skills của user không
            List<Long> userSkillIds = user.getSkills().stream()
                .map(Skill::getId)
                .collect(Collectors.toList());

            List<Long> jobSkillIds = jobWithSkills.getSkills().stream()
                .map(Skill::getId)
                .collect(Collectors.toList());

            boolean isMatching = jobSkillIds.stream()
                .anyMatch(jobSkillId -> userSkillIds.contains(jobSkillId));

            if (isMatching) {
                matchingUsers++;
                System.out.println(">>> [SubscriberService] User " + user.getEmail() + " has matching skills!");
                
                // Convert job to email format
                ResEmailJob emailJob = convertJobToEmailFormat(jobWithSkills);
                List<ResEmailJob> emailJobs = new ArrayList<>();
                emailJobs.add(emailJob);
                
                // Gửi email
                try {
                    System.out.println(">>> [SubscriberService] Sending email to: " + user.getEmail());
                    this.emailService.sendEmailFromTemplateSync(
                        user.getEmail(),
                        "Cơ hội việc làm mới phù hợp với bạn!",
                        "job",
                        user.getName() != null ? user.getName() : "Bạn",
                        emailJobs
                    );
                    emailsSent++;
                    System.out.println(">>> [SubscriberService] ✓ Email sent successfully to: " + user.getEmail());
                } catch (Exception e) {
                    System.err.println(">>> [SubscriberService] ✗ Lỗi khi gửi email cho " + user.getEmail() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        System.out.println(">>> [SubscriberService] Summary:");
        System.out.println(">>>   - Total users: " + allUsers.size());
        System.out.println(">>>   - Users with email: " + usersWithEmail);
        System.out.println(">>>   - Users with skills: " + usersWithSkills);
        System.out.println(">>>   - Matching users: " + matchingUsers);
        System.out.println(">>>   - Emails sent: " + emailsSent);
    }

    // ========== Legacy Subscriber Methods (for backward compatibility) ==========

    /**
     * Convert Job to ResEmailJob (legacy method, kept for backward compatibility)
     */
    public ResEmailJob convertJobToSendEmail(Job job) {
        return convertJobToEmailFormat(job);
    }

    /**
     * Gửi email cho subscribers (legacy method, kept for backward compatibility)
     * Note: This method uses Subscriber table, not User table
     */
    public void sendSubscribersEmailJobs() {
        List<Subscriber> listSubs = this.subscriberRepository.findAll();
        if (listSubs != null && listSubs.size() > 0) {
            for (Subscriber sub : listSubs) {
                List<Skill> listSkills = sub.getSkills();
                if (listSkills != null && listSkills.size() > 0) {
                    List<Job> listJobs = this.jobRepository.findBySkillsIn(listSkills);
                    if (listJobs != null && listJobs.size() > 0) {

                        List<ResEmailJob> arr = listJobs.stream().map(
                                job -> this.convertJobToSendEmail(job)).collect(Collectors.toList());

                        this.emailService.sendEmailFromTemplateSync(
                                sub.getEmail(),
                                "Cơ hội việc làm hot đang chờ đón bạn, khám phá ngay",
                                "job",
                                sub.getName(),
                                arr);
                    }
                }
            }
        }
    }
}
