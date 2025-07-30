package nl.vink.func_omschr.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
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
        
        ensureValidAccessToken();
        
        if (siteId == null) {
            siteId = getSiteId();
        }
        
        if (driveId == null) {
            driveId = getDriveId();
        }
        
        String folderPath = "Functionele Omschrijvingen/" + projectNummer.replaceAll("[^a-zA-Z0-9-_]", "");
        ensureFolderExists(folderPath);
        
        return uploadToSharePoint(documentBytes, folderPath, bestandsnaam);
    }
    
    /**
     * Download een document van SharePoint - GEFIXTE VERSIE
     */
    public byte[] downloadDocument(String documentUrl) throws Exception {
        // Zorg voor complete initialisatie
        ensureFullInitialization();
        
        String downloadUrl = convertWebUrlToDownloadUrl(documentUrl);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                downloadUrl, HttpMethod.GET, request, byte[].class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new Exception("Download gefaald met status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new Exception("Fout bij downloaden van SharePoint: " + e.getMessage(), e);
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
     * Download een tekstblok (specifiek voor .docx bestanden uit tekstblok folder)
     */
    public byte[] downloadTextblok(String filename) throws Exception {
        ensureValidAccessToken();
        
        if (siteId == null) {
            siteId = getSiteId();
        }
        
        if (driveId == null) {
            driveId = getDriveId();
        }
        
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
     * Controleert of een document beschikbaar is op SharePoint - GEFIXTE VERSIE
     */
    public boolean isDocumentAvailable(String documentUrl) {
        try {
            // Zorg voor complete initialisatie
            ensureFullInitialization();
            
            String downloadUrl = convertWebUrlToDownloadUrl(documentUrl);
            
            return testDownloadUrl(downloadUrl);
            
        } catch (Exception e) {
            // Log de fout voor debugging maar return false
            System.err.println("Document availability check failed: " + e.getMessage());
            return false;
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
     * Converteert SharePoint web URL naar download URL - GEFIXTE VERSIE
     */
    private String convertWebUrlToDownloadUrl(String webUrl) throws Exception {
        try {
            // Zorg ervoor dat we geïnitialiseerd zijn
            ensureFullInitialization();
            
            // SharePoint geeft ons een _layouts URL met sourcedoc parameter
            if (webUrl.contains("_layouts/15/Doc.aspx") && webUrl.contains("sourcedoc=")) {
                return convertLayoutsUrlToDownloadUrl(webUrl);
            } else if (webUrl.contains("/Gedeelde documenten/") || webUrl.contains("/Shared Documents/")) {
                return convertDirectUrlToDownloadUrl(webUrl);
            } else {
                throw new Exception("Onbekend SharePoint URL formaat: " + webUrl);
            }
            
        } catch (Exception e) {
            throw new Exception("Fout bij converteren download URL: " + e.getMessage(), e);
        }
    }

    /**
     * Public methode voor debugging - TOEGEVOEGD
     */
    public String convertWebUrlToDownloadUrlPublic(String webUrl) throws Exception {
        return convertWebUrlToDownloadUrl(webUrl);
    }
    
    /**
     * Converteer _layouts URL naar Graph API download URL - GEFIXTE VERSIE
     */
    private String convertLayoutsUrlToDownloadUrl(String layoutsUrl) throws Exception {
        // Extract filename from URL
        String filename = null;
        if (layoutsUrl.contains("&file=")) {
            String[] parts = layoutsUrl.split("&file=");
            if (parts.length > 1) {
                filename = parts[1].split("&")[0];
                filename = java.net.URLDecoder.decode(filename, "UTF-8");
            }
        }
        
        if (filename == null) {
            throw new Exception("Kan bestandsnaam niet extraheren uit URL: " + layoutsUrl);
        }
        
        // Zoek het bestand in de Functionele Omschrijvingen folder
        return findFileInFunctioneleOmschrijvingen(filename);
    }
    
    /**
     * Zoek een bestand in de Functionele Omschrijvingen folder - GEFIXTE VERSIE
     */
    private String findFileInFunctioneleOmschrijvingen(String filename) throws Exception {
        
        // Probeer verschillende locaties in volgorde van waarschijnlijkheid
        String[] possiblePaths = {
            // Directe pad
            "Functionele Omschrijvingen/" + filename,
            
            // In project subfolders - probeer veel voorkomende patronen
            "Functionele Omschrijvingen/999KT5454/" + filename,
            "Functionele Omschrijvingen/TEST001/" + filename,
            
            // Root van Documenten
            filename
        };
        
        for (String path : possiblePaths) {
            String downloadUrl = "https://graph.microsoft.com/v1.0/drives/" + driveId + 
                            "/root:/" + path + ":/content";
            
            if (testDownloadUrl(downloadUrl)) {
                return downloadUrl;
            }
        }
        
        // Als geen directe paden werken, zoek dynamisch in alle subfolders
        return searchInAllSubfolders(filename);
    }

    /**
     * Zoek dynamisch in alle subfolders van Functionele Omschrijvingen
     */
    private String searchInAllSubfolders(String filename) throws Exception {
        
        String searchUrl = "https://graph.microsoft.com/v1.0/drives/" + driveId + 
                        "/root:/Functionele Omschrijvingen:/children";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                searchUrl, HttpMethod.GET, request, String.class);
            
            JsonNode folderContents = objectMapper.readTree(response.getBody());
            JsonNode items = folderContents.get("value");
            
            // Zoek in alle subfolders
            for (JsonNode item : items) {
                if (item.has("folder")) {
                    String folderName = item.get("name").asText();
                    String subfolderPath = "https://graph.microsoft.com/v1.0/drives/" + driveId + 
                                        "/root:/Functionele Omschrijvingen/" + folderName + "/" + filename + ":/content";
                    
                    if (testDownloadUrl(subfolderPath)) {
                        return subfolderPath;
                    }
                }
            }
            
            throw new Exception("Bestand '" + filename + "' niet gevonden in Functionele Omschrijvingen folder of subfolders");
            
        } catch (RestClientException restEx) {
            throw new Exception("Fout bij zoeken naar bestand - mogelijk authenticatie probleem: " + restEx.getMessage(), restEx);
        } catch (Exception e) {
            throw new Exception("Fout bij zoeken naar bestand: " + e.getMessage(), e);
        }
    }
    
    /**
     * Test of een download URL werkt - GEFIXTE VERSIE met betere error handling
     */
    @SuppressWarnings("UseSpecificCatch")
    private boolean testDownloadUrl(String downloadUrl) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                downloadUrl, HttpMethod.HEAD, request, String.class);
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            // Log voor debugging maar return false
            System.err.println("Test download URL failed for: " + downloadUrl + " - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Converteer directe SharePoint URL naar download URL (bestaande methode)
     */
    private String convertDirectUrlToDownloadUrl(String webUrl) throws Exception {
        String decodedUrl = java.net.URLDecoder.decode(webUrl, "UTF-8");
        
        String[] urlParts = decodedUrl.split("/Gedeelde documenten/");
        if (urlParts.length != 2) {
            urlParts = decodedUrl.split("/Shared Documents/");
            if (urlParts.length != 2) {
                throw new Exception("Kan bestandspad niet extraheren uit URL");
            }
        }
        
        String filePath = urlParts[1];
        
        if (driveId == null) {
            driveId = getDriveId();
        }
        
        return "https://graph.microsoft.com/v1.0/drives/" + driveId + 
               "/root:/" + filePath + ":/content";
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

    public void ensureValidAccessTokenPublic() throws Exception {
        ensureValidAccessToken();
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