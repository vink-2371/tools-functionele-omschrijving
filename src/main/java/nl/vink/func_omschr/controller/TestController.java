package nl.vink.func_omschr.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nl.vink.func_omschr.service.SharePointService;

@RestController
@RequestMapping("/api/test")
public class TestController {
    
    @Autowired
    private SharePointService sharePointService;
    
    /**
     * Test SharePoint connectie
     */
    @GetMapping("/sharepoint-connection")
    @SuppressWarnings("CallToPrintStackTrace")
    public ResponseEntity<String> testSharePointConnection() {
        try {
            System.out.println("=== SHAREPOINT CONNECTION TEST START ===");
            
            // Test 1: Basis connectie test
            boolean connectionOk = sharePointService.testConnection();
            System.out.println("Connection test result: " + connectionOk);
            
            if (!connectionOk) {
                return ResponseEntity.ok("❌ SharePoint connectie gefaald. Check logs voor details.");
            }
            
            // Test 2: Site info ophalen
            String siteInfo = sharePointService.getSiteInfo();
            System.out.println("Site info opgehaald: " + siteInfo.substring(0, Math.min(200, siteInfo.length())));
            
            return ResponseEntity.ok("✅ SharePoint connectie OK!\n\nSite info: " + 
                                   siteInfo.substring(0, Math.min(500, siteInfo.length())) + "...");
            
        } catch (Exception e) {
            System.err.println("SharePoint test error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok("❌ Error: " + e.getMessage());
        }
    }
    
    /**
     * Test document upload met dummy bestand
     */
    @GetMapping("/test-upload")
    @SuppressWarnings("CallToPrintStackTrace")
    public ResponseEntity<String> testDocumentUpload() {
        try {
            System.out.println("=== TEST UPLOAD START ===");
            
            // Maak dummy Word document
            String testContent = "Dit is een test document voor SharePoint upload.";
            byte[] testBytes = testContent.getBytes();
            
            String bestandsnaam = "test_upload_" + System.currentTimeMillis() + ".txt";
            String projectNummer = "TEST001";
            
            // Upload via SharePointService
            String sharePointUrl = sharePointService.uploadDocument(testBytes, bestandsnaam, projectNummer);
            
            System.out.println("Test upload succesvol: " + sharePointUrl);
            
            return ResponseEntity.ok("✅ Test upload succesvol!\n\nBestand: " + bestandsnaam + 
                                   "\nSharePoint URL: " + sharePointUrl);
            
        } catch (Exception e) {
            System.err.println("Test upload error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok("❌ Upload test gefaald: " + e.getMessage());
        }
    }
    
    /**
     * Debug configuratie
     */
    @GetMapping("/debug-config")
    public ResponseEntity<String> debugConfig() {
        try {
            // Haal debug info op via SharePointService
            String configDebug = sharePointService.getConfigurationDebug();
            
            return ResponseEntity.ok(configDebug);
            
        } catch (Exception e) {
            return ResponseEntity.ok("❌ Fout bij ophalen configuratie debug: " + e.getMessage());
        }
    }
    
    /**
     * Toon configuratie (zonder gevoelige data)
     */
    @GetMapping("/config")
    public ResponseEntity<String> showConfig() {
        try {
            return ResponseEntity.ok("""
                                     Configuration check - zie console logs voor details.
                                     
                                     Gebruik /api/test/debug-config voor gedetailleerde configuratie info.""");
        } catch (Exception e) {
            return ResponseEntity.ok("❌ Config error: " + e.getMessage());
        }
    }
}