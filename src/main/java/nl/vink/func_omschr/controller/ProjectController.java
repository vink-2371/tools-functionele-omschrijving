package nl.vink.func_omschr.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import nl.vink.func_omschr.model.Project;
import nl.vink.func_omschr.service.DocumentService;
import nl.vink.func_omschr.service.ProjectService;

@Controller
@RequestMapping("/projecten")
public class ProjectController {
    
    private final ProjectService projectService;
    
    private final DocumentService documentService;

    public ProjectController(ProjectService projectService, DocumentService documentService) {
        this.projectService = projectService;
        this.documentService = documentService;
    }
    
    // Overzichtspagina met alle projecten
    @GetMapping
    public String projectOverzicht(Model model) {
        try {
            List<Project> projecten = projectService.getAlleProjecten();
            
            // Tel projecten voor statistieken
            long projectenMetDocument = projecten.stream()
                    .filter(Project::isDocumentGegenereerd)
                    .count();
            
            long projectenZonderDocument = projecten.stream()
                    .filter(p -> !p.isDocumentGegenereerd())
                    .count();
            
            // Voeg data toe aan model
            model.addAttribute("projecten", projecten);
            model.addAttribute("projectenMetDocument", projectenMetDocument);
            model.addAttribute("projectenZonderDocument", projectenZonderDocument);
            
            return "projecten/overzicht";
            
        } catch (Exception e) {
            // Fallback: lege data bij fout
            model.addAttribute("projecten", new ArrayList<>());
            model.addAttribute("projectenMetDocument", 0L);
            model.addAttribute("projectenZonderDocument", 0L);
            return "projecten/overzicht";
        }
    }
    
    // Toon formulier voor nieuw project
    @GetMapping("/nieuw")
    public String nieuwProjectFormulier(Model model) {
        model.addAttribute("project", new Project());
        return "projecten/nieuw";
    }
    
    // Verwerk nieuw project formulier
    @PostMapping("/nieuw")
    public String nieuwProjectOpslaan(@Valid @ModelAttribute("project") Project project, 
                                     BindingResult bindingResult,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes,
                                     Model model) {
        
        // Controleer of projectnummer al bestaat
        if (projectService.projectNummerBestaat(project.getProjectNummer())) {
            bindingResult.rejectValue("projectNummer", "error.project", 
                "Dit projectnummer bestaat al. Kies een ander nummer.");
        }
        
        if (bindingResult.hasErrors()) {
            return "projecten/nieuw";
        }
        
        // Zet gebruiker email als bekend
        if (principal != null) {
            project.setGebruikerEmail(principal.getName());
        }
        
        Project opgeslagenProject = projectService.opslaanProject(project);
        
        redirectAttributes.addFlashAttribute("successMessage", 
            "Project '" + opgeslagenProject.getProjectNaam() + "' is succesvol aangemaakt!");
        
        // Redirect naar volgende stap in vragenlijst
        return "redirect:/projecten/" + opgeslagenProject.getId() + "/configuratie";
    }
    
    // Toon project details
    @GetMapping("/{id}")
    public String projectDetails(@PathVariable Long id, Model model) {
        Project project = projectService.vindProjectById(id);
        model.addAttribute("project", project);
        return "projecten/details";
    }
    
    // Configuratie stap (hier komen later de technische vragen)
    @GetMapping("/{id}/configuratie")
    public String projectConfiguratie(@PathVariable Long id, Model model) {
        Project project = projectService.vindProjectById(id);
        model.addAttribute("project", project);
        
        // TODO: Hier komen later de dynamische vragen
        // Voor nu tonen we gewoon de project details
        return "projecten/configuratie";
    }
    
    // Bewerk project
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
        
        // Controleer of projectnummer uniek is (behalve voor huidig project)
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
    
    // Verwijder project
    @PostMapping("/{id}/verwijderen")
    public String verwijderProject(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Project project = projectService.vindProjectById(id);
        projectService.verwijderProject(id);
        
        redirectAttributes.addFlashAttribute("successMessage", 
            "Project '" + project.getProjectNaam() + "' is verwijderd.");
        
        return "redirect:/projecten";
    }
    
    @PostMapping("/{id}/genereer-document")
    public String genereerDocument(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        
        System.out.println("Document wordt gegenereerd.");
        System.out.println("ID: " + id);
        
        // DEBUG: Check of DocumentService bestaat
        if (documentService == null) {
            System.out.println("ERROR: DocumentService is NULL!");
            redirectAttributes.addFlashAttribute("errorMessage", "DocumentService niet beschikbaar");
            return "redirect:/projecten/" + id;
        }
        System.out.println("DocumentService is OK");

        try {
            // Genereer document via DocumentService
            String bestandsnaam = documentService.genereerDocument(id);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Document '" + bestandsnaam + "' is succesvol gegenereerd! " +
                "Het document is opgeslagen in: C:\\Users\\sander.nales\\OneDrive - Vink\\Bureaublad\\installatie_omschrijvingen");
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Fout bij genereren document: " + e.getMessage());
        }
        
        return "redirect:/projecten/" + id;
    }
}