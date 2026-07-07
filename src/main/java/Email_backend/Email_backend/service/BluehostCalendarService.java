package Email_backend.Email_backend.service;

import Email_backend.Email_backend.model.Event;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.security.cert.X509Certificate;

@Service
public class BluehostCalendarService {

    public static class RoundcubeSession {
        public String cpsess;
        public String cookie;
        public String requestToken;
        public String unlock;
        public String last;
    }

    private static void disableSslVerification(HttpURLConnection conn) {
        if (conn instanceof HttpsURLConnection) {
            try {
                TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
                };
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
                httpsConn.setSSLSocketFactory(sc.getSocketFactory());
                httpsConn.setHostnameVerifier(new HostnameVerifier() {
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Helper method to follow redirects manually and preserve cookies
    private HttpURLConnection executeWithRedirects(String targetUrl, String method, String currentCookies) throws Exception {
        String urlString = targetUrl;
        HttpURLConnection conn;
        int redirects = 0;
        String cookies = currentCookies;
        
        while (redirects < 10) {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            disableSslVerification(conn);
            conn.setRequestMethod(method);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            if (cookies != null && !cookies.isEmpty()) {
                conn.setRequestProperty("Cookie", cookies);
            }
            
            int status = conn.getResponseCode();
            
            // Capture any newly set cookies
            for (int i = 1; ; i++) {
                String headerName = conn.getHeaderFieldKey(i);
                String headerValue = conn.getHeaderField(i);
                if (headerName == null && headerValue == null) break;
                if ("Set-Cookie".equalsIgnoreCase(headerName) && headerValue != null) {
                    cookies += headerValue.split(";")[0] + "; ";
                }
            }

            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 303) {
                String location = conn.getHeaderField("Location");
                if (location.startsWith("/")) {
                    URL currentUrl = new URL(urlString);
                    location = currentUrl.getProtocol() + "://" + currentUrl.getHost() + (currentUrl.getPort() != -1 ? ":" + currentUrl.getPort() : "") + location;
                }
                urlString = location;
                redirects++;
            } else {
                conn.setRequestProperty("Cookie", cookies); // save the final state
                return conn; // Return the final connection
            }
        }
        throw new Exception("Server redirected too many times (manual redirect limit reached)");
    }

    private RoundcubeSession loginAndGetSession(String email, String password) {
        try {
            if (email == null || !email.contains("@")) return null;
            String domain = email.substring(email.indexOf("@") + 1);
            
            // Phase 1: Login
            URL loginUrl = new URL("https://webmail." + domain + "/login/?login_only=1");
            HttpURLConnection loginConn = (HttpURLConnection) loginUrl.openConnection();
            disableSslVerification(loginConn);
            loginConn.setRequestMethod("POST");
            loginConn.setDoOutput(true);
            loginConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            
            String loginBody = "user=" + URLEncoder.encode(email, "UTF-8") + "&pass=" + URLEncoder.encode(password, "UTF-8");
            try (OutputStream os = loginConn.getOutputStream()) {
                os.write(loginBody.getBytes(StandardCharsets.UTF_8));
            }
            
            if (loginConn.getResponseCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode loginRes = mapper.readTree(loginConn.getInputStream());
                if (loginRes.has("security_token")) {
                    String cpsess = loginRes.get("security_token").asText();
                    
                    StringBuilder cookies = new StringBuilder();
                    for (int i = 1; ; i++) {
                        String headerName = loginConn.getHeaderFieldKey(i);
                        String headerValue = loginConn.getHeaderField(i);
                        if (headerName == null && headerValue == null) break;
                        if ("Set-Cookie".equalsIgnoreCase(headerName) && headerValue != null) {
                            cookies.append(headerValue.split(";")[0]).append("; ");
                        }
                    }
                    
                    // Phase 2: Open Calendar Page with manual redirect handling
                    String phase2Url = "https://webmail." + domain + cpsess + "/3rdparty/roundcube/?_task=calendar";
                    HttpURLConnection rcConn = executeWithRedirects(phase2Url, "GET", cookies.toString());
                    
                    if (rcConn.getResponseCode() == 200) {
                        // The final connection has the accumulated cookies in its RequestProperty
                        String finalCookies = rcConn.getRequestProperty("Cookie");
                        if (finalCookies != null) cookies = new StringBuilder(finalCookies);
                        
                        StringBuilder html = new StringBuilder();
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(rcConn.getInputStream()))) {
                            String line;
                            while ((line = br.readLine()) != null) html.append(line);
                        }
                        
                        String htmlStr = html.toString();
                        // System.out.println("[DEBUG] Roundcube Calendar HTML: " + htmlStr); // Uncomment to print full HTML
                        
                        // Extract X-Roundcube-Request (request_token)
                        String requestToken = "";
                        
                        // Method 1: JavaScript rcmail.set_env({"request_token":"..."})
                        int tokenIdx = htmlStr.indexOf("\"request_token\":\"");
                        if (tokenIdx != -1) {
                            int start = tokenIdx + 17;
                            int end = htmlStr.indexOf("\"", start);
                            if (end != -1) requestToken = htmlStr.substring(start, end);
                        } 
                        // Method 2: HTML hidden input <input type="hidden" name="_token" value="...">
                        else {
                            tokenIdx = htmlStr.indexOf("name=\"_token\" value=\"");
                            if (tokenIdx != -1) {
                                int start = tokenIdx + 21;
                                int end = htmlStr.indexOf("\"", start);
                                if (end != -1) requestToken = htmlStr.substring(start, end);
                            }
                        }
                        
                        if (requestToken.isEmpty()) {
                            System.out.println("[DEBUG] CRITICAL: Failed to extract X-Roundcube-Request token from HTML!");
                            // If needed for debugging: System.out.println(htmlStr);
                        } else {
                            System.out.println("[DEBUG] Successfully extracted CSRF token: " + requestToken);
                        }
                        
                        RoundcubeSession session = new RoundcubeSession();
                        session.cpsess = cpsess;
                        session.cookie = cookies.toString();
                        session.requestToken = requestToken;
                        session.unlock = "loading" + System.currentTimeMillis();
                        session.last = String.valueOf(System.currentTimeMillis() / 1000);
                        return session;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[DEBUG] Failed to get Roundcube session: " + e.getMessage());
        }
        return null;
    }

    public List<Event> fetchCalendarEvents(String email, String password) {
        List<Event> events = new ArrayList<>();
        RoundcubeSession session = loginAndGetSession(email, password);
        
        System.out.println("===== ROUNDCUBE SESSION =====");
        System.out.println("Session = " + (session != null));
        if (session != null) {
            System.out.println("cpsess = " + session.cpsess);
            System.out.println("Token = " + session.requestToken);
            System.out.println("Cookie = " + session.cookie);
        }

        if (session == null) return events;
        
        try {
            String domain = email.substring(email.indexOf("@") + 1);
            // Phase 3: Fetch Events using POST _action=refresh
            URL url = new URL("https://webmail." + domain + session.cpsess + "/3rdparty/roundcube/?_task=calendar&_action=refresh");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            disableSslVerification(conn);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Cookie", session.cookie);
            conn.setRequestProperty("X-Roundcube-Request", session.requestToken);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            
            long startTs = LocalDateTime.now().minusYears(1).toEpochSecond(ZoneOffset.UTC);
            long endTs = LocalDateTime.now().plusYears(5).toEpochSecond(ZoneOffset.UTC);
            
            String body = "_last=" + session.last + "&_remote=1&_unlock=" + session.unlock + "&start=" + startTs + "&end=" + endTs;
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while((line = br.readLine()) != null){
                    sb.append(line);
                }
                
                System.out.println("===== ROUNDCUBE RESPONSE =====");
                System.out.println(sb);

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(sb.toString());
                System.out.println(root.toPrettyString());
                System.out.println("Callbacks = " + root.get("callbacks"));
                
                // Phase 4: Parse JSON
                if (root.has("callbacks") && root.get("callbacks").isArray()) {
                    for (JsonNode callback : root.get("callbacks")) {
                        if (callback.isArray() && callback.size() > 1 && "plugin.refresh_calendar".equals(callback.get(0).asText())) {
                            JsonNode payload = callback.get(1);
                            if (payload.has("update")) {
                                JsonNode update = payload.get("update");
                                if (update.isObject()) {
                                    // Single Event
                                    if (update.has("title")) {
                                        events.add(parseEventNode(update, email));
                                    }
                                    // Multiple Events
                                    else if (update.has("events")) {
                                        for(JsonNode node : update.get("events")){
                                            events.add(parseEventNode(node, email));
                                        }
                                    }
                                } else if (update.isArray()) {
                                    for (JsonNode node : update) {
                                        events.add(parseEventNode(node, email));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch(Exception e) {
            System.out.println("[DEBUG] Failed to fetch Roundcube events: " + e.getMessage());
        }
        return events;
    }

    private Event parseEventNode(JsonNode node, String email) {
        Event event = new Event();
        if (node.has("id")) event.setGraphEventId("bluehost_" + node.get("id").asText());
        if (node.has("title")) event.setTitle(node.get("title").asText());
        if (node.has("description")) event.setDescription(node.get("description").asText());
        if (node.has("location")) event.setLocation(node.get("location").asText());
        
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        if (node.has("start")) {
            try { event.setStartTime(LocalDateTime.parse(node.get("start").asText(), formatter)); } catch(Exception ignored) {}
        }
        if (node.has("end")) {
            try { event.setEndTime(LocalDateTime.parse(node.get("end").asText(), formatter)); } catch(Exception ignored) {}
        }
        if (node.has("allDay")) {
            event.setAllDay(node.get("allDay").asBoolean());
        }
        
        event.setOrganizerEmail(email);
        return event;
    }

    public String createCalendarEvent(String email, String password, Email_backend.Email_backend.dto.EventRequest req, String existingId) {
        RoundcubeSession session = loginAndGetSession(email, password);
        if (session == null) return null;
        
        try {
            String domain = email.substring(email.indexOf("@") + 1);
            // Phase 5: Create/Update Event
            URL url = new URL("https://webmail." + domain + session.cpsess + "/3rdparty/roundcube/?_task=calendar&_action=event");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            disableSslVerification(conn);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Cookie", session.cookie);
            conn.setRequestProperty("X-Roundcube-Request", session.requestToken);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            
            StringBuilder body = new StringBuilder();
            body.append("action=").append(existingId != null ? "edit" : "new");
            body.append("&e[calendar]=1");
            if (existingId != null) {
                body.append("&e[id]=").append(URLEncoder.encode(existingId, "UTF-8"));
            }
            body.append("&e[title]=").append(URLEncoder.encode(req.getTitle() != null ? req.getTitle() : "Untitled", "UTF-8"));
            body.append("&e[description]=").append(URLEncoder.encode(req.getDescription() != null ? req.getDescription() : "", "UTF-8"));
            body.append("&e[location]=").append(URLEncoder.encode(req.getLocation() != null ? req.getLocation() : "", "UTF-8"));
            
            if (req.getStartTime() != null) body.append("&e[start]=").append(URLEncoder.encode(req.getStartTime().toString(), "UTF-8"));
            if (req.getEndTime() != null) body.append("&e[end]=").append(URLEncoder.encode(req.getEndTime().toString(), "UTF-8"));
            body.append("&e[allDay]=").append(req.isAllDay() ? "1" : "0");
            body.append("&_remote=1");
            body.append("&_unlock=").append(session.unlock);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            
            if (conn.getResponseCode() == 200) {
                StringBuilder res = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) res.append(line);
                }
                
                String respStr = res.toString();
                System.out.println("[DEBUG] Roundcube Create Response: " + respStr);
                
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(respStr);
                if (root.has("callbacks") && root.get("callbacks").isArray()) {
                    for (JsonNode callback : root.get("callbacks")) {
                        if (callback.isArray() && callback.size() > 1 && "plugin.refresh_calendar".equals(callback.get(0).asText())) {
                            JsonNode payload = callback.get(1);
                            if (payload.has("update")) {
                                JsonNode update = payload.get("update");
                                if (update.isObject() && update.has("_id")) {
                                    return "bluehost_" + update.get("_id").asText();
                                } else if (update.isArray() && update.size() > 0 && update.get(0).has("_id")) {
                                    return "bluehost_" + update.get(0).get("_id").asText();
                                }
                            }
                        }
                    }
                }
                
                return existingId != null ? "bluehost_" + existingId : "bluehost_fake_" + System.currentTimeMillis();
            }
        } catch(Exception e) {
            System.out.println("[DEBUG] Failed to create Roundcube event: " + e.getMessage());
        }
        return null;
    }

    public void deleteCalendarEvent(String email, String password, String graphEventId) {
        if (graphEventId == null || !graphEventId.startsWith("bluehost_")) return;
        String eventId = graphEventId.substring(9);
        
        RoundcubeSession session = loginAndGetSession(email, password);
        if (session == null) return;
        
        try {
            String domain = email.substring(email.indexOf("@") + 1);
            // Phase 6: Delete Event
            URL url = new URL("https://webmail." + domain + session.cpsess + "/3rdparty/roundcube/?_task=calendar&_action=event");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            disableSslVerification(conn);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Cookie", session.cookie);
            conn.setRequestProperty("X-Roundcube-Request", session.requestToken);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            
            String body = "action=remove&e[id]=" + URLEncoder.encode(eventId, "UTF-8") + "&_remote=1&_unlock=" + session.unlock;
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            conn.getResponseCode();
        } catch(Exception e) {
            System.out.println("[DEBUG] Failed to delete Roundcube event: " + e.getMessage());
        }
    }
}
