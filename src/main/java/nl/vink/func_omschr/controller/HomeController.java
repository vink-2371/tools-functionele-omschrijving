package nl.vink.func_omschr.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import nl.vink.func_omschr.model.Project;
import nl.vink.func_omschr.service.ProjectService;

@Controller
public class HomeController {

    @Autowired
    private ProjectService projectService;
    
    /**
     * Homepage redirect naar projectenoverzicht
     */
    @GetMapping("/")
    public String home(Model model) {
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
    }
}