package vn.hoidanit.jobhunter.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.UserCv;
import vn.hoidanit.jobhunter.domain.request.ReqUserCvDTO;
import vn.hoidanit.jobhunter.domain.response.ResUserCvDTO;
import vn.hoidanit.jobhunter.repository.UserCvRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@Service
public class UserCvService {

    private final UserCvRepository userCvRepository;
    private final UserRepository userRepository;

    public UserCvService(UserCvRepository userCvRepository, UserRepository userRepository) {
        this.userCvRepository = userCvRepository;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() throws IdInvalidException {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new IdInvalidException("Vui lòng đăng nhập để sử dụng chức năng này"));
        User user = this.userRepository.findByEmail(email);
        if (user == null) {
            throw new IdInvalidException("Không tìm thấy thông tin người dùng");
        }
        return user;
    }

    public List<ResUserCvDTO> getMyCvs() throws IdInvalidException {
        User currentUser = getCurrentUser();
        List<UserCv> cvs = this.userCvRepository.findByUser(currentUser);
        return cvs.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public ResUserCvDTO getCvById(long id) throws IdInvalidException {
        User currentUser = getCurrentUser();
        UserCv cv = this.userCvRepository.findById(id)
                .orElseThrow(() -> new IdInvalidException("Không tìm thấy CV"));

        // Check ownership
        if (cv.getUser() == null || cv.getUser().getId() != currentUser.getId()) {
            throw new IdInvalidException("Bạn không có quyền xem CV này");
        }

        return convertToDTO(cv);
    }

    @Transactional
    public ResUserCvDTO createCv(ReqUserCvDTO dto) throws IdInvalidException {
        User currentUser = getCurrentUser();

        UserCv cv = new UserCv();
        cv.setUser(currentUser);
        cv.setTitle(dto.getTitle() != null && !dto.getTitle().isBlank() ? dto.getTitle() : "My CV");
        cv.setPdfUrl(dto.getPdfUrl());
        cv.setDefaultCv(dto.getDefaultCv() != null ? dto.getDefaultCv() : false);

        // If this is set as default, unset other defaults
        if (cv.isDefaultCv()) {
            Optional<UserCv> existingDefault = this.userCvRepository.findByUserIdAndDefaultCvTrue(currentUser.getId());
            if (existingDefault.isPresent()) {
                existingDefault.get().setDefaultCv(false);
                this.userCvRepository.save(existingDefault.get());
            }
        }

        UserCv saved = this.userCvRepository.save(cv);
        return convertToDTO(saved);
    }

    @Transactional
    public ResUserCvDTO updateCv(long id, ReqUserCvDTO dto) throws IdInvalidException {
        User currentUser = getCurrentUser();
        UserCv cv = this.userCvRepository.findById(id)
                .orElseThrow(() -> new IdInvalidException("Không tìm thấy CV"));

        // Check ownership
        if (cv.getUser() == null || cv.getUser().getId() != currentUser.getId()) {
            throw new IdInvalidException("Bạn không có quyền chỉnh sửa CV này");
        }

        if (dto.getTitle() != null) {
            cv.setTitle(dto.getTitle());
        }
        if (dto.getPdfUrl() != null) {
            cv.setPdfUrl(dto.getPdfUrl());
        }
        if (dto.getDefaultCv() != null) {
            // If setting as default, unset other defaults
            if (dto.getDefaultCv()) {
                Optional<UserCv> existingDefault = this.userCvRepository.findByUserIdAndDefaultCvTrue(currentUser.getId());
                if (existingDefault.isPresent() && existingDefault.get().getId() != id) {
                    existingDefault.get().setDefaultCv(false);
                    this.userCvRepository.save(existingDefault.get());
                }
            }
            cv.setDefaultCv(dto.getDefaultCv());
        }

        UserCv saved = this.userCvRepository.save(cv);
        return convertToDTO(saved);
    }

    @Transactional
    public void deleteCv(long id) throws IdInvalidException {
        User currentUser = getCurrentUser();
        UserCv cv = this.userCvRepository.findById(id)
                .orElseThrow(() -> new IdInvalidException("Không tìm thấy CV"));

        // Check ownership
        if (cv.getUser() == null || cv.getUser().getId() != currentUser.getId()) {
            throw new IdInvalidException("Bạn không có quyền xóa CV này");
        }

        this.userCvRepository.delete(cv);
    }

    @Transactional
    public ResUserCvDTO setDefaultCv(long id) throws IdInvalidException {
        User currentUser = getCurrentUser();
        UserCv cv = this.userCvRepository.findById(id)
                .orElseThrow(() -> new IdInvalidException("Không tìm thấy CV"));

        // Check ownership
        if (cv.getUser() == null || cv.getUser().getId() != currentUser.getId()) {
            throw new IdInvalidException("Bạn không có quyền đặt CV này làm mặc định");
        }

        // Unset other defaults
        Optional<UserCv> existingDefault = this.userCvRepository.findByUserIdAndDefaultCvTrue(currentUser.getId());
        if (existingDefault.isPresent() && existingDefault.get().getId() != id) {
            existingDefault.get().setDefaultCv(false);
            this.userCvRepository.save(existingDefault.get());
        }

        cv.setDefaultCv(true);
        UserCv saved = this.userCvRepository.save(cv);
        return convertToDTO(saved);
    }

    private ResUserCvDTO convertToDTO(UserCv cv) {
        ResUserCvDTO dto = new ResUserCvDTO();
        dto.setId(cv.getId());
        dto.setTitle(cv.getTitle());
        dto.setPdfUrl(cv.getPdfUrl());
        dto.setDefaultCv(cv.isDefaultCv());
        dto.setCreatedAt(cv.getCreatedAt());
        dto.setUpdatedAt(cv.getUpdatedAt());
        return dto;
    }
}



