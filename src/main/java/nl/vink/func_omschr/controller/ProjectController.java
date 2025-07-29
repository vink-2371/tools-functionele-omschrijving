package nl.vink.func_omschr.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import nl.vink.func_omschr.model.Project;
import nl.vink.func_omschr.service.DocumentService;
import nl.vink.func_omschr.service.ProjectService;
import nl.vink.func_omschr.service.SharePointService;

@Controller
@RequestMapping("/projecten")
public class ProjectController {
    
    private final ProjectService projectService;
    private final DocumentService documentService;
    private final SharePointService sharePointService;

    public ProjectController(ProjectService projectService, DocumentService documentService, SharePointService sharePointService) {
        this.projectService = projectService;
        this.documentService = documentService;
        this.sharePointService = sharePointService;
    }
    
    /**
     * Overzichtspagina met alle projecten
     */
    @GetMapping
    public String projectOverzicht(Model model) {
        try {
            List<Project> projecten = projectService.getAlleProjecten();
            
            long projectenMetDocument = projecten.stream()
                    .filter(Project::isDocumentGegenereerd)
                    .count();
            
            long projectenZonderDocument = projecten.stream()
                    .filter(p -> !p.isDocumentGegenereerd())
                    .count();
            
            model.addAttribute("projecten", projecten);
            model.addAttribute("projectenMetDocument", projectenMetDocument);
            model.addAttribute("projectenZonderDocument", projectenZonderDocument);
            
            return "projecten/overzicht";
            
        } catch (Exception e) {
            model.addAttribute("projecten", new ArrayList<>());
            model.addAttribute("projectenMetDocument", 0L);
            model.addAttribute("projectenZonderDocument", 0L);
            return "projecten/overzicht";
        }
    }
    
    /**
     * Toon formulier voor nieuw project
     */
    @GetMapping("/nieuw")
    public String nieuwProjectFormulier(Model model) {
        model.addAttribute("project", new Project());
        return "projecten/nieuw";
    }
    
    /**
     * Verwerk nieuw project formulier
     */
    @PostMapping("/nieuw")
    public String nieuwProjectOpslaan(@Valid @ModelAttribute("project") Project project, 
                                     BindingResult bindingResult,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes,
                                     Model model) {
        
        if (projectService.projectNummerBestaat(project.getProjectNummer())) {
            bindingResult.rejectValue("projectNummer", "error.project", 
                "Dit projectnummer bestaat al. Kies een ander nummer.");
        }
        
        if (bindingResult.hasErrors()) {
            return "projecten/nieuw";
        }
        
        if (principal != null) {
            project.setGebruikerEmail(principal.getName());
        }
        
        Project opgeslagenProject = projectService.opslaanProject(project);
        
        redirectAttributes.addFlashAttribute("successMessage", 
            "Project '" + opgeslagenProject.getProjectNaam() + "' is succesvol aangemaakt!");
        
        return "redirect:/projecten/" + opgeslagenProject.getId() + "/configuratie";
    }
    
    /**
     * Toon project details met URL parameter handling
     */
    @GetMapping("/{id}")
    public String projectDetails(@PathVariable Long id, 
                            @RequestParam(required = false) String error,
                            @RequestParam(required = false) String warning,
                            Model model) {
        Project project = projectService.vindProjectById(id);
        model.addAttribute("project", project);
        
        // Voeg error/warning messages toe van URL parameters
        if (error != null && !error.trim().isEmpty()) {
            model.addAttribute("errorMessage", error);
        }
        
        if (warning != null && !warning.trim().isEmpty()) {
            model.addAttribute("warningMessage", warning);
        }
        
        return "projecten/details";
    }
    
    /**
     * Configuratie stap
     */
    @GetMapping("/{id}/configuratie")
    public String projectConfiguratie(@PathVariable Long id, Model model) {
        Project project = projectService.vindProjectById(id);
        model.addAttribute("project", project);
        return "projecten/configuratie";
    }
    
