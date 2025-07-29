package nl.vink.func_omschr.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SharePointService {
    
    @Value("${sharepoint.site-url:}")
    private String siteUrl;
    
    @Value("${sharepoint.document-library:Documenten}")
    private String libraryName;
    
    @Value("${azure.activedirectory.client-id:}")
    private String clientId;
    
    @Value("${azure.activedirectory.client-secret:}")
    private String clientSecret;
    
    @Value("${azure.activedirectory.tenant-id:}")
    private String tenantId;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private String accessToken;
    private LocalDateTime tokenExpiry;
    private String siteId; // Cache site ID
    private String driveId; // Cache drive ID
    
    /**
     * Upload een document naar SharePoint
     */
    public String uploadDocument(byte[] documentBytes, String bestandsnaam, String projectNummer) throws Exception {
        
        System.out.println("=== SHAREPOINT UPLOAD START ===");
        System.out.println("Bestand: " + bestandsnaam + " (" + documentBytes.length + " bytes)");
        System.out.println("Project: " + projectNummer);
        
        // 1. Zorg voor geldige access token
        ensureValidAccessToken();
        
        // 2. Haal site ID op (cache dit)
        if (siteId == null) {
            siteId = getSiteId();
            System.out.println("Site ID opgehaald: " + siteId);
        }
        
        // 3. Haal drive ID op (cache dit)
        if (driveId == null) {
            driveId = getDriveId();
            System.out.println("Drive ID opgehaald: " + driveId);
        }
        
        // 4. Maak folder structuur (als niet bestaat)
        // Upload naar: Documenten/Functionele Omschrijvingen/[ProjectNummer]/
        String folderPath = "Functionele Omschrijvingen/" + projectNummer.replaceAll("[^a-zA-Z0-9-_]", "");
        ensureFolderExists(folderPath);
        
        // 5. Upload document
        String sharePointUrl = uploadToSharePoint(documentBytes, folderPath, bestandsnaam);
        
        System.out.println("Upload succesvol: " + sharePointUrl);
        System.out.println("=== SHAREPOINT UPLOAD COMPLETE ===");
        
        return sharePointUrl;
    }
    
    /**
     * Zorgt voor een geldige access token
     */
    private void ensureValidAccessToken() throws Exception {
        if (accessToken == null || tokenExpiry == null || LocalDateTime.now().isAfter(tokenExpiry)) {
            refreshAccessToken();
        }
    }
    
    /**
     * Haalt nieuwe access token op via Azure AD
     */
    @SuppressWarnings("UseSpecificCatch")
    private void refreshAccessToken() throws Exception {
        System.out.println("Access token ophalen...");
        
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        String requestBody = "grant_type=client_credentials" +
                           "&client_id=" + clientId +
                           "&client_secret=" + clientSecret +
                           "&scope=https://graph.microsoft.com/.default";
        
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                tokenUrl, HttpMethod.POST, request, String.class);
            
            JsonNode tokenResponse = objectMapper.readTree(response.getBody());
            
            this.accessToken = tokenResponse.get("access_token").asText();
            
            // Token verloopt meestal na 1 uur, we nemen wat marge
            int expiresInSeconds = tokenResponse.get("expires_in").asInt();
            this.tokenExpiry = LocalDateTime.now().plusSeconds(expiresInSeconds - 300); // 5 min marge
            
            System.out.println("Access token vernieuwd, verloopt op: " + tokenExpiry);
            
        } catch (Exception e) {
            throw new Exception("Fout bij ophalen access token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Haalt site ID op via hostname en site path
     */
    @SuppressWarnings("UseSpecificCatch")
    private String getSiteId() throws Exception {
        System.out.println("Site ID ophalen voor: " + siteUrl);
        
        // Parse site URL: https://vinkinstallatiegroep.sharepoint.com/sites/FunctioneleOmschrijvingen
        String hostname = extractHostname(siteUrl);
        String sitePath = extractSitePath(siteUrl);
        
        System.out.println("Hostname: " + hostname);
        System.out.println("Site path: " + sitePath);
        
        // Microsoft Graph API voor site lookup
        String apiUrl = "https://graph.microsoft.com/v1.0/sites/" + hostname + ":/" + sitePath;
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.GET, request, String.class);
            
            JsonNode siteResponse = objectMapper.readTree(response.getBody());
            String id = siteResponse.get("id").asText();
            
            System.out.println("Site ID gevonden: " + id);
            return id;
            
        } catch (Exception e) {
            throw new Exception("Fout bij ophalen site ID: " + e.getMessage() + 
                              "\nAPI URL: " + apiUrl, e);
        }
    }
    
    /**
     * Haalt drive ID op voor document library
     */
    private String getDriveId() throws Exception {
        System.out.println("Drive ID ophalen voor library: " + libraryName);
        
        String apiUrl = "https://graph.microsoft.com/v1.0/sites/" + siteId + "/drives";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.GET, request, String.class);
            
            JsonNode drivesResponse = objectMapper.readTree(response.getBody());
            JsonNode drives = drivesResponse.get("value");
            
            // Zoek drive met juiste naam
            for (JsonNode drive : drives) {
                String driveName = drive.get("name").asText();
                System.out.println("Gevonden drive: " + driveName);
                
                if (driveName.equals(libraryName) || 
                    driveName.equals("Documents") || 
                    driveName.contains("Document")) {
                    
                    String id = drive.get("id").asText();
                    System.out.println("Drive ID gevonden: " + id);
                    return id;
                }
            }
            
            // Als geen match, gebruik eerste drive
            if (drives.size() > 0) {
                String id = drives.get(0).get("id").asText();
                System.out.println("Geen exacte match, gebruik eerste drive: " + id);
                return id;
            }
            
            throw new Exception("Geen drives gevonden in site");
            
        } catch (Exception e) {
            throw new Exception("Fout bij ophalen drive ID: " + e.getMessage(), e);
        }
    }
    
    /**
     * Zorgt ervoor dat folder bestaat
     */
    private void ensureFolderExists(String folderPath) throws Exception {
        System.out.println("Folder controleren: " + folderPath);
        
        String[] pathParts = folderPath.split("/");
        String currentPath = "";
        
        for (String part : pathParts) {
            currentPath += (currentPath.isEmpty() ? "" : "/") + part;
            createFolderIfNotExists(currentPath);
        }
    }
    
    /**
     * Maakt folder aan als deze niet bestaat
     */
    @SuppressWarnings("UseSpecificCatch")
    private void createFolderIfNotExists(String folderPath) throws Exception {
        String checkUrl = "https://graph.microsoft.com/v1.0/drives/" + driveId + "/root:/" + folderPath;
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            // Probeer folder op te halen
            restTemplate.exchange(checkUrl, HttpMethod.GET, request, String.class);
            System.out.println("Folder bestaat al: " + folderPath);
            
        } catch (Exception e) {
            // Folder bestaat niet, maak aan
            System.out.println("Folder aanmaken: " + folderPath);
            
            String createUrl = "https://graph.microsoft.com/v1.0/drives/" + driveId + "/root/children";
            
            HttpHeaders createHeaders = new HttpHeaders();
            createHeaders.set("Authorization", "Bearer " + accessToken);
            createHeaders.setContentType(MediaType.APPLICATION_JSON);
            
            String requestBody = "{ \"name\": \"" + folderPath.substring(folderPath.lastIndexOf("/") + 1) + 
                               "\", \"folder\": {}, \"@microsoft.graph.conflictBehavior\": \"rename\" }";
            
            HttpEntity<String> createRequest = new HttpEntity<>(requestBody, createHeaders);
            
            try {
                restTemplate.exchange(createUrl, HttpMethod.POST, createRequest, String.class);
                System.out.println("Folder aangemaakt: " + folderPath);
            } catch (Exception createEx) {
                System.out.println("Folder aanmaken gefaald (mogelijk al bestaand): " + createEx.getMessage());
            }
        }
    }
    
    /**
     * Upload document naar SharePoint
     */
    private String uploadToSharePoint(byte[] documentBytes, String folderPath, String bestandsnaam) throws Exception {
        
        String uploadUrl = "https://graph.microsoft.com/v1.0/drives/" + driveId + 
                          "/root:/" + folderPath + "/" + bestandsnaam + ":/content";
        
        System.out.println("Upload URL: " + uploadUrl);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        
        HttpEntity<byte[]> request = new HttpEntity<>(documentBytes, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                uploadUrl, HttpMethod.PUT, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode uploadResponse = objectMapper.readTree(response.getBody());
                
                // SharePoint web URL voor directe toegang
                String webUrl = uploadResponse.get("webUrl").asText();
                return webUrl;
                
            } else {
                throw new Exception("Upload gefaald met status: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            throw new Exception("Fout bij uploaden naar SharePoint: " + e.getMessage() + 
                              "\nUpload URL: " + uploadUrl, e);
        }
    }
    
    /**
     * Extraheert hostname uit SharePoint URL
     */
    private String extractHostname(String url) {
        // Van: https://vinkinstallatiegroep.sharepoint.com/sites/FunctioneleOmschrijvingen
        // Naar: vinkinstallatiegroep.sharepoint.com
        return url.replace("https://", "").replace("http://", "").split("/")[0];
    }
    
    /**
     * Extraheert site path uit SharePoint URL
     */
    private String extractSitePath(String url) {
        // Van: https://vinkinstallatiegroep.sharepoint.com/sites/FunctioneleOmschrijvingen
        // Naar: sites/FunctioneleOmschrijvingen
        String cleanUrl = url.replace("https://", "").replace("http://", "");
        String[] parts = cleanUrl.split("/");
        if (parts.length > 1) {
            return String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        }
        return "";
    }
    
    /**
     * Test methode om connectie te testen
     */
    public boolean testConnection() {
        try {
            ensureValidAccessToken();
            
            // Reset cached IDs voor fresh test
            siteId = null;
            driveId = null;
            
            getSiteId();
            getDriveId();
            
            return true;
        } catch (Exception e) {
            System.err.println("SharePoint connectie test gefaald: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Haalt site informatie op (voor debugging)
     */
    public String getSiteInfo() throws Exception {
        ensureValidAccessToken();
        
        if (siteId == null) {
            siteId = getSiteId();
        }
        
        String siteApiUrl = "https://graph.microsoft.com/v1.0/sites/" + siteId;
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            siteApiUrl, HttpMethod.GET, request, String.class);
        
        return response.getBody();
    }

    /**
     * Debug methode voor configuratie controle
     */
    public String getConfigurationDebug() {
        StringBuilder debug = new StringBuilder();
        
        debug.append("=== SHAREPOINT CONFIGURATIE DEBUG ===\n\n");
        debug.append("Site URL: ").append(siteUrl != null ? siteUrl : "❌ NIET INGESTELD").append("\n");
        debug.append("Library: ").append(libraryName != null ? libraryName : "❌ NIET INGESTELD").append("\n");
        debug.append("Client ID: ").append(clientId != null && !clientId.isEmpty() ? "✅ Ingesteld (" + clientId.substring(0, 8) + "...)" : "❌ Leeg").append("\n");
        debug.append("Tenant ID: ").append(tenantId != null && !tenantId.isEmpty() ? "✅ Ingesteld (" + tenantId.substring(0, 8) + "...)" : "❌ Leeg").append("\n");
        debug.append("Client Secret: ").append(clientSecret != null && !clientSecret.isEmpty() ? "✅ Ingesteld (***verborgen***)" : "❌ Leeg").append("\n");
        
        debug.append("\n=== URL PARSING TEST ===\n");
        if (siteUrl != null && !siteUrl.isEmpty()) {
            String hostname = extractHostname(siteUrl);
            String sitePath = extractSitePath(siteUrl);
            
            debug.append("Hostname: ").append(hostname).append("\n");
            debug.append("Site Path: ").append(sitePath).append("\n");
            debug.append("Graph API URL: https://graph.microsoft.com/v1.0/sites/").append(hostname).append(":/").append(sitePath).append("\n");
        } else {
            debug.append("❌ Site URL niet ingesteld - kan niet parsen\n");
        }
        
        debug.append("\n=== VERWACHTE CONFIGURATIE ===\n");
        debug.append("Verwachte Site URL: https://vinkinstallatiegroep.sharepoint.com/sites/FunctioneleOmschrijvingen\n");
        debug.append("Verwachte Library: Documenten\n");
        debug.append("Upload pad: Documenten/Functionele Omschrijvingen/[ProjectNummer]/\n");
        
        return debug.toString();
    }
}