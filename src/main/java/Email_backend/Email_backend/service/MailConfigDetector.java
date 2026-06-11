package Email_backend.Email_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MailConfigDetector {

    private static String microsoftClientId;
    private static String microsoftScope;
    private static String microsoftDomains;

    @Value("${microsoft.oauth.client-id:04b47bff-348d-41d1-829a-f4276486e287}")
    public void setMicrosoftClientId(String clientId) {
        MailConfigDetector.microsoftClientId = clientId;
    }

    @Value("${microsoft.oauth.scope:openid profile email https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send offline_access}")
    public void setMicrosoftScope(String scope) {
        MailConfigDetector.microsoftScope = scope;
    }

    @Value("${microsoft.oauth.domains:botsedge.ai}")
    public void setMicrosoftDomains(String domains) {
        MailConfigDetector.microsoftDomains = domains;
    }

    public static class Config {
        private final java.util.UUID orgcode;
        private final String imapHost;
        private final String imapPort;
        private final Boolean imapSecure;
        private final String smtpHost;
        private final String smtpPort;
        private final Boolean smtpSecure;
        private final String sentFolder;

        public Config(String imapHost, String imapPort, String smtpHost, String smtpPort, String sentFolder) {
            this(null, imapHost, imapPort, true, smtpHost, smtpPort, true, sentFolder);
        }

        public Config(java.util.UUID orgcode, String imapHost, String imapPort, Boolean imapSecure, String smtpHost, String smtpPort, Boolean smtpSecure, String sentFolder) {
            this.orgcode = orgcode;
            this.imapHost = imapHost;
            this.imapPort = imapPort;
            this.imapSecure = imapSecure;
            this.smtpHost = smtpHost;
            this.smtpPort = smtpPort;
            this.smtpSecure = smtpSecure;
            this.sentFolder = sentFolder;
        }

        public java.util.UUID getOrgcode() {
            return orgcode;
        }

        public String getImapHost() {
            return imapHost;
        }

        public String getImapPort() {
            return imapPort;
        }

        public Boolean getImapSecure() {
            return imapSecure != null ? imapSecure : true;
        }

        public String getSmtpHost() {
            return smtpHost;
        }

        public String getSmtpPort() {
            return smtpPort;
        }

        public Boolean getSmtpSecure() {
            return smtpSecure != null ? smtpSecure : true;
        }

        public String getSentFolder() {
            return sentFolder;
        }
    }

    public static boolean isMicrosoftDomain(String domain) {
        if (domain == null) return false;

        // Check standard Microsoft domains
        if (domain.endsWith("outlook.com") ||
            domain.endsWith("hotmail.com") ||
            domain.endsWith("live.com") ||
            domain.endsWith("msn.com") ||
            domain.endsWith("office365.com") ||
            domain.endsWith("live.in")) {
            return true;
        }

        // Check configurable Microsoft domains
        String domainsStr = microsoftDomains;
        if (domainsStr != null && !domainsStr.trim().isEmpty()) {
            String[] customDomains = domainsStr.split(",");
            for (String cd : customDomains) {
                if (domain.endsWith(cd.trim().toLowerCase())) {
                    return true;
                }
            }
        }

        return false;
    }


    public static boolean isOAuthToken(String password) {
        if (password == null) {
            return false;
        }
        String trimmed = password.trim();
        return trimmed.startsWith("ya29.") || trimmed.startsWith("ey") || trimmed.length() > 50;
    }

    public static String resolvePassword(String email, String password) {
        if (password == null || password.trim().isEmpty() || isOAuthToken(password)) {
            return password;
        }

        if (email == null || !email.contains("@")) {
            return password;
        }

        String domain = email.substring(email.indexOf("@") + 1).toLowerCase().trim();
        if (isMicrosoftDomain(domain)) {
            try {
                String tenant = "organizations";
                if (domain.endsWith("outlook.com") || domain.endsWith("hotmail.com") || 
                    domain.endsWith("live.com") || domain.endsWith("msn.com") || domain.endsWith("live.in")) {
                    tenant = "consumers";
                }
                
                java.net.URL url = new java.net.URL("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String scope = microsoftScope;
                String postData = "grant_type=password"
                        + "&client_id=" + microsoftClientId
                        + "&username=" + java.net.URLEncoder.encode(email, "UTF-8")
                        + "&password=" + java.net.URLEncoder.encode(password, "UTF-8")
                        + "&scope=" + java.net.URLEncoder.encode(scope, "UTF-8");

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = postData.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            response.append(line.trim());
                        }
                        String respStr = response.toString();
                        int tokenIdx = respStr.indexOf("\"access_token\":\"");
                        if (tokenIdx != -1) {
                            int start = tokenIdx + 16;
                            int end = respStr.indexOf("\"", start);
                            return respStr.substring(start, end);
                        }
                    }
                } else {
                    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                        StringBuilder errorResponse = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            errorResponse.append(line.trim());
                        }
                        System.err.println("ROPC Token Exchange Failed (" + responseCode + "): " + errorResponse.toString());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return password;
    }
}
