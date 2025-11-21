package vn.hoidanit.jobhunter.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import jakarta.validation.Valid;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqLoginDTO;
import vn.hoidanit.jobhunter.domain.request.ReqRegisterDTO;
import vn.hoidanit.jobhunter.domain.response.ResCreateUserDTO;
import vn.hoidanit.jobhunter.domain.response.ResLoginDTO;
import vn.hoidanit.jobhunter.domain.response.ResUserDTO;
import vn.hoidanit.jobhunter.repository.RoleRepository;
import vn.hoidanit.jobhunter.service.CompanyService;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final SecurityUtil securityUtil;
    private final UserService userService;
    private final CompanyService companyService;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;

    private static final String ROLE_NORMAL_USER = "NORMAL_USER";
    private static final String ROLE_HR = "HR";
    private static final String ROLE_HR_PENDING = "HR_PENDING";

    @Value("${hoidanit.jwt.refresh-token-validity-in-seconds}")
    private long refreshTokenExpiration;

    @Value("${hoidanit.jwt.cookie-secure:true}")
    private boolean cookieSecure;

    @Value("${hoidanit.jwt.cookie-samesite:Lax}")
    private String cookieSameSite;

    public AuthController(
            AuthenticationManagerBuilder authenticationManagerBuilder,
            SecurityUtil securityUtil,
            UserService userService,
            CompanyService companyService,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            RestTemplate restTemplate) {
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.securityUtil = securityUtil;
        this.userService = userService;
        this.companyService = companyService;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.restTemplate = restTemplate;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ResLoginDTO> login(@Valid @RequestBody ReqLoginDTO loginDto) {
        // Nạp input gồm username/password vào Security
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                loginDto.getUsername(), loginDto.getPassword());

        // xác thực người dùng => cần viết hàm loadUserByUsername
        Authentication authentication = authenticationManagerBuilder.getObject()
                .authenticate(authenticationToken);

        // set thông tin người dùng đăng nhập vào context (có thể sử dụng sau này)
        SecurityContextHolder.getContext().setAuthentication(authentication);

        ResLoginDTO res = new ResLoginDTO();
        User currentUserDB = this.userService.handleGetUserByUsername(loginDto.getUsername());
        if (currentUserDB != null) {
            ResLoginDTO.UserLogin userLogin = new ResLoginDTO.UserLogin(
                    currentUserDB.getId(),
                    currentUserDB.getEmail(),
                    currentUserDB.getName(),
                    currentUserDB.getRole());
            res.setUser(userLogin);
        }

        // create access token
        String access_token = this.securityUtil.createAccessToken(authentication.getName(), res);
        res.setAccessToken(access_token);

        // create refresh token
        String refresh_token = this.securityUtil.createRefreshToken(loginDto.getUsername(), res);

        // update user
        this.userService.updateUserToken(refresh_token, loginDto.getUsername());

        // set cookies
        ResponseCookie resCookies = ResponseCookie
                .from("refresh_token", refresh_token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(refreshTokenExpiration)
                .sameSite(cookieSameSite)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, resCookies.toString())
                .body(res);
    }

    @GetMapping("/auth/account")
    @ApiMessage("fetch account")
    public ResponseEntity<ResUserDTO> getAccount() {
        String email = SecurityUtil.getCurrentUserLogin().isPresent()
                ? SecurityUtil.getCurrentUserLogin().get()
                : "";

        User currentUserDB = this.userService.handleGetUserByUsername(email);
        if (currentUserDB != null) {
            ResUserDTO userDTO = this.userService.convertToResUserDTO(currentUserDB);
            return ResponseEntity.ok().body(userDTO);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @GetMapping("/auth/refresh")
    @ApiMessage("Get User by refresh token")
    public ResponseEntity<ResLoginDTO> getRefreshToken(
            @CookieValue(name = "refresh_token", defaultValue = "abc") String refresh_token) throws IdInvalidException {
        if (refresh_token.equals("abc")) {
            throw new IdInvalidException("Bạn không có refresh token ở cookie");
        }
        // check valid
        Jwt decodedToken = this.securityUtil.checkValidRefreshToken(refresh_token);
        String email = decodedToken.getSubject();

        // check user by token + email
        User currentUser = this.userService.getUserByRefreshTokenAndEmail(refresh_token, email);
        if (currentUser == null) {
            throw new IdInvalidException("Refresh Token không hợp lệ");
        }

        // issue new token/set refresh token as cookies
        ResLoginDTO res = new ResLoginDTO();
        User currentUserDB = this.userService.handleGetUserByUsername(email);
        if (currentUserDB != null) {
            ResLoginDTO.UserLogin userLogin = new ResLoginDTO.UserLogin(
                    currentUserDB.getId(),
                    currentUserDB.getEmail(),
                    currentUserDB.getName(),
                    currentUserDB.getRole());
            res.setUser(userLogin);
        }

        // create access token
        String access_token = this.securityUtil.createAccessToken(email, res);
        res.setAccessToken(access_token);

        // create refresh token
        String new_refresh_token = this.securityUtil.createRefreshToken(email, res);

        // update user
        this.userService.updateUserToken(new_refresh_token, email);

        // set cookies
        ResponseCookie resCookies = ResponseCookie
                .from("refresh_token", new_refresh_token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(refreshTokenExpiration)
                .sameSite(cookieSameSite)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, resCookies.toString())
                .body(res);
    }

    @PostMapping("/auth/logout")
    @ApiMessage("Logout User")
    public ResponseEntity<Void> logout() throws IdInvalidException {
        String email = SecurityUtil.getCurrentUserLogin().isPresent() ? SecurityUtil.getCurrentUserLogin().get() : "";

        if (email.equals("")) {
            throw new IdInvalidException("Access Token không hợp lệ");
        }

        // update refresh token = null
        this.userService.updateUserToken("", email);

        // remove refresh token cookie
        ResponseCookie deleteSpringCookie = ResponseCookie
                .from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite(cookieSameSite)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteSpringCookie.toString())
                .body(null);
    }

    @PostMapping("/auth/register")
    @ApiMessage("Register a new user")
    public ResponseEntity<ResCreateUserDTO> register(@Valid @RequestBody ReqRegisterDTO registerDTO)
            throws IdInvalidException {
        boolean isEmailExist = this.userService.isEmailExist(registerDTO.getEmail());
        if (isEmailExist) {
            throw new IdInvalidException(
                    "Email " + registerDTO.getEmail() + " đã tồn tại, vui lòng sử dụng email khác.");
        }

        User newUser = new User();
        newUser.setName(registerDTO.getName());
        newUser.setEmail(registerDTO.getEmail());
        newUser.setAge(registerDTO.getAge());

        String hashPassword = this.passwordEncoder.encode(registerDTO.getPassword());
        newUser.setPassword(hashPassword);

        String accountType = Optional.ofNullable(registerDTO.getAccountType()).orElse("CANDIDATE");

        if ("HR".equalsIgnoreCase(accountType)) {
            // HR đăng ký với trạng thái chờ phê duyệt
            newUser.setRole(this.getRoleOrThrow(ROLE_HR_PENDING));
            newUser.setCompany(null);
        } else {
            newUser.setRole(this.getRoleOrThrow(ROLE_NORMAL_USER));
        }

        User createdUser = this.userService.handleCreateUser(newUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(this.userService.convertToResCreateUserDTO(createdUser));
    }

    private Role getRoleOrThrow(String roleName) throws IdInvalidException {
        Role role = this.roleRepository.findByName(roleName);
        if (role == null) {
            throw new IdInvalidException("Role " + roleName + " chưa được cấu hình. Vui lòng tạo role này trước.");
        }
        return role;
    }

    @PostMapping("/auth/google/login")
    @ApiMessage("Login with Google Authorization Code")
    public ResponseEntity<ResLoginDTO> googleLogin(@RequestParam("code") String code,
            @RequestParam("redirectUri") String redirectUri,
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret}") String clientSecret) {
        try {
            // 1) Exchange code for tokens (x-www-form-urlencoded)
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("code", code);
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("redirect_uri", redirectUri);
            form.add("grant_type", "authorization_code");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(form, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResponse = restTemplate.postForObject(
                    "https://oauth2.googleapis.com/token", httpEntity, Map.class);

            if (tokenResponse == null || tokenResponse.get("access_token") == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String accessTokenGoogle = tokenResponse.get("access_token").toString();

            // 2) Fetch user info with Authorization header
            HttpHeaders userHeaders = new HttpHeaders();
            userHeaders.setBearerAuth(accessTokenGoogle);
            HttpEntity<Void> userReq = new HttpEntity<>(userHeaders);
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> userResp = restTemplate.exchange(
                    "https://openidconnect.googleapis.com/v1/userinfo",
                    HttpMethod.GET,
                    userReq,
                    Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> userInfo = userResp.getBody();

            if (userInfo == null || userInfo.get("email") == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String email = userInfo.get("email").toString();
            // String name = userInfo.get("name") != null ? userInfo.get("name").toString() : "Google User";

            // 3) Check user in DB
            Optional<User> userOptional = Optional.ofNullable(this.userService.handleGetUserByUsername(email));
            if (userOptional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            User user = userOptional.get();

            ResLoginDTO res = new ResLoginDTO();
            ResLoginDTO.UserLogin userLogin = new ResLoginDTO.UserLogin(
                    user.getId(), user.getEmail(), user.getName(), user.getRole());
            res.setUser(userLogin);
            String siteAccessToken = this.securityUtil.createAccessToken(email, res);
            res.setAccessToken(siteAccessToken);

            return ResponseEntity.ok(res);
        } catch (HttpClientErrorException ex) {
            return ResponseEntity.status(ex.getStatusCode()).build();
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/auth/google/register")
    @ApiMessage("Register with Google Authorization Code")
    public ResponseEntity<ResLoginDTO> googleRegister(@RequestParam("code") String code,
            @RequestParam("redirectUri") String redirectUri,
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret}") String clientSecret) {
        try {
            // 1) Exchange code for tokens (x-www-form-urlencoded)
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("code", code);
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("redirect_uri", redirectUri);
            form.add("grant_type", "authorization_code");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(form, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResponse = restTemplate.postForObject(
                    "https://oauth2.googleapis.com/token", httpEntity, Map.class);

            if (tokenResponse == null || tokenResponse.get("access_token") == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String accessTokenGoogle = tokenResponse.get("access_token").toString();

            // 2) Fetch user info with Authorization header
            HttpHeaders userHeaders = new HttpHeaders();
            userHeaders.setBearerAuth(accessTokenGoogle);
            HttpEntity<Void> userReq = new HttpEntity<>(userHeaders);
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> userResp = restTemplate.exchange(
                    "https://openidconnect.googleapis.com/v1/userinfo",
                    HttpMethod.GET,
                    userReq,
                    Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> userInfo = userResp.getBody();

            if (userInfo == null || userInfo.get("email") == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String email = userInfo.get("email").toString();
            String name = userInfo.get("name") != null ? userInfo.get("name").toString() : "Google User";

            // 3) Check if user already exists
            User existingUser = this.userService.handleGetUserByUsername(email);
            if (existingUser != null) {
                // User exists, treat as login
                ResLoginDTO res = new ResLoginDTO();
                ResLoginDTO.UserLogin userLogin = new ResLoginDTO.UserLogin(
                        existingUser.getId(), existingUser.getEmail(), existingUser.getName(), existingUser.getRole());
                res.setUser(userLogin);
                String siteAccessToken = this.securityUtil.createAccessToken(email, res);
                res.setAccessToken(siteAccessToken);

                return ResponseEntity.ok(res);
            }

            // 4) Create new user
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(name);
            newUser.setPassword("OAUTH2_USER"); // Default password for OAuth users
            newUser.setAge(25); // Default age

            User createdUser = this.userService.handleCreateUser(newUser);

            // 5) Issue JWT and response
            ResLoginDTO res = new ResLoginDTO();
            ResLoginDTO.UserLogin userLogin = new ResLoginDTO.UserLogin(
                    createdUser.getId(), createdUser.getEmail(), createdUser.getName(), createdUser.getRole());
            res.setUser(userLogin);
            String siteAccessToken = this.securityUtil.createAccessToken(email, res);
            res.setAccessToken(siteAccessToken);

            return ResponseEntity.ok(res);
        } catch (HttpClientErrorException ex) {
            return ResponseEntity.status(ex.getStatusCode()).build();
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/auth/facebook/login")
    @ApiMessage("Login with Facebook access token")
    public ResponseEntity<ResLoginDTO> facebookLogin(@RequestParam("accessToken") String accessToken) {
        try {
            // Verify token and fetch user info
            String url = "https://graph.facebook.com/me?fields=id,name,email&access_token=" + accessToken;
            String response = restTemplate.getForObject(url, String.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> info = new com.fasterxml.jackson.databind.ObjectMapper().readValue(response, Map.class);

            String email = info.get("email") != null ? info.get("email").toString() : null;
            String facebookId = info.get("id") != null ? info.get("id").toString() : null;
            String name = info.get("name") != null ? info.get("name").toString() : "Facebook User";

            // Some FB accounts don't expose email; fallback to synthetic email
            if (email == null && facebookId != null) {
                email = facebookId + "@facebook.local";
            }

            if (email == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            User user = this.userService.handleGetUserByUsername(email);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            ResLoginDTO res = new ResLoginDTO();
            ResLoginDTO.UserLogin userLogin = new ResLoginDTO.UserLogin(
                    user.getId(), user.getEmail(), user.getName(), user.getRole());
            res.setUser(userLogin);
            String siteAccessToken = this.securityUtil.createAccessToken(email, res);
            res.setAccessToken(siteAccessToken);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/auth/facebook/register")
    @ApiMessage("Register with Facebook access token")
    public ResponseEntity<ResLoginDTO> facebookRegister(@RequestParam("accessToken") String accessToken) {
        try {
            // Verify token and fetch user info
            String url = "https://graph.facebook.com/me?fields=id,name,email&access_token=" + accessToken;
            String response = restTemplate.getForObject(url, String.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> info = new com.fasterxml.jackson.databind.ObjectMapper().readValue(response, Map.class);

            String email = info.get("email") != null ? info.get("email").toString() : null;
            String facebookId = info.get("id") != null ? info.get("id").toString() : null;
            String name = info.get("name") != null ? info.get("name").toString() : "Facebook User";

            // Fallback email for accounts without email permission
            if (email == null && facebookId != null) {
                email = facebookId + "@facebook.local";
            }

            if (email == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            User existing = this.userService.handleGetUserByUsername(email);
            if (existing != null) {
                ResLoginDTO res = new ResLoginDTO();
                ResLoginDTO.UserLogin userLogin = new ResLoginDTO.UserLogin(
                        existing.getId(), existing.getEmail(), existing.getName(), existing.getRole());
                res.setUser(userLogin);
                String siteAccessToken = this.securityUtil.createAccessToken(email, res);
                res.setAccessToken(siteAccessToken);
                return ResponseEntity.ok(res);
            }

            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(name);
            newUser.setPassword("OAUTH2_USER");
            newUser.setAge(25);
            User created = this.userService.handleCreateUser(newUser);

            ResLoginDTO res = new ResLoginDTO();
            ResLoginDTO.UserLogin userLogin = new ResLoginDTO.UserLogin(
                    created.getId(), created.getEmail(), created.getName(), created.getRole());
            res.setUser(userLogin);
            String siteAccessToken = this.securityUtil.createAccessToken(email, res);
            res.setAccessToken(siteAccessToken);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
