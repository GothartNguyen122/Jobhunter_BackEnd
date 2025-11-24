package vn.hoidanit.jobhunter.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResCreateUserDTO;
import vn.hoidanit.jobhunter.domain.response.ResUpdateUserDTO;
import vn.hoidanit.jobhunter.domain.response.ResUserDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.domain.Skill;
import vn.hoidanit.jobhunter.repository.SkillRepository;
import vn.hoidanit.jobhunter.repository.JobAlertRepository;
import vn.hoidanit.jobhunter.domain.JobAlert;
import vn.hoidanit.jobhunter.repository.FavoriteRepository;
import vn.hoidanit.jobhunter.domain.Favorite;
import vn.hoidanit.jobhunter.repository.FeedbackRepository;
import vn.hoidanit.jobhunter.domain.Feedback;
import vn.hoidanit.jobhunter.repository.UserCvRepository;
import vn.hoidanit.jobhunter.domain.UserCv;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@Service
public class UserService {
    
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final RoleService roleService;
    private final SkillRepository skillRepository;
    private final JobAlertRepository jobAlertRepository;
    private final FavoriteRepository favoriteRepository;
    private final FeedbackRepository feedbackRepository;
    private final UserCvRepository userCvRepository;
    private final ResumeRepository resumeRepository;

    public UserService(UserRepository userRepository,
            CompanyService companyService,
            RoleService roleService,
            SkillRepository skillRepository,
            JobAlertRepository jobAlertRepository,
            FavoriteRepository favoriteRepository,
            FeedbackRepository feedbackRepository,
            UserCvRepository userCvRepository,
            ResumeRepository resumeRepository) {
        this.userRepository = userRepository;
        this.companyService = companyService;
        this.roleService = roleService;
        this.skillRepository = skillRepository;
        this.jobAlertRepository = jobAlertRepository;
        this.favoriteRepository = favoriteRepository;
        this.feedbackRepository = feedbackRepository;
        this.userCvRepository = userCvRepository;
        this.resumeRepository = resumeRepository;
    }

    public User handleCreateUser(User user) {
        // check company
        if (user.getCompany() != null) {
            Optional<Company> companyOptional = this.companyService.findById(user.getCompany().getId());
            user.setCompany(companyOptional.isPresent() ? companyOptional.get() : null);
        }

        // check role
        if (user.getRole() != null) {
            Role r = this.roleService.fetchById(user.getRole().getId());
            user.setRole(r != null ? r : null);
        }

        return this.userRepository.save(user);
    }

    /**
     * Xóa user và tất cả dữ liệu liên quan
     * Business rules:
     * - Không cho phép xóa SUPER_ADMIN
     * - Xóa theo thứ tự để tránh foreign key constraint
     * 
     * @param id User ID cần xóa
     * @throws IdInvalidException Nếu user không tồn tại hoặc là SUPER_ADMIN
     */
    @Transactional
    public void handleDeleteUser(long id) throws IdInvalidException {
        // Tìm user trước
        Optional<User> userOptional = this.userRepository.findById(id);
        if (userOptional.isEmpty()) {
            throw new IdInvalidException("User với id = " + id + " không tồn tại");
        }

        User user = userOptional.get();

        // Business rule: Không cho phép xóa SUPER_ADMIN
        if (user.getRole() != null && ROLE_SUPER_ADMIN.equalsIgnoreCase(user.getRole().getName())) {
            throw new IdInvalidException("Không thể xóa tài khoản SUPER_ADMIN");
        }

        // Xóa tất cả các entity liên quan trước (để tránh foreign key constraint)
        // Thứ tự xóa quan trọng: xóa từ child đến parent
        
        // 1. Xóa JobAlert (cần clear skills trước vì có join table)
        deleteJobAlertsByUser(user);
        
        // 2-5. Xóa các entity khác (tối ưu: dùng helper method chung)
        deleteUserRelatedEntities(user, this.favoriteRepository::findByUser, this.favoriteRepository::deleteAll);
        deleteUserRelatedEntities(user, this.feedbackRepository::findByUser, this.feedbackRepository::deleteAll);
        deleteUserRelatedEntities(user, this.userCvRepository::findByUser, this.userCvRepository::deleteAll);
        deleteUserRelatedEntities(user, this.resumeRepository::findByUser, this.resumeRepository::deleteAll);

        // Cuối cùng mới xóa user
        this.userRepository.deleteById(id);
    }

    /**
     * Xóa JobAlert của user (cần clear skills trước vì có join table)
     */
    private void deleteJobAlertsByUser(User user) {
        List<JobAlert> jobAlerts = this.jobAlertRepository.findByUser(user);
        if (jobAlerts != null && !jobAlerts.isEmpty()) {
            jobAlerts.forEach(alert -> alert.getSkills().clear());
            this.jobAlertRepository.saveAll(jobAlerts);
            this.jobAlertRepository.deleteAll(jobAlerts);
        }
    }

    /**
     * Helper method tối ưu: xóa entities theo pattern chung (findByUser -> deleteAll)
     */
    @SuppressWarnings("unchecked")
    private <T> void deleteUserRelatedEntities(User user, 
                                               java.util.function.Function<User, List<T>> finder, 
                                               java.util.function.Consumer<List<T>> deleter) {
        List<T> entities = finder.apply(user);
        if (entities != null && !entities.isEmpty()) {
            deleter.accept(entities);
        }
    }

    public User fetchUserById(long id) {
        Optional<User> userOptional = this.userRepository.findById(id);
        if (userOptional.isPresent()) {
            return userOptional.get();
        }
        return null;
    }

