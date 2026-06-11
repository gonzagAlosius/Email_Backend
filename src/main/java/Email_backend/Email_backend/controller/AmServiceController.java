package Email_backend.Email_backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import Email_backend.Email_backend.repository.UserEmailConfigRepository;
import Email_backend.Email_backend.model.UserEmailConfig;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/am")
@CrossOrigin(origins = "*")
public class AmServiceController {

    @Autowired
    private UserEmailConfigRepository userEmailConfigRepository;

    @Value("${accessmanager.base-url}")
    private String accessManagerBaseUrl;

    @Value("${email.product-code:3}")
    private Integer emailProductCode;

    @Value("${email.orgcode.uuid-prefix:00000000-0000-0000-0000-}")
    private String uuidPrefix;

    private final RestTemplate restTemplate = new RestTemplate();

    private java.util.UUID convertOrgCodeToUuid(Number orgCode) {
        if (orgCode == null) return null;
        String formatted = String.format("%012d", orgCode.longValue());
        return java.util.UUID.fromString(uuidPrefix + formatted);
    }

    private java.util.UUID convertUserScdToUuid(String userScd) {
        if (userScd == null) return null;
        try {
            return java.util.UUID.fromString(userScd);
        } catch (Exception e) {
            return java.util.UUID.nameUUIDFromBytes(userScd.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @PostMapping("/exchange-token")
    public ResponseEntity<?> exchangeToken(@RequestHeader("Authorization") String authHeader,
                                           @RequestBody(required = false) Map<String, Object> body) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authorization header missing or invalid");
        }

        try {
            String url = accessManagerBaseUrl + "/exchange/exchange-token";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authHeader);

            Map<String, Object> requestBody = new HashMap<>();
            // Use passed productCode or default to Email's product code (3)
            Integer prodCode = emailProductCode;
            if (body != null && body.containsKey("productCode")) {
                Object val = body.get("productCode");
                if (val instanceof Number) {
                    prodCode = ((Number) val).intValue();
                } else if (val instanceof String) {
                    prodCode = Integer.parseInt((String) val);
                }
            }
            requestBody.put("productCode", prodCode);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            // Sync/Upsert UserEmailConfig in mail102 if response is 200 and has session_data
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map bodyMap = response.getBody();
                if (bodyMap.containsKey("session_data")) {
                    Map sessionData = (Map) bodyMap.get("session_data");
                    String email = (String) sessionData.get("email");
                    Object orgCodeObj = sessionData.get("orgCode");
                    String userScd = (String) sessionData.get("userScd");
                    
                    if (email != null && !email.trim().isEmpty()) {
                        try {
                            Number orgCode = null;
                            if (orgCodeObj instanceof Number) {
                                orgCode = (Number) orgCodeObj;
                            } else if (orgCodeObj instanceof String) {
                                orgCode = Long.parseLong((String) orgCodeObj);
                            }
                            
                            java.util.UUID orgUuid = convertOrgCodeToUuid(orgCode);
                            java.util.UUID userUuid = convertUserScdToUuid(userScd);
                            
                            java.util.Optional<UserEmailConfig> existingOpt = userEmailConfigRepository.findByEmailAddress(email);
                            UserEmailConfig configEntity;
                            if (existingOpt.isPresent()) {
                                configEntity = existingOpt.get();
                                if (orgUuid != null) {
                                    configEntity.setOrgcode(orgUuid);
                                }
                                if (userUuid != null) {
                                    configEntity.setUserId(userUuid);
                                }
                                configEntity.setEdate(java.time.LocalDateTime.now());
                                configEntity.setEuser("system");
                            } else {
                                configEntity = new UserEmailConfig();
                                configEntity.setMailboxId(java.util.UUID.randomUUID());
                                configEntity.setOrgcode(orgUuid != null ? orgUuid : java.util.UUID.randomUUID());
                                configEntity.setUserId(userUuid != null ? userUuid : java.util.UUID.randomUUID());
                                configEntity.setEmailAddress(email);
                                configEntity.setEncryptedPassword(""); // Empty password initially, will be set on direct login or OAuth use
                                configEntity.setIsActive(true);
                                configEntity.setCdate(java.time.LocalDateTime.now());
                                configEntity.setCuser("system");
                            }
                            userEmailConfigRepository.save(configEntity);
                            System.out.println("Synced UserEmailConfig orgcode & userId for: " + email);
                        } catch (Exception syncEx) {
                            System.err.println("Failed to sync UserEmailConfig during exchange-token: " + syncEx.getMessage());
                            syncEx.printStackTrace();
                        }
                    }
                }
            }
            
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> err = new HashMap<>();
            err.put("error", "AccessManager Token Exchange Failed");
            err.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    @PostMapping("/user-sync")
    public ResponseEntity<?> syncUser(@RequestHeader("Authorization") String authHeader,
                                      @RequestBody Map<String, Object> body) {
        return proxyPost("/user/get-user", authHeader, body);
    }

    @GetMapping("/get-user")
    public ResponseEntity<?> getUser(@RequestHeader("Authorization") String authHeader,
                                     @RequestParam String userCode,
                                     @RequestParam Long orgCode) {
        try {
            String url = accessManagerBaseUrl + "/user/get-user?userCode=" + userCode + "&orgCode=" + orgCode;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authHeader);
            headers.set("X-Internal-Call", "true");

            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/organization-sync")
    public ResponseEntity<?> syncOrganization(@RequestHeader("Authorization") String authHeader,
                                              @RequestBody Map<String, Object> body) {
        return proxyPost("/organization/get-organization", authHeader, body);
    }

    @PostMapping("/products-sync")
    public ResponseEntity<?> syncProducts(@RequestHeader("Authorization") String authHeader,
                                          @RequestBody Map<String, Object> body) {
        return proxyPost("/product/get-products", authHeader, body);
    }

    @PostMapping("/branches-sync")
    public ResponseEntity<?> syncBranches(@RequestHeader("Authorization") String authHeader,
                                          @RequestBody Map<String, Object> body) {
        return proxyPost("/branch/get-branches", authHeader, body);
    }

    @PostMapping("/reset-password/{userScd}/{orgCode}")
    public ResponseEntity<?> resetPassword(@RequestHeader("Authorization") String authHeader,
                                           @PathVariable String userScd,
                                           @PathVariable Long orgCode,
                                           @RequestBody Map<String, Object> body) {
        return proxyPost("/auth/reset-password/" + userScd + "/" + orgCode, authHeader, body);
    }

    private ResponseEntity<?> proxyPost(String path, String authHeader, Map<String, Object> body) {
        try {
            String url = accessManagerBaseUrl + path;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (authHeader != null) {
                headers.set("Authorization", authHeader);
            }
            headers.set("X-Internal-Call", "true");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
