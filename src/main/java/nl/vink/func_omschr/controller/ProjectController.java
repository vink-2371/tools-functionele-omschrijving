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
import org.springframework.web.bind.annotation.RequestParam;
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
    @SuppressWarnings("CallToPrintStackTrace")
    public String genereerDocument(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        
        System.out.println("Document wordt gegenereerd voor project: " + id);

        try {
            // ECHTE IMPLEMENTATIE - niet meer uitgecommentarieerd!
            String bestandsnaam = documentService.genereerDocument(id);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Document '" + bestandsnaam + "' is succesvol gegenereerd en geüpload naar SharePoint!");
            
        } catch (Exception e) {
            System.err.println("Fout bij genereren document: " + e.getMessage());
            e.printStackTrace();
            
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Fout bij genereren document: " + e.getMessage());
        }
        
        return "redirect:/projecten/" + id;
    }

    @PostMapping("/{id}/configuratie")
    @SuppressWarnings("CallToPrintStackTrace")
    public String opslaanConfiguratie(@PathVariable Long id, 
                                    @RequestParam String configuratieJson,
                                    RedirectAttributes redirectAttributes) {
        try {
            System.out.println("=== CONFIGURATIE OPSLAAN ===");
            System.out.println("Project ID: " + id);
            System.out.println("Configuratie JSON: " + configuratieJson);
            
            // Haal project op
            Project project = projectService.vindProjectById(id);
            System.out.println("Project gevonden: " + project.getProjectNaam());
            
            // Sla configuratie op
            Project bijgewerktProject = projectService.opslaanConfiguratie(id, configuratieJson);
            System.out.println("Configuratie opgeslagen voor project: " + bijgewerktProject.getProjectNaam());
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Project '" + project.getProjectNaam() + "' is succesvol geconfigureerd! " +
                "Je kunt nu het document genereren via de project details.");
            
            System.out.println("Redirect naar projectoverzicht");
            return "redirect:/projecten";
            
        } catch (Exception e) {
            System.err.println("Fout bij opslaan configuratie: " + e.getMessage());
            e.printStackTrace();
            
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Fout bij opslaan configuratie: " + e.getMessage());
            
            return "redirect:/projecten/" + id + "/configuratie";
        }
    }
}