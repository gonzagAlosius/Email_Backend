package Email_backend.Email_backend.controller;

import Email_backend.Email_backend.dto.LoginRequest;
import Email_backend.Email_backend.dto.SignupRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.mail.Session;
import javax.mail.Store;
import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Value("${mail.imap.host}")
    private String imapHost;

    @Value("${mail.imap.port}")
    private String imapPort;

    @Value("${cpanel.host:mail.botsuat.com}")
    private String cpanelHost;

    @Value("${cpanel.port:2083}")
    private String cpanelPort;

    @Value("${cpanel.username:}")
    private String cpanelUsername;

    @Value("${cpanel.token:}")
    private String cpanelToken;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        Map<String, String> response = new HashMap<>();
        try {
            // Attempt to connect to IMAP server to verify credentials dynamically
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.host", imapHost);
            props.put("mail.imaps.port", imapPort);
            props.put("mail.imaps.ssl.trust", imapHost);

            Session session = Session.getInstance(props, null);
            Store store = session.getStore("imaps");
            store.connect(imapHost, loginRequest.getEmail(), loginRequest.getPassword());
            
            // If connection succeeds, credentials are valid
            store.close();
            
            response.put("message", "Login successful");
            response.put("email", loginRequest.getEmail());
            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("error", "Invalid email or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest signupRequest) {
        Map<String, String> response = new HashMap<>();
        
        String email = signupRequest.getEmail();
        String password = signupRequest.getPassword();
        
        if (email == null || !email.contains("@") || password == null || password.isEmpty()) {
            response.put("error", "Invalid email address or password.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        
        if (cpanelUsername == null || cpanelUsername.trim().isEmpty() || 
            cpanelUsername.contains("YOUR_CPANEL_USERNAME") ||
            cpanelToken == null || cpanelToken.trim().isEmpty() || 
            cpanelToken.contains("YOUR_CPANEL_API_TOKEN")) {
            
            response.put("error", "cPanel API credentials are not configured in application.properties. Please set cpanel.username and cpanel.token first!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            int atIdx = email.indexOf('@');
            String emailUser = email.substring(0, atIdx);
            String emailDomain = email.substring(atIdx + 1);

            // Disable SSL verification to prevent issues with self-signed / missing SSL certificates
            disableSslVerification();

            String urlStr = "https://" + cpanelHost + ":" + cpanelPort + "/execute/Email/add_pop";
            String postData = "email=" + URLEncoder.encode(emailUser, "UTF-8")
                    + "&domain=" + URLEncoder.encode(emailDomain, "UTF-8")
                    + "&password=" + URLEncoder.encode(password, "UTF-8")
                    + "&quota=0";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "cpanel " + cpanelUsername + ":" + cpanelToken);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            StringBuilder responseBuilder = new StringBuilder();
            
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    responseBuilder.append(responseLine.trim());
                }
            }

            String respStr = responseBuilder.toString();
            System.out.println("cPanel API Status Code: " + responseCode);
            System.out.println("cPanel API Response: " + respStr);

            if (respStr.contains("\"status\":1") || respStr.contains("\"status\": 1")) {
                response.put("message", "Email account '" + email + "' created successfully in Bluehost! Please login.");
                return ResponseEntity.ok().body(response);
            } else {
                // Parse out cPanel error message if available
                String errorMsg = "";
                if (respStr.contains("\"errors\":")) {
                    int errorsIdx = respStr.indexOf("\"errors\":");
                    String errorsPart = respStr.substring(errorsIdx);
                    if (errorsPart.contains("[") && errorsPart.contains("]")) {
                        errorMsg = errorsPart.substring(errorsPart.indexOf("[") + 1, errorsPart.indexOf("]"))
                                .replace("\"", "").trim();
                    }
                }
                
                if (errorMsg.isEmpty()) {
                    errorMsg = "cPanel UAPI Error. Response: " + respStr;
                }
                
                response.put("error", errorMsg);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.put("error", "Error contacting cPanel API: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private void disableSslVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) { return true; }
            };
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
