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
     * Download document
     */
    @GetMapping("/{id}/document-download")
    public ResponseEntity<?> downloadDocument(@PathVariable Long id) {
        
        try {
            Project project = projectService.vindProjectById(id);
            
            if (!project.isDocumentGegenereerd() || project.getDocumentSharepointUrl() == null) {
                return createErrorRedirect(id, "Er is nog geen document gegenereerd voor dit project.");
            }
            
            String documentUrl = project.getDocumentSharepointUrl();
            
            if (!sharePointService.isDocumentAvailable(documentUrl)) {
                return createWarningRedirect(id, 
                    "Het document wordt nog verwerkt door SharePoint. " +
                    "Dit kan enkele minuten duren na het genereren. Probeer het opnieuw.");
            }
            
            byte[] documentBytes = sharePointService.downloadDocument(documentUrl);
            
            if (documentBytes == null || documentBytes.length == 0) {
                return createErrorRedirect(id, "Het gedownloade document is leeg. Probeer het document opnieuw te genereren.");
            }
            
            String filename = project.getDocumentBestandsnaam();
            if (filename == null || filename.isEmpty()) {
                filename = "Functionele_Omschrijving_" + 
                        project.getProjectNummer().replaceAll("[^a-zA-Z0-9-_]", "") + 
                        ".docx";
            }
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(documentBytes.length))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(documentBytes);
            
        } catch (Exception e) {
            return createErrorRedirect(id, 
                "Fout bij downloaden document: " + e.getMessage());
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
}