package vn.hoidanit.jobhunter.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.hoidanit.jobhunter.domain.Permission;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.error.PermissionException;

public class PermissionInterceptor implements HandlerInterceptor {

    @Autowired
    UserService userService;

    @Override
    @Transactional
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response, @NonNull Object handler)
            throws Exception {

        String path = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String httpMethod = request.getMethod();
        // Debug logs - disabled for production
        // System.out.println(">>> RUN preHandle");
        // System.out.println(">>> path= " + path);
        // System.out.println(">>> httpMethod= " + httpMethod);
        // System.out.println(">>> requestURI= " + request.getRequestURI());

        // Skip permission check for public endpoints that should be accessible even when user is authenticated
        if (isPublicEndpoint(path, httpMethod)) {
            return true;
        }

        // Check if user is authenticated
        String email = SecurityUtil.getCurrentUserLogin().orElse("");
        
        // Allow authenticated users to access endpoints without specific permission
        if (!email.isEmpty() && isAuthenticatedUserEndpoint(path, httpMethod)) {
            return true;
        }

        // Check permission for other endpoints (requires specific permission)
        if (!email.isEmpty()) {
            User user = this.userService.handleGetUserByUsername(email);
            if (user == null) {
                throw new PermissionException("Bạn không có quyền truy cập endpoint này.");
            }

            Role role = user.getRole();
            if (role == null) {
                throw new PermissionException("Bạn không có quyền truy cập endpoint này.");
            }

            List<Permission> permissions = role.getPermissions();
            boolean hasPermission = permissions.stream()
                    .anyMatch(item -> item.getApiPath().equals(path) && item.getMethod().equals(httpMethod));

            if (!hasPermission) {
                throw new PermissionException("Bạn không có quyền truy cập endpoint này.");
            }
        }

        return true;
    }

    private boolean isPublicEndpoint(String path, String method) {
        if (path == null || method == null) {
            return false;
        }

        // Public GET endpoints
        if ("GET".equalsIgnoreCase(method)) {
            return "/api/v1/career-articles".equals(path)
                    || "/api/v1/categories".equals(path);
        }

        return false;
    }

    /**
     * Check if endpoint is accessible by all authenticated users (no specific permission required)
     */
    private boolean isAuthenticatedUserEndpoint(String path, String method) {
        if (path == null || method == null) {
            return false;
        }

        // Endpoints accessible by all authenticated users
        if ("GET".equalsIgnoreCase(method)) {
            return "/api/v1/jobs/matching".equals(path)
                    || "/api/v1/jobs/matching/count".equals(path)
                    || "/api/v1/job-alerts/matching-jobs".equals(path)
                    || "/api/v1/favorites/jobs".equals(path)
                    || "/api/v1/favorites/companies".equals(path)
                    || "/api/v1/favorites/jobs/check/{jobId}".equals(path)
                    || "/api/v1/favorites/companies/check/{companyId}".equals(path)
                    || "/api/v1/job-alerts/my".equals(path)
                    || "/api/v1/user-cvs/my".equals(path)
                    || (path.startsWith("/api/v1/user-cvs/") && path.matches("/api/v1/user-cvs/\\d+"));
        } else if ("POST".equalsIgnoreCase(method)) {
            return "/api/v1/favorites/jobs/{jobId}".equals(path)
                    || "/api/v1/favorites/companies/{companyId}".equals(path)
                    || "/api/v1/job-alerts".equals(path)
                    || "/api/v1/user-cvs".equals(path)
                    || "/api/v1/feedback".equals(path)
                    || (path.startsWith("/api/v1/job-alerts/") && path.endsWith("/toggle"))
                    || (path.startsWith("/api/v1/user-cvs/") && path.endsWith("/set-default"));
        } else if ("PUT".equalsIgnoreCase(method)) {
            return (path.startsWith("/api/v1/job-alerts/") && path.matches("/api/v1/job-alerts/\\d+"))
                    || (path.startsWith("/api/v1/user-cvs/") && path.matches("/api/v1/user-cvs/\\d+"));
        } else if ("DELETE".equalsIgnoreCase(method)) {
            return "/api/v1/favorites/jobs/{jobId}".equals(path)
                    || "/api/v1/favorites/companies/{companyId}".equals(path)
                    || (path.startsWith("/api/v1/job-alerts/") && path.matches("/api/v1/job-alerts/\\d+"))
                    || (path.startsWith("/api/v1/user-cvs/") && path.matches("/api/v1/user-cvs/\\d+"));
        }

        return false;
    }
}