    /**
     * Opslaan configuratie
     */
    @PostMapping("/{id}/configuratie")
    public String opslaanConfiguratie(@PathVariable Long id, 
                                    @RequestParam String configuratieJson,
                                    RedirectAttributes redirectAttributes) {
        try {
            Project project = projectService.vindProjectById(id);
            projectService.opslaanConfiguratie(id, configuratieJson);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Project '" + project.getProjectNaam() + "' is succesvol geconfigureerd! " +
                "Je kunt nu het document genereren via de project details.");
            
            return "redirect:/projecten";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Fout bij opslaan configuratie: " + e.getMessage());
            
            return "redirect:/projecten/" + id + "/configuratie";
        }
    }
    
    /**
     * Bewerk project
     */
    @GetMapping("/{id}/bewerken")
    public String bewerkProject(@PathVariable Long id, Model model) {
        Project project = projectService.vindProjectById(id);
        model.addAttribute("project", project);
        return "projecten/bewerken";
    }
    
    @PostMapping("/{id}/bewerken")
    public String bewerkProjectOpslaan(@PathVariable Long id,
                                      @Valid @ModelAttribute("project") Project project,
                                      BindingResult bindingResult,
                                      RedirectAttributes redirectAttributes) {
        
        if (bindingResult.hasErrors()) {
            return "projecten/bewerken";
        }
        
        if (projectService.projectNummerBestaatVoorAnderProject(project.getProjectNummer(), id)) {
            bindingResult.rejectValue("projectNummer", "error.project", 
                "Dit projectnummer wordt al gebruikt door een ander project.");
            return "projecten/bewerken";
        }
        
        project.setId(id);
        projectService.bijwerkenProject(project);
        
        redirectAttributes.addFlashAttribute("successMessage", 
            "Project is succesvol bijgewerkt!");
        
        return "redirect:/projecten/" + id;
    }
    
    /**
     * Verwijder project
     */
    @PostMapping("/{id}/verwijderen")
    public String verwijderProject(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Project project = projectService.vindProjectById(id);
        projectService.verwijderProject(id);
        
        redirectAttributes.addFlashAttribute("successMessage", 
            "Project '" + project.getProjectNaam() + "' is verwijderd.");
        
        return "redirect:/projecten";
    }
    
    /**
     * Genereer document
     */
    @PostMapping("/{id}/genereer-document")
    public String genereerDocument(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        
        try {
            String bestandsnaam = documentService.genereerDocument(id);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Document '" + bestandsnaam + "' is succesvol gegenereerd en geüpload naar SharePoint!");
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Fout bij genereren document: " + e.getMessage());
        }
        
        return "redirect:/projecten/" + id;
    }
    
    /**
     * Download document met uitgebreide feedback
     */
    @GetMapping("/{id}/document-download")
    public ResponseEntity<?> downloadDocument(@PathVariable Long id) {
        
        try {
            // Stap 1: Project ophalen
            Project project = projectService.vindProjectById(id);
            
            // Stap 2: Valideren of document bestaat
            if (!project.isDocumentGegenereerd() || project.getDocumentSharepointUrl() == null) {
                return createErrorRedirect(id, "Er is nog geen document gegenereerd voor dit project.");
            }
            
            String documentUrl = project.getDocumentSharepointUrl();
            
            // Stap 3: Controleren of document beschikbaar is
            if (!sharePointService.isDocumentAvailable(documentUrl)) {
                return createWarningRedirect(id, 
                    "Het document wordt nog verwerkt door SharePoint. " +
                    "Dit kan enkele minuten duren na het genereren. Probeer het opnieuw.");
            }
            
            // Stap 4: Document downloaden van SharePoint
            byte[] documentBytes;
            try {
                documentBytes = sharePointService.downloadDocument(documentUrl);
            } catch (Exception downloadEx) {
                return createErrorRedirect(id, 
                    "Fout bij ophalen document van SharePoint: " + downloadEx.getMessage() + 
                    ". Het document is mogelijk nog niet volledig verwerkt.");
            }
            
            // Stap 5: Valideren download
            if (documentBytes == null || documentBytes.length == 0) {
                return createErrorRedirect(id, "Het gedownloade document is leeg. Probeer het document opnieuw te genereren.");
            }
            
            // Stap 6: Bestandsnaam bepalen
            String filename = project.getDocumentBestandsnaam();
            if (filename == null || filename.isEmpty()) {
                filename = "Functionele_Omschrijving_" + 
                        project.getProjectNummer().replaceAll("[^a-zA-Z0-9-_]", "") + 
                        ".docx";
            }
            
            // Stap 7: Document naar browser sturen
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(documentBytes.length))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(documentBytes);
            
        } catch (Exception e) {
            // Algemene fout afhandeling
            return createErrorRedirect(id, 
                "Onverwachte fout bij downloaden: " + e.getMessage() + 
                ". Neem contact op met de beheerder als dit probleem aanhoudt.");
        }
    }

    /**
     * Helper methode voor error redirects
     */
    private ResponseEntity<?> createErrorRedirect(Long projectId, String message) {
        return ResponseEntity.status(302)
                .header("Location", "/projecten/" + projectId + "?error=" + 
                    java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8))
                .build();
    }

    /**
     * Helper methode voor warning redirects  
     */
    private ResponseEntity<?> createWarningRedirect(Long projectId, String message) {
        return ResponseEntity.status(302)
                .header("Location", "/projecten/" + projectId + "?warning=" + 
                    java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8))
                .build();
    }



    // Tijdelijke debug endpoint - voeg toe aan ProjectController

    @GetMapping("/{id}/debug-download")
    public ResponseEntity<String> debugDownload(@PathVariable Long id) {
        StringBuilder debug = new StringBuilder();
        
        try {
            debug.append("=== DOWNLOAD DEBUG INFO V2 ===\n\n");
            
            // Stap 1: Project info
            Project project = projectService.vindProjectById(id);
            debug.append("1. PROJECT INFO:\n");
            debug.append("   - ID: ").append(project.getId()).append("\n");
            debug.append("   - Naam: ").append(project.getProjectNaam()).append("\n");
            debug.append("   - Document gegenereerd: ").append(project.isDocumentGegenereerd()).append("\n");
            debug.append("   - SharePoint URL: ").append(project.getDocumentSharepointUrl()).append("\n");
            debug.append("   - Bestandsnaam: ").append(project.getDocumentBestandsnaam()).append("\n\n");
            
            if (!project.isDocumentGegenereerd()) {
                debug.append("❌ STOP: Geen document gegenereerd\n");
                return ResponseEntity.ok(debug.toString());
            }
            
            if (project.getDocumentSharepointUrl() == null) {
                debug.append("❌ STOP: Geen SharePoint URL\n");
                return ResponseEntity.ok(debug.toString());
            }
            
            // Stap 2: URL Analysis
            debug.append("2. URL ANALYSE:\n");
            String originalUrl = project.getDocumentSharepointUrl();
            debug.append("   - Originele URL: ").append(originalUrl).append("\n");
            debug.append("   - URL Type: ");
            if (originalUrl.contains("_layouts/15/Doc.aspx")) {
                debug.append("Layouts URL (viewer link)\n");
            } else if (originalUrl.contains("/Gedeelde documenten/")) {
                debug.append("Directe document URL\n");
            } else {
                debug.append("Onbekend formaat\n");
            }
            
            // Extract filename
            String filename = project.getDocumentBestandsnaam();
            if (filename == null && originalUrl.contains("&file=")) {
                String[] parts = originalUrl.split("&file=");
                if (parts.length > 1) {
                    filename = parts[1].split("&")[0];
                    try {
                        filename = java.net.URLDecoder.decode(filename, "UTF-8");
                    } catch (Exception e) {}
                }
            }
            debug.append("   - Extracted filename: ").append(filename).append("\n\n");
            
            // Stap 3: Download URL Conversie
            debug.append("3. DOWNLOAD URL CONVERSIE:\n");
            try {
                // Test de nieuwe convertWebUrlToDownloadUrl methode
                String downloadUrl = sharePointService.convertWebUrlToDownloadUrl(originalUrl);
                debug.append("   - Geconverteerde URL: ").append(downloadUrl).append("\n");
            } catch (Exception e) {
                debug.append("   - Conversie gefaald: ").append(e.getMessage()).append("\n");
            }
            
            // Stap 4: Beschikbaarheid check
            debug.append("\n4. BESCHIKBAARHEID CHECK:\n");
            boolean beschikbaar = sharePointService.isDocumentAvailable(originalUrl);
            debug.append("   - Document beschikbaar: ").append(beschikbaar).append("\n\n");
            
            if (!beschikbaar) {
                debug.append("❌ STOP: Document niet beschikbaar\n");
                debug.append("   Dit kan betekenen:\n");
                debug.append("   - Document wordt nog verwerkt door SharePoint\n");
                debug.append("   - URL conversie werkt niet correct\n");
                debug.append("   - Bestand staat in andere folder dan verwacht\n");
                return ResponseEntity.ok(debug.toString());
            }
            
            // Stap 5: Download test
            debug.append("5. DOWNLOAD TEST:\n");
            byte[] documentBytes = sharePointService.downloadDocument(originalUrl);
            debug.append("   - Download grootte: ").append(documentBytes != null ? documentBytes.length : "null").append(" bytes\n");
            
            if (documentBytes == null || documentBytes.length == 0) {
                debug.append("❌ STOP: Download gefaald of leeg\n");
            } else {
                debug.append("✅ SUCCESS: Document kan worden gedownload!\n");
            }
            
        } catch (Exception e) {
            debug.append("❌ ALGEMENE FOUT: ").append(e.getMessage()).append("\n");
            debug.append("Stack trace eerste regel: ");
            if (e.getStackTrace().length > 0) {
                debug.append(e.getStackTrace()[0].toString());
            }
        }
        
        return ResponseEntity.ok(debug.toString());
    }

    // Maak deze methode public in SharePointService voor debugging
    public String convertWebUrlToDownloadUrl(String webUrl) throws Exception {
        // Deze methode is nu public voor debug doeleinden
        return convertWebUrlToDownloadUrl(webUrl);
    }
}