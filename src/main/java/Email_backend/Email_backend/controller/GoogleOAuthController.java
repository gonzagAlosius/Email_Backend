package Email_backend.Email_backend.controller;

import Email_backend.Email_backend.model.UserEmailConfig;
import Email_backend.Email_backend.repository.UserEmailConfigRepository;
import Email_backend.Email_backend.service.EncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@CrossOrigin
public class GoogleOAuthController {

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    @Value("${google.redirect-uri}")
    private String redirectUri;

    @Autowired
    private UserEmailConfigRepository userEmailConfigRepository;

    @Autowired
    private EncryptionService encryptionService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping("/oauth/google/login")
    public void login(@RequestParam(value = "redirect", required = false) String appRedirectUrl,
            HttpServletResponse response) throws Exception {
        String state = appRedirectUrl != null
                ? Base64.getUrlEncoder().encodeToString(appRedirectUrl.getBytes(StandardCharsets.UTF_8))
                : "";
        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8") +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode("openid email profile https://mail.google.com/", "UTF-8") +
                "&access_type=offline" +
                "&state=" + state;
        response.sendRedirect(authUrl);
    }

    @GetMapping("/oauth/google/callback")
    public void callback(@RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state, HttpServletResponse response)
            throws Exception {
        String tokenEndpoint = "https://oauth2.googleapis.com/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String requestBody = "client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8") +
                "&code=" + URLEncoder.encode(code, "UTF-8") +
                "&grant_type=authorization_code" +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8");

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(tokenEndpoint, entity, Map.class);

        if (tokenResponse.getStatusCode() == HttpStatus.OK && tokenResponse.getBody() != null) {
            Map<String, Object> body = tokenResponse.getBody();
            String accessToken = (String) body.get("access_token");
            String refreshToken = (String) body.get("refresh_token");
            String idToken = (String) body.get("id_token");

            String email = decodeEmailFromIdToken(idToken);

            if (email != null && refreshToken != null) {
                // save refresh token in db
                Optional<UserEmailConfig> existing = userEmailConfigRepository.findByEmailAddress(email);
                UserEmailConfig configEntity = existing.orElse(new UserEmailConfig());
                if (!existing.isPresent()) {
                    configEntity.setMailboxId(UUID.randomUUID());
                    configEntity.setUserId(UUID.randomUUID());
                    configEntity.setOrgcode(101L);
                    configEntity.setEmailAddress(email);
                    configEntity.setIsActive(true);
                    configEntity.setCdate(java.time.LocalDateTime.now());
                    configEntity.setCuser("system");
                } else {
                    configEntity.setEdate(java.time.LocalDateTime.now());
                    configEntity.setEuser("system");
                }
                configEntity.setEncryptedPassword(encryptionService.encrypt(refreshToken));
                userEmailConfigRepository.save(configEntity);
            }

            String flutterRedirectUrl = "/";
            if (state != null && !state.isEmpty()) {
                try {
                    flutterRedirectUrl = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    flutterRedirectUrl = "/";
                }
            }

            // Append tokens to url so Flutter can extract and save them.
            String finalUrl = flutterRedirectUrl + (flutterRedirectUrl.contains("?") ? "&" : "?") +
                    "token=" + URLEncoder.encode(accessToken != null ? accessToken : "", "UTF-8") +
                    "&email=" + URLEncoder.encode(email != null ? email : "", "UTF-8") +
                    "&provider=google";

            response.sendRedirect(finalUrl);
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Failed to exchange token with Google.");
        }
    }

    @PostMapping("/api/auth/google/refresh")
    public ResponseEntity<?> refreshGoogleToken(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Email is required"));
        }

        Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(email);
        if (!configOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "User not found"));
        }

        try {
            String refreshToken = encryptionService.decrypt(configOpt.get().getEncryptedPassword());

            String tokenEndpoint = "https://oauth2.googleapis.com/token";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String requestBody = "client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                    "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8") +
                    "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8") +
                    "&grant_type=refresh_token";

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(tokenEndpoint, entity, Map.class);

            if (tokenResponse.getStatusCode() == HttpStatus.OK && tokenResponse.getBody() != null) {
                Map<String, Object> body = tokenResponse.getBody();
                String newAccessToken = (String) body.get("access_token");
                Map<String, String> res = new HashMap<>();
                res.put("access_token", newAccessToken);
                res.put("email", email);
                return ResponseEntity.ok(res);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Collections.singletonMap("error", "Failed to refresh token"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Error processing refresh token"));
        }
    }

    private String decodeEmailFromIdToken(String idToken) {
        if (idToken == null)
            return null;
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length > 1) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                Map<String, Object> map = mapper.readValue(payload, Map.class);
                return (String) map.get("email");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
