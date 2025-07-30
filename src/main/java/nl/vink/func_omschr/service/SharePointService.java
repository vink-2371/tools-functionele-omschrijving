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
     * Download een document van SharePoint - MULTI-METHODE AANPAK
     */
    public byte[] downloadDocument(String documentUrl) throws Exception {
        // Zorg voor complete initialisatie
        ensureFullInitialization();
        
        // Probeer verschillende download methodes
        Exception lastException = null;
        
        // Methode 1: SharePoint REST API via GUID (BESTE OPTIE)
        try {
            byte[] result = downloadDocumentViaSharePointRest(documentUrl);
            if (result != null && result.length > 0) {
                return result;
            }
        } catch (Exception e) {
            lastException = e;
            System.err.println("SharePoint REST API methode gefaald: " + e.getMessage());
        }
        
        // Methode 2: Graph API via GUID
        try {
            String downloadUrl = convertLayoutsUrlToDownloadUrlViaGuid(documentUrl);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                downloadUrl, HttpMethod.GET, request, byte[].class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            lastException = e;
            System.err.println("Graph API GUID methode gefaald: " + e.getMessage());
        }
        
        // Methode 3: Direct file access via bekende pad structuur
        try {
            String filename = extractFilenameFromUrl(documentUrl);
            if (filename != null) {
                byte[] result = downloadViaDirectPath(filename);
                if (result != null && result.length > 0) {
                    return result;
                }
            }
        } catch (Exception e) {
            lastException = e;
            System.err.println("Direct path methode gefaald: " + e.getMessage());
        }
        
        // Als alle methodes gefaald hebben
        throw new Exception("Alle download methodes gefaald. Laatste fout: " + 
                           (lastException != null ? lastException.getMessage() : "Onbekend"));
    }
    
    /**
     * METHODE 1: Download via SharePoint REST API (vaak betrouwbaarder dan Graph API)
     */
    public byte[] downloadDocumentViaSharePointRest(String documentUrl) throws Exception {
        
        ensureValidAccessToken();
        
        // Extract GUID
        String guid = extractGuidFromUrl(documentUrl);
        if (guid == null) {
            throw new Exception("Kan GUID niet extraheren uit URL: " + documentUrl);
        }
        
        // SharePoint REST API endpoint
        String restUrl = siteUrl + "/_api/web/getfilebyid('" + guid + "')/$value";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/octet-stream");
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                restUrl, HttpMethod.GET, request, byte[].class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new Exception("SharePoint REST download gefaald: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            throw new Exception("SharePoint REST API fout: " + e.getMessage(), e);
        }
    }
    
    /**
     * METHODE 2: Graph API download via GUID
     */
    private String convertLayoutsUrlToDownloadUrlViaGuid(String layoutsUrl) throws Exception {
        
        // Extract GUID from sourcedoc parameter
        String guid = extractGuidFromUrl(layoutsUrl);
        
        if (guid == null) {
            throw new Exception("Kan GUID niet extraheren uit URL: " + layoutsUrl);
        }
        
        // Direct via drive items met GUID
        return "https://graph.microsoft.com/v1.0/drives/" + driveId + "/items/" + guid + "/content";
    }
    
    /**
     * METHODE 3: Download via direct pad op basis van bestandsnaam
     */
    @SuppressWarnings("UseSpecificCatch")
    private byte[] downloadViaDirectPath(String filename) throws Exception {
        
        // Probeer bekende paden waar het bestand zou kunnen staan
        String[] possiblePaths = {
            "/drives/" + driveId + "/root:/Functionele Omschrijvingen/" + filename + ":/content",
            "/drives/" + driveId + "/root:/" + filename + ":/content",
            "/sites/" + siteId + "/drive/root:/Functionele Omschrijvingen/" + filename + ":/content"
        };
        
        for (String path : possiblePaths) {
            try {
                String fullUrl = "https://graph.microsoft.com/v1.0" + path;
                
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + accessToken);
                HttpEntity<String> request = new HttpEntity<>(headers);
                
                ResponseEntity<byte[]> response = restTemplate.exchange(
                    fullUrl, HttpMethod.GET, request, byte[].class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    return response.getBody();
                }
                
            } catch (Exception e) {
                // Probeer volgende pad
            }
        }
        
        throw new Exception("Geen direct pad werkte voor bestand: " + filename);
    }
    
    /**
     * Extraheer GUID uit SharePoint URL
     */
    @SuppressWarnings("UseSpecificCatch")
    private String extractGuidFromUrl(String url) {
        try {
            // Zoek naar sourcedoc parameter
            if (url.contains("sourcedoc=")) {
                String[] parts = url.split("sourcedoc=");
                if (parts.length > 1) {
                    String guidPart = parts[1].split("&")[0]; // Tot volgende parameter
                    
                    // URL decode
                    guidPart = java.net.URLDecoder.decode(guidPart, "UTF-8");
                    
                    // Remove {} brackets als aanwezig
                    guidPart = guidPart.replace("{", "").replace("}", "");
                    
                    return guidPart;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Extraheer bestandsnaam uit verschillende URL formaten
     */
    @SuppressWarnings("UseSpecificCatch")
    private String extractFilenameFromUrl(String url) {
        // Uit _layouts URL
        if (url.contains("&file=")) {
            String[] parts = url.split("&file=");
            if (parts.length > 1) {
                try {
                    return java.net.URLDecoder.decode(parts[1].split("&")[0], "UTF-8");
                } catch (Exception e) {
                    return null;
                }
            }
        }
        
        // Uit directe URL
        if (url.contains("/")) {
            String[] parts = url.split("/");
            return parts[parts.length - 1];
        }
        
        return null;
    }
    
    /**
     * Controleert of een document beschikbaar is op SharePoint
     */
    public boolean isDocumentAvailable(String documentUrl) {
        try {
            // Probeer de REST API methode voor availability check
            ensureValidAccessToken();
            
            String guid = extractGuidFromUrl(documentUrl);
            if (guid == null) {
                return false;
            }
            
            // Test SharePoint REST API metadata endpoint
            String restUrl = siteUrl + "/_api/web/getfilebyid('" + guid + "')";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Accept", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                restUrl, HttpMethod.GET, request, String.class);
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            System.err.println("Document availability check failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Download een tekstblok (specifiek voor .docx bestanden uit tekstblok folder)
     */
    public byte[] downloadTextblok(String filename) throws Exception {
        ensureFullInitialization();
        
        // Tekstblokken staan in: Documenten/Tekstblokken/filename
        String downloadUrl = "https://graph.microsoft.com/v1.0/drives/" + driveId + 
                           "/root:/Tekstblokken/" + filename + ":/content";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                downloadUrl, HttpMethod.GET, request, byte[].class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new Exception("Tekstblok niet gevonden: " + filename);
            }
        } catch (Exception e) {
            throw new Exception("Fout bij ophalen tekstblok '" + filename + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Zorgt voor complete initialisatie van alle vereiste variabelen
     */
    private void ensureFullInitialization() throws Exception {
        // Zorg voor geldige access token
        ensureValidAccessToken();
        
        // Zorg voor site ID
        if (siteId == null || siteId.trim().isEmpty()) {
            siteId = getSiteId();
        }
        
        // Zorg voor drive ID
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
                // Folder mogelijk al aangemaakt door concurrent proces, negeer fout
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
    
    // ====== PUBLIC METHODES VOOR DEBUGGING ======
    
    /**
     * Public methode voor debugging - Token check
     */
    public void ensureValidAccessTokenPublic() throws Exception {
        ensureValidAccessToken();
    }
    
    /**
     * Public methode voor debugging - URL conversie
     */
    public String convertWebUrlToDownloadUrlPublic(String webUrl) throws Exception {
        return convertLayoutsUrlToDownloadUrlViaGuid(webUrl);
    }
    
    /**
     * Debug methode om initialisatie status te checken
     */
    public String getInitializationStatus() {
        StringBuilder status = new StringBuilder();
        status.append("=== SHAREPOINT SERVICE STATUS ===\n");
        status.append("Access Token: ").append(accessToken != null && !accessToken.isEmpty() ? "Present" : "Missing").append("\n");
        status.append("Token Expiry: ").append(tokenExpiry != null ? tokenExpiry.toString() : "Not set").append("\n");
        status.append("Site ID: ").append(siteId != null ? siteId : "null").append("\n");
        status.append("Drive ID: ").append(driveId != null ? driveId : "null").append("\n");
        status.append("Site URL Config: ").append(siteUrl != null ? siteUrl : "null").append("\n");
        status.append("Library Name: ").append(libraryName != null ? libraryName : "null").append("\n");
        return status.toString();
    }
}