    public ResultPaginationDTO fetchAllUser(Specification<User> spec, Pageable pageable) {
        Page<User> pageUser = this.userRepository.findAll(spec, pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();

        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());

        mt.setPages(pageUser.getTotalPages());
        mt.setTotal(pageUser.getTotalElements());

        rs.setMeta(mt);

        // remove sensitive data
        List<ResUserDTO> listUser = pageUser.getContent()
                .stream().map(item -> this.convertToResUserDTO(item))
                .collect(Collectors.toList());

        rs.setResult(listUser);

        return rs;
    }

    public User handleUpdateUser(User reqUser) {
        User currentUser = this.fetchUserById(reqUser.getId());
        if (currentUser != null) {
            currentUser.setAddress(reqUser.getAddress());
            currentUser.setGender(reqUser.getGender());
            currentUser.setAge(reqUser.getAge());
            currentUser.setName(reqUser.getName());
            currentUser.setPhone(reqUser.getPhone());

            // check company
            if (reqUser.getCompany() != null) {
                Optional<Company> companyOptional = this.companyService.findById(reqUser.getCompany().getId());
                currentUser.setCompany(companyOptional.isPresent() ? companyOptional.get() : null);
            }

            // check role
            if (reqUser.getRole() != null) {
                Role r = this.roleService.fetchById(reqUser.getRole().getId());
                currentUser.setRole(r != null ? r : null);
            }

            // update salary luôn, level nếu khác null
            currentUser.setSalary(reqUser.getSalary());
            if (reqUser.getLevel() != null) {
                currentUser.setLevel(reqUser.getLevel());
            }
            // update skills giống bên job
            if (reqUser.getSkills() != null) {
                List<Long> reqSkills = reqUser.getSkills()
                        .stream().map(x -> x.getId())
                        .collect(Collectors.toList());
                List<Skill> dbSkills = this.skillRepository.findByIdIn(reqSkills);
                currentUser.setSkills(dbSkills);
            }

            // update
            currentUser = this.userRepository.save(currentUser);
        }
        return currentUser;
    }

    public User handleGetUserByUsername(String username) {
        return this.userRepository.findByEmail(username);
    }

    public boolean isEmailExist(String email) {
        return this.userRepository.existsByEmail(email);
    }

    public ResCreateUserDTO convertToResCreateUserDTO(User user) {
        ResCreateUserDTO res = new ResCreateUserDTO();
        ResCreateUserDTO.CompanyUser com = new ResCreateUserDTO.CompanyUser();

        res.setId(user.getId());
        res.setEmail(user.getEmail());
        res.setName(user.getName());
        res.setAge(user.getAge());
        res.setCreatedAt(user.getCreatedAt());
        res.setGender(user.getGender());
        res.setAddress(user.getAddress());
        res.setPhone(user.getPhone());
        res.setSalary(user.getSalary());
        res.setLevel(user.getLevel());
        if (user.getSkills() != null) {
            List<String> skills = user.getSkills()
                    .stream().map(item -> item.getName())
                    .collect(Collectors.toList());
            res.setSkills(skills);
        }

        if (user.getCompany() != null) {
            com.setId(user.getCompany().getId());
            com.setName(user.getCompany().getName());
            res.setCompany(com);
        }
        return res;
    }

    public ResUpdateUserDTO convertToResUpdateUserDTO(User user) {
        ResUpdateUserDTO res = new ResUpdateUserDTO();
        ResUpdateUserDTO.CompanyUser com = new ResUpdateUserDTO.CompanyUser();
        if (user.getCompany() != null) {
            com.setId(user.getCompany().getId());
            com.setName(user.getCompany().getName());
            res.setCompany(com);
        }

        res.setId(user.getId());
        res.setName(user.getName());
        res.setAge(user.getAge());
        res.setUpdatedAt(user.getUpdatedAt());
        res.setGender(user.getGender());
        res.setAddress(user.getAddress());
        res.setPhone(user.getPhone());
        return res;
    }

    public ResUserDTO convertToResUserDTO(User user) {
        ResUserDTO res = new ResUserDTO();
        ResUserDTO.CompanyUser com = new ResUserDTO.CompanyUser();
        ResUserDTO.RoleUser roleUser = new ResUserDTO.RoleUser();
        if (user.getCompany() != null) {
            com.setId(user.getCompany().getId());
            com.setName(user.getCompany().getName());
            res.setCompany(com);
        }

        if (user.getRole() != null) {
            roleUser.setId(user.getRole().getId());
            roleUser.setName(user.getRole().getName());
            roleUser.setPermissions(user.getRole().getPermissions());
            res.setRole(roleUser);
        }
        res.setSkills(user.getSkills());
        res.setId(user.getId());
        res.setEmail(user.getEmail());
        res.setName(user.getName());
        res.setAge(user.getAge());
        res.setSalary(user.getSalary());
        res.setLevel(user.getLevel());
        res.setUpdatedAt(user.getUpdatedAt());
        res.setCreatedAt(user.getCreatedAt());
        res.setGender(user.getGender());
        res.setAddress(user.getAddress());
        res.setPhone(user.getPhone());
        return res;
    }

    public void updateUserToken(String token, String email) {
        User currentUser = this.handleGetUserByUsername(email);
        if (currentUser != null) {
            currentUser.setRefreshToken(token);
            this.userRepository.save(currentUser);
        }
    }

    public User getUserByRefreshTokenAndEmail(String token, String email) {
        return this.userRepository.findByRefreshTokenAndEmail(token, email);
    }
}
