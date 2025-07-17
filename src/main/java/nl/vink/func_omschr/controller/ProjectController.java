package nl.vink.func_omschr.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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
import nl.vink.func_omschr.service.ProjectService;

@Controller
@RequestMapping("/projecten")
public class ProjectController {
    
    @Autowired
    private ProjectService projectService;
    
    // Overzichtspagina met alle projecten
    @GetMapping
    public String projectOverzicht(Model model) {
        System.out.println("🔍 ProjectController.projectOverzicht() aangeroepen");
        
        List<Project> projecten = projectService.getAlleProjecten();
        System.out.println("📊 Aantal projecten opgehaald: " + (projecten != null ? projecten.size() : "NULL"));
        
        // Zorg ervoor dat projecten nooit null is
        if (projecten == null) {
            projecten = new ArrayList<>();
            System.out.println("⚠️ Projecten was null, lege lijst aangemaakt");
        }
        
        // Tel projecten voor statistieken
        long projectenMetDocument = projecten.stream()
                .filter(Project::isDocumentGegenereerd)
                .count();
        
        long projectenZonderDocument = projecten.stream()
                .filter(p -> !p.isDocumentGegenereerd())
                .count();
        
        System.out.println("📈 Statistieken - Met document: " + projectenMetDocument + ", Zonder document: " + projectenZonderDocument);
        
        // Voeg data toe aan model
        model.addAttribute("projecten", projecten);
        model.addAttribute("projectenMetDocument", projectenMetDocument);
        model.addAttribute("projectenZonderDocument", projectenZonderDocument);
        
        System.out.println("✅ Model attributes toegevoegd, naar template...");
        return "projecten/overzicht";
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
    
    // Document genereren (placeholder voor later)
    @PostMapping("/{id}/document-genereren")
    public String genereersDocument(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Project project = projectService.vindProjectById(id);
        
        // TODO: Hier komt later de document generatie logica
        project.markeerDocumentAlsGegenereerd();
        projectService.bijwerkenProject(project);
        
        redirectAttributes.addFlashAttribute("successMessage", 
            "Document wordt gegenereerd! (Dit is nog een placeholder)");
        
        return "redirect:/projecten/" + id;
    }
}