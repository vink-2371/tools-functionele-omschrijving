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
    private String siteId;
    private String driveId;
    
    /**
     * Upload een document naar SharePoint
     */
    public String uploadDocument(byte[] documentBytes, String bestandsnaam, String projectNummer) throws Exception {
        
        ensureFullInitialization();
        
        String folderPath = "Functionele Omschrijvingen/" + projectNummer.replaceAll("[^a-zA-Z0-9-_]", "");
        ensureFolderExists(folderPath);
        
        return uploadToSharePoint(documentBytes, folderPath, bestandsnaam);
    }
    
    /**
     * Download een document van SharePoint
     */
    public byte[] downloadDocument(String documentUrl) throws Exception {
        ensureFullInitialization();
        
        // Extract GUID uit URL
        String guid = extractGuidFromUrl(documentUrl);
        if (guid == null) {
            throw new Exception("Kan GUID niet extraheren uit URL");
        }
        
        // Direct download via GUID met Graph API
        String downloadUrl = "https://graph.microsoft.com/v1.0/sites/" + siteId + "/drive/items/" + guid + "/content";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        ResponseEntity<byte[]> response = restTemplate.exchange(
            downloadUrl, HttpMethod.GET, request, byte[].class);
        
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody();
        } else {
            throw new Exception("Document download gefaald");
        }
    }
    
    /**
     * Controleert of een document beschikbaar is op SharePoint
     */
    public boolean isDocumentAvailable(String documentUrl) {
        try {
            ensureFullInitialization();
            
            String guid = extractGuidFromUrl(documentUrl);
            if (guid == null) {
                return false;
            }
            
            // Test of bestand bestaat via metadata
            String metadataUrl = "https://graph.microsoft.com/v1.0/sites/" + siteId + "/drive/items/" + guid;
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                metadataUrl, HttpMethod.GET, request, String.class);
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Download een tekstblok (voor toekomstige functionaliteit)
     */
    public byte[] downloadTextblok(String filename) throws Exception {
        ensureFullInitialization();
        
        String downloadUrl = "https://graph.microsoft.com/v1.0/drives/" + driveId + 
                           "/root:/Tekstblokken/" + filename + ":/content";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        ResponseEntity<byte[]> response = restTemplate.exchange(
            downloadUrl, HttpMethod.GET, request, byte[].class);
        
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody();
        } else {
            throw new Exception("Tekstblok niet gevonden: " + filename);
        }
    }
    
    /**
     * Zorgt voor complete initialisatie van alle vereiste variabelen
     */
    private void ensureFullInitialization() throws Exception {
        ensureValidAccessToken();
        
        if (siteId == null || siteId.trim().isEmpty()) {
            siteId = getSiteId();
        }
        
        if (driveId == null || driveId.trim().isEmpty()) {
            driveId = getDriveId();
        }
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
    private void refreshAccessToken() throws Exception {
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        String requestBody = "grant_type=client_credentials" +
                           "&client_id=" + clientId +
                           "&client_secret=" + clientSecret +
                           "&scope=https://graph.microsoft.com/.default";
        
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            tokenUrl, HttpMethod.POST, request, String.class);
        
        JsonNode tokenResponse = objectMapper.readTree(response.getBody());
        
        this.accessToken = tokenResponse.get("access_token").asText();
        
        int expiresInSeconds = tokenResponse.get("expires_in").asInt();
        this.tokenExpiry = LocalDateTime.now().plusSeconds(expiresInSeconds - 300);
    }
    
    /**
     * Haalt site ID op via hostname en site path
     */
    private String getSiteId() throws Exception {
        String hostname = extractHostname(siteUrl);
        String sitePath = extractSitePath(siteUrl);
        
        String apiUrl = "https://graph.microsoft.com/v1.0/sites/" + hostname + ":/" + sitePath;
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            apiUrl, HttpMethod.GET, request, String.class);
        
        JsonNode siteResponse = objectMapper.readTree(response.getBody());
        return siteResponse.get("id").asText();
    }
    
    /**
     * Haalt drive ID op voor document library
     */
    private String getDriveId() throws Exception {
        String apiUrl = "https://graph.microsoft.com/v1.0/sites/" + siteId + "/drives";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            apiUrl, HttpMethod.GET, request, String.class);
        
        JsonNode drivesResponse = objectMapper.readTree(response.getBody());
        JsonNode drives = drivesResponse.get("value");
        
        for (JsonNode drive : drives) {
            String driveName = drive.get("name").asText();
            
            if (driveName.equals(libraryName) || 
                driveName.equals("Documents") || 
                driveName.contains("Document")) {
                return drive.get("id").asText();
            }
        }
        
        if (drives.size() > 0) {
            return drives.get(0).get("id").asText();
        }
        
        throw new Exception("Geen document library gevonden");
    }
    
    /**
     * Zorgt ervoor dat folder bestaat
     */
    private void ensureFolderExists(String folderPath) throws Exception {
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
            restTemplate.exchange(checkUrl, HttpMethod.GET, request, String.class);
        } catch (Exception e) {
            // Folder bestaat niet, maak aan
            String createUrl = "https://graph.microsoft.com/v1.0/drives/" + driveId + "/root/children";
            
            HttpHeaders createHeaders = new HttpHeaders();
            createHeaders.set("Authorization", "Bearer " + accessToken);
            createHeaders.setContentType(MediaType.APPLICATION_JSON);
            
            String requestBody = "{ \"name\": \"" + folderPath.substring(folderPath.lastIndexOf("/") + 1) + 
                               "\", \"folder\": {}, \"@microsoft.graph.conflictBehavior\": \"rename\" }";
            
            HttpEntity<String> createRequest = new HttpEntity<>(requestBody, createHeaders);
            
            try {
                restTemplate.exchange(createUrl, HttpMethod.POST, createRequest, String.class);
            } catch (Exception createEx) {
                // Folder mogelijk al aangemaakt door concurrent proces
            }
        }
    }
    
    /**
     * Upload document naar SharePoint
     */
    private String uploadToSharePoint(byte[] documentBytes, String folderPath, String bestandsnaam) throws Exception {
        
        String uploadUrl = "https://graph.microsoft.com/v1.0/drives/" + driveId + 
                          "/root:/" + folderPath + "/" + bestandsnaam + ":/content";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        
        HttpEntity<byte[]> request = new HttpEntity<>(documentBytes, headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            uploadUrl, HttpMethod.PUT, request, String.class);
        
        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode uploadResponse = objectMapper.readTree(response.getBody());
            return uploadResponse.get("webUrl").asText();
        } else {
            throw new Exception("Upload gefaald");
        }
    }
    
    /**
     * Extraheer GUID uit SharePoint URL
     */
    @SuppressWarnings("UseSpecificCatch")
    private String extractGuidFromUrl(String url) {
        try {
            if (url.contains("sourcedoc=")) {
                String[] parts = url.split("sourcedoc=");
                if (parts.length > 1) {
                    String guidPart = parts[1].split("&")[0];
                    guidPart = java.net.URLDecoder.decode(guidPart, "UTF-8");
                    return guidPart.replace("{", "").replace("}", "");
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Extraheert hostname uit SharePoint URL
     */
    private String extractHostname(String url) {
        return url.replace("https://", "").replace("http://", "").split("/")[0];
    }
    
    /**
     * Extraheert site path uit SharePoint URL
     */
    private String extractSitePath(String url) {
        String cleanUrl = url.replace("https://", "").replace("http://", "");
        String[] parts = cleanUrl.split("/");
        if (parts.length > 1) {
            return String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        }
        return "";
    }
}