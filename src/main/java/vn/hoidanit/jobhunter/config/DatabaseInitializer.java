package vn.hoidanit.jobhunter.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.hoidanit.jobhunter.domain.Permission;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.CareerArticle;
import vn.hoidanit.jobhunter.repository.PermissionRepository;
import vn.hoidanit.jobhunter.repository.RoleRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.repository.CareerArticleRepository;
import vn.hoidanit.jobhunter.util.constant.GenderEnum;

@Service
public class DatabaseInitializer implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CareerArticleRepository careerArticleRepository;

    public DatabaseInitializer(
            PermissionRepository permissionRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            CareerArticleRepository careerArticleRepository) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.careerArticleRepository = careerArticleRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println(">>> START INIT DATABASE");
        long countPermissions = this.permissionRepository.count();
        long countRoles = this.roleRepository.count();
        long countUsers = this.userRepository.count();

        if (countPermissions == 0) {
            ArrayList<Permission> arr = new ArrayList<>();
            arr.add(new Permission("Create a company", "/api/v1/companies", "POST", "COMPANIES"));
            arr.add(new Permission("Update a company", "/api/v1/companies", "PUT", "COMPANIES"));
            arr.add(new Permission("Delete a company", "/api/v1/companies/{id}", "DELETE", "COMPANIES"));
            arr.add(new Permission("Get a company by id", "/api/v1/companies/{id}", "GET", "COMPANIES"));
            arr.add(new Permission("Get companies with pagination", "/api/v1/companies", "GET", "COMPANIES"));

            arr.add(new Permission("Create a job", "/api/v1/jobs", "POST", "JOBS"));
            arr.add(new Permission("Update a job", "/api/v1/jobs", "PUT", "JOBS"));
            arr.add(new Permission("Delete a job", "/api/v1/jobs/{id}", "DELETE", "JOBS"));
            arr.add(new Permission("Get a job by id", "/api/v1/jobs/{id}", "GET", "JOBS"));
            arr.add(new Permission("Get jobs with pagination", "/api/v1/jobs", "GET", "JOBS"));

            arr.add(new Permission("Create a permission", "/api/v1/permissions", "POST", "PERMISSIONS"));
            arr.add(new Permission("Update a permission", "/api/v1/permissions", "PUT", "PERMISSIONS"));
            arr.add(new Permission("Delete a permission", "/api/v1/permissions/{id}", "DELETE", "PERMISSIONS"));
            arr.add(new Permission("Get a permission by id", "/api/v1/permissions/{id}", "GET", "PERMISSIONS"));
            arr.add(new Permission("Get permissions with pagination", "/api/v1/permissions", "GET", "PERMISSIONS"));

            arr.add(new Permission("Create a resume", "/api/v1/resumes", "POST", "RESUMES"));
            arr.add(new Permission("Update a resume", "/api/v1/resumes", "PUT", "RESUMES"));
            arr.add(new Permission("Delete a resume", "/api/v1/resumes/{id}", "DELETE", "RESUMES"));
            arr.add(new Permission("Get a resume by id", "/api/v1/resumes/{id}", "GET", "RESUMES"));
            arr.add(new Permission("Get resumes with pagination", "/api/v1/resumes", "GET", "RESUMES"));

            arr.add(new Permission("Create a role", "/api/v1/roles", "POST", "ROLES"));
            arr.add(new Permission("Update a role", "/api/v1/roles", "PUT", "ROLES"));
            arr.add(new Permission("Delete a role", "/api/v1/roles/{id}", "DELETE", "ROLES"));
            arr.add(new Permission("Get a role by id", "/api/v1/roles/{id}", "GET", "ROLES"));
            arr.add(new Permission("Get roles with pagination", "/api/v1/roles", "GET", "ROLES"));

            arr.add(new Permission("Create a user", "/api/v1/users", "POST", "USERS"));
            arr.add(new Permission("Update a user", "/api/v1/users", "PUT", "USERS"));
            arr.add(new Permission("Delete a user", "/api/v1/users/{id}", "DELETE", "USERS"));
            arr.add(new Permission("Get a user by id", "/api/v1/users/{id}", "GET", "USERS"));
            arr.add(new Permission("Get users with pagination", "/api/v1/users", "GET", "USERS"));

            arr.add(new Permission("Download a file", "/api/v1/files", "POST", "FILES"));
            arr.add(new Permission("Upload a file", "/api/v1/files", "GET", "FILES"));

            // Career articles (public GET)
            arr.add(new Permission("Get career articles", "/api/v1/career-articles", "GET", "CAREER_ARTICLES"));

            this.permissionRepository.saveAll(arr);
        }

        // Tạo các role cần thiết nếu chưa tồn tại
        if (countRoles == 0) {
            List<Permission> allPermissions = this.permissionRepository.findAll();

            // Tạo role SUPER_ADMIN
            Role adminRole = new Role();
            adminRole.setName("SUPER_ADMIN");
            adminRole.setDescription("Admin thì full permissions");
            adminRole.setActive(true);
            adminRole.setPermissions(allPermissions);
            this.roleRepository.save(adminRole);

            // Tạo role NORMAL_USER (Ứng viên) - không có permission nào
            Role normalUserRole = new Role();
            normalUserRole.setName("NORMAL_USER");
            normalUserRole.setDescription("Role cho ứng viên tìm việc");
            normalUserRole.setActive(true);
            normalUserRole.setPermissions(new ArrayList<>()); // Ứng viên không có permission quản lý
            this.roleRepository.save(normalUserRole);

            // Tạo role HR (Nhà tuyển dụng) - có permission quản lý job và company của mình
            Role hrRole = new Role();
            hrRole.setName("HR");
            hrRole.setDescription("Role cho nhà tuyển dụng");
            hrRole.setActive(true);
            // HR có permission quản lý job và company
            List<Permission> hrPermissions = allPermissions.stream()
                    .filter(p -> p.getModule().equals("JOBS") || p.getModule().equals("COMPANIES") || p.getModule().equals("RESUMES"))
                    .collect(java.util.stream.Collectors.toList());
            hrRole.setPermissions(hrPermissions);
            this.roleRepository.save(hrRole);

            // Tạo role HR_PENDING - chỉ được quản lý thông tin công ty của mình
            Role hrPendingRole = new Role();
            hrPendingRole.setName("HR_PENDING");
            hrPendingRole.setDescription("Role cho HR chờ admin phê duyệt công ty");
            hrPendingRole.setActive(true);
            List<Permission> hrPendingPermissions = allPermissions.stream()
                    .filter(p -> p.getModule().equals("COMPANIES") && p.getMethod().equalsIgnoreCase("GET"))
                    .collect(java.util.stream.Collectors.toList());
            hrPendingRole.setPermissions(hrPendingPermissions);
            this.roleRepository.save(hrPendingRole);
        } else {
            // Kiểm tra và tạo các role còn thiếu nếu database đã có data
            if (this.roleRepository.findByName("NORMAL_USER") == null) {
                Role normalUserRole = new Role();
                normalUserRole.setName("NORMAL_USER");
                normalUserRole.setDescription("Role cho ứng viên tìm việc");
                normalUserRole.setActive(true);
                normalUserRole.setPermissions(new ArrayList<>());
                this.roleRepository.save(normalUserRole);
            }
            
            if (this.roleRepository.findByName("HR") == null) {
                List<Permission> allPermissions = this.permissionRepository.findAll();
                Role hrRole = new Role();
                hrRole.setName("HR");
                hrRole.setDescription("Role cho nhà tuyển dụng");
                hrRole.setActive(true);
                List<Permission> hrPermissions = allPermissions.stream()
                        .filter(p -> p.getModule().equals("JOBS") || p.getModule().equals("COMPANIES") || p.getModule().equals("RESUMES"))
                        .collect(java.util.stream.Collectors.toList());
                hrRole.setPermissions(hrPermissions);
                this.roleRepository.save(hrRole);
            }

            if (this.roleRepository.findByName("HR_PENDING") == null) {
                List<Permission> allPermissions = this.permissionRepository.findAll();
                Role hrPendingRole = new Role();
                hrPendingRole.setName("HR_PENDING");
                hrPendingRole.setDescription("Role cho HR chờ admin phê duyệt công ty");
                hrPendingRole.setActive(true);
                List<Permission> hrPendingPermissions = allPermissions.stream()
                        .filter(p -> p.getModule().equals("COMPANIES") && p.getMethod().equalsIgnoreCase("GET"))
                        .collect(java.util.stream.Collectors.toList());
                hrPendingRole.setPermissions(hrPendingPermissions);
                this.roleRepository.save(hrPendingRole);
            }
        }

        if (countUsers == 0) {
            User adminUser = new User();
            adminUser.setEmail("admin@gmail.com");
            adminUser.setAddress("hn");
            adminUser.setAge(25);
            adminUser.setGender(GenderEnum.MALE);
            adminUser.setName("I'm super admin");
            adminUser.setPassword(this.passwordEncoder.encode("123456"));

            Role adminRole = this.roleRepository.findByName("SUPER_ADMIN");
            if (adminRole != null) {
                adminUser.setRole(adminRole);
            }

            this.userRepository.save(adminUser);
        }

        if (countPermissions > 0 && countRoles > 0 && countUsers > 0) {
            System.out.println(">>> SKIP INIT DATABASE ~ ALREADY HAVE DATA...");
        } else
            System.out.println(">>> END INIT DATABASE");

        // Seed sample Career Articles if empty
        try {
            long articleCount = this.careerArticleRepository.count();
            if (articleCount == 0) {
                ArrayList<CareerArticle> articles = new ArrayList<>();
                // Sample career articles với links chính thức, có thể truy cập được
                // Admin có thể quản lý và cập nhật links thực tế qua admin panel
                
                CareerArticle a1 = new CareerArticle();
                a1.setTitle("Xu hướng nghề nghiệp 2024: Những ngành nghề hot nhất tại Việt Nam");
                a1.setDescription("Phân tích chi tiết về các ngành nghề đang phát triển mạnh tại Việt Nam trong năm 2024, từ công nghệ thông tin đến marketing số và tài chính.");
                a1.setCategory("XU HƯỚNG NGHỀ NGHIỆP");
                a1.setCategoryColor("#e6f7ff");
                a1.setImage("https://images.unsplash.com/photo-1521737604893-d14cc237f11d?w=800");
                a1.setLink("https://vnexpress.net/kinh-te-viec-lam"); // Link chính thức VnExpress
                a1.setActive(true);
                articles.add(a1);

                CareerArticle a2 = new CareerArticle();
                a2.setTitle("Báo cáo thị trường lao động Q4/2024: Mức lương trung bình tăng 15%");
                a2.setDescription("Báo cáo mới nhất về tình hình thị trường lao động Việt Nam, phân tích mức lương, nhu cầu tuyển dụng và xu hướng việc làm theo ngành nghề.");
                a2.setCategory("THỊ TRƯỜNG LAO ĐỘNG");
                a2.setCategoryColor("#fff7e6");
                a2.setImage("https://images.unsplash.com/photo-1552664730-d307ca884978?w=800");
                a2.setLink("https://www.vietnamworks.com"); // Link chính thức VietnamWorks
                a2.setActive(true);
                articles.add(a2);

                CareerArticle a3 = new CareerArticle();
                a3.setTitle("Top 10 kỹ năng mềm quan trọng nhất trong năm 2024");
                a3.setDescription("Danh sách các kỹ năng mềm được nhà tuyển dụng đánh giá cao nhất, bao gồm giao tiếp, làm việc nhóm, tư duy phản biện và khả năng thích ứng.");
                a3.setCategory("KỸ NĂNG NGHỀ NGHIỆP");
                a3.setCategoryColor("#f6ffed");
                a3.setImage("https://images.unsplash.com/photo-1556761175-5973dc0f32e7?w=800");
                a3.setLink("https://www.topcv.vn"); // Link chính thức TopCV
                a3.setActive(true);
                articles.add(a3);

                CareerArticle a4 = new CareerArticle();
                a4.setTitle("Remote Work: Tương lai của môi trường làm việc hiện đại");
                a4.setDescription("Phân tích về xu hướng làm việc từ xa, những lợi ích và thách thức, cùng với các công cụ và kỹ năng cần thiết để thành công trong môi trường remote.");
                a4.setCategory("XU HƯỚNG NGHỀ NGHIỆP");
                a4.setCategoryColor("#e6f7ff");
                a4.setImage("https://images.unsplash.com/photo-1551434678-e076c223a692?w=800");
                a4.setLink("https://www.linkedin.com/learning/topics/career-development"); // Link chính thức LinkedIn Learning
                a4.setActive(true);
                articles.add(a4);

                this.careerArticleRepository.saveAll(articles);
                System.out.println(">>> Seeded sample career articles");
            }
        } catch (Exception e) {
            System.out.println(">>> Seed career articles failed: " + e.getMessage());
        }
    }

}
