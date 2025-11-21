package vn.hoidanit.jobhunter.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Favorite;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.favorite.FavoriteCompanyDTO;
import vn.hoidanit.jobhunter.domain.response.favorite.FavoriteJobDTO;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.repository.FavoriteRepository;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@Service
public class FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;

    public FavoriteService(
            FavoriteRepository favoriteRepository,
            UserRepository userRepository,
            JobRepository jobRepository,
            CompanyRepository companyRepository) {
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    public List<FavoriteJobDTO> getFavoriteJobsForCurrentUser() throws IdInvalidException {
        User user = getCurrentUser();
        List<Favorite> favorites = this.favoriteRepository.findByUserAndJobIsNotNull(user);
        if (favorites == null || favorites.isEmpty()) {
            return Collections.emptyList();
        }
        return favorites.stream()
                .map(this::convertToFavoriteJobDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FavoriteCompanyDTO> getFavoriteCompaniesForCurrentUser() throws IdInvalidException {
        User user = getCurrentUser();
        List<Favorite> favorites = this.favoriteRepository.findByUserAndCompanyIsNotNull(user);
        if (favorites == null || favorites.isEmpty()) {
            return Collections.emptyList();
        }
        return favorites.stream()
                .map(this::convertToFavoriteCompanyDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public FavoriteJobDTO saveJobToFavorites(long jobId) throws IdInvalidException {
        User user = getCurrentUser();
        Job job = this.jobRepository.findById(jobId)
                .orElseThrow(() -> new IdInvalidException("Job không tồn tại"));

        Optional<Favorite> existingFavorite = this.favoriteRepository.findByUserAndJobId(user, jobId);
        if (existingFavorite.isPresent()) {
            return convertToFavoriteJobDTO(existingFavorite.get());
        }

        Favorite favorite = new Favorite();
        favorite.setUser(user);
        favorite.setJob(job);
        favorite.setCompany(null);

        Favorite saved = this.favoriteRepository.save(favorite);
        return convertToFavoriteJobDTO(saved);
    }

    @Transactional
    public void removeJobFromFavorites(long jobId) throws IdInvalidException {
        User user = getCurrentUser();
        Favorite favorite = this.favoriteRepository.findByUserAndJobId(user, jobId)
                .orElseThrow(() -> new IdInvalidException("Bạn chưa lưu công việc này"));
        this.favoriteRepository.delete(favorite);
    }

    @Transactional(readOnly = true)
    public boolean isJobFavorited(long jobId) throws IdInvalidException {
        User user = getCurrentUser();
        return this.favoriteRepository.findByUserAndJobId(user, jobId).isPresent();
    }

    @Transactional
    public FavoriteCompanyDTO saveCompanyToFavorites(long companyId) throws IdInvalidException {
        User user = getCurrentUser();
        Company company = this.companyRepository.findById(companyId)
                .orElseThrow(() -> new IdInvalidException("Company không tồn tại"));

        Optional<Favorite> existingFavorite = this.favoriteRepository.findByUserAndCompanyId(user, companyId);
        if (existingFavorite.isPresent()) {
            return convertToFavoriteCompanyDTO(existingFavorite.get());
        }

        Favorite favorite = new Favorite();
        favorite.setUser(user);
        favorite.setCompany(company);
        favorite.setJob(null);

        Favorite saved = this.favoriteRepository.save(favorite);
        return convertToFavoriteCompanyDTO(saved);
    }

    @Transactional
    public void removeCompanyFromFavorites(long companyId) throws IdInvalidException {
        User user = getCurrentUser();
        Favorite favorite = this.favoriteRepository.findByUserAndCompanyId(user, companyId)
                .orElseThrow(() -> new IdInvalidException("Bạn chưa lưu công ty này"));
        this.favoriteRepository.delete(favorite);
    }

    @Transactional(readOnly = true)
    public boolean isCompanyFavorited(long companyId) throws IdInvalidException {
        User user = getCurrentUser();
        return this.favoriteRepository.findByUserAndCompanyId(user, companyId).isPresent();
    }

    private FavoriteJobDTO convertToFavoriteJobDTO(Favorite favorite) {
        FavoriteJobDTO dto = new FavoriteJobDTO();
        dto.setFavoriteId(favorite.getId());
        dto.setSavedAt(favorite.getSavedAt());
        if (favorite.getJob() != null) {
            Job job = favorite.getJob();
            dto.setJobId(job.getId());
            dto.setJobName(job.getName());
            dto.setLocation(job.getLocation());
            dto.setSalary(job.getSalary());
            dto.setLevel(job.getLevel() != null ? job.getLevel().name() : null);
            dto.setActive(job.isActive());
            if (job.getCompany() != null) {
                dto.setCompanyName(job.getCompany().getName());
                dto.setCompanyLogo(job.getCompany().getLogo());
            }
        }
        return dto;
    }

    private FavoriteCompanyDTO convertToFavoriteCompanyDTO(Favorite favorite) {
        FavoriteCompanyDTO dto = new FavoriteCompanyDTO();
        dto.setFavoriteId(favorite.getId());
        dto.setSavedAt(favorite.getSavedAt());
        if (favorite.getCompany() != null) {
            Company company = favorite.getCompany();
            dto.setCompanyId(company.getId());
            dto.setName(company.getName());
            dto.setAddress(company.getAddress());
            dto.setLogo(company.getLogo());
        }
        return dto;
    }

    private User getCurrentUser() throws IdInvalidException {
        String email = SecurityUtil.getCurrentUserLogin().orElse("");
        if (email.isEmpty()) {
            throw new IdInvalidException("Vui lòng đăng nhập để sử dụng chức năng này");
        }
        User user = this.userRepository.findByEmail(email);
        if (user == null) {
            throw new IdInvalidException("Không tìm thấy thông tin người dùng");
        }
        return user;
    }
}



