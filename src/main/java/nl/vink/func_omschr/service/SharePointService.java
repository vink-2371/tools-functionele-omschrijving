package nl.vink.func_omschr.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
    
    @Value("${sharepoint.document-library:VinkDocumenten}")
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
    
    /**
     * Upload een document naar SharePoint
     * 
     * @param documentBytes Het document als byte array
     * @param bestandsnaam De gewenste bestandsnaam
     * @param projectNummer Voor subfolder structuur
     * @return SharePoint URL van het geüploade bestand
     */
    public String uploadDocument(byte[] documentBytes, String bestandsnaam, String projectNummer) throws Exception {
        
        // 1. Zorg voor geldige access token
        ensureValidAccessToken();
        
        // 2. Bepaal upload pad (optioneel: subfolder per project)
        String uploadPath = bepaalUploadPad(projectNummer, bestandsnaam);
        
        // 3. Upload naar SharePoint via Graph API
        String sharePointUrl = uploadViaGraphAPI(documentBytes, uploadPath);
        
        System.out.println("Document succesvol geüpload naar: " + sharePointUrl);
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
    private void refreshAccessToken() throws Exception {
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
     * Bepaalt het upload pad binnen SharePoint
     */
    private String bepaalUploadPad(String projectNummer, String bestandsnaam) {
        // Upload naar: /Documenten/Functionele Omschrijvingen/[ProjectNummer]/bestandsnaam
        String safePath = projectNummer.replaceAll("[^a-zA-Z0-9-_]", "");
        return "Functionele Omschrijvingen/" + safePath + "/" + bestandsnaam;
    }
    
    /**
     * Upload document via Microsoft Graph API
     */
    private String uploadViaGraphAPI(byte[] documentBytes, String uploadPath) throws Exception {
        
        // Graph API endpoint voor file upload
        // We gebruiken site URL i.p.v. site ID voor eenvoudiger setup
        String uploadUrl = String.format(
            "https://graph.microsoft.com/v1.0/sites/%s/drives/root:/%s/%s:/content",
            convertSiteUrlToSiteId(siteUrl), libraryName, uploadPath
        );
        
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
            throw new Exception("Fout bij uploaden naar SharePoint: " + e.getMessage(), e);
        }
    }
    
    /**
     * Test methode om connectie te testen
     */
    public boolean testConnection() {
        try {
            ensureValidAccessToken();
            return true;
        } catch (Exception e) {
            System.err.println("SharePoint connectie test gefaald: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Converteert SharePoint site URL naar site ID voor Graph API
     * Bijv: https://vink.sharepoint.com/sites/projecten -> vink.sharepoint.com,sites,projecten
     */
    private String convertSiteUrlToSiteId(String siteUrl) {
        // Simpele conversie voor Graph API
        // Van: https://vink.sharepoint.com/sites/projecten
        // Naar: vink.sharepoint.com,sites,projecten
        
        if (siteUrl == null || siteUrl.isEmpty()) {
            throw new IllegalArgumentException("SharePoint site URL is niet geconfigureerd");
        }
        
        String cleanUrl = siteUrl.replace("https://", "").replace("http://", "");
        return cleanUrl.replace("/", ",");
    }
    
    /**
     * Haalt site informatie op (voor debugging)
     */
    public String getSiteInfo() throws Exception {
        ensureValidAccessToken();
        
        String siteApiUrl = "https://graph.microsoft.com/v1.0/sites/" + convertSiteUrlToSiteId(siteUrl);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            siteApiUrl, HttpMethod.GET, request, String.class);
        
        return response.getBody();
    }
}