package nl.vink.func_omschr.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nl.vink.func_omschr.model.Project;
import nl.vink.func_omschr.repository.ProjectRepository;

@Service
@Transactional
public class ProjectService {
    
    @Autowired
    private ProjectRepository projectRepository;
    
    /**
     * Haalt alle projecten op, gesorteerd op aanmaakdatum (nieuwste eerst)
     */
    public List<Project> getAlleProjecten() {
        return projectRepository.findAllByOrderByAangemaaktOpDesc();
    }
    
    /**
     * Zoekt een project op ID
     * @param id Het project ID
     * @return Het gevonden project
     * @throws RuntimeException als project niet bestaat
     */
    public Project vindProjectById(Long id) {
        Optional<Project> project = projectRepository.findById(id);
        if (project.isPresent()) {
            return project.get();
        } else {
            throw new RuntimeException("Project met ID " + id + " niet gevonden");
        }
    }
    
    /**
     * Zoekt een project op projectnummer
     */
    public Optional<Project> vindProjectByProjectNummer(String projectNummer) {
        return projectRepository.findByProjectNummer(projectNummer);
    }
    
    /**
     * Controleert of een projectnummer al bestaat
     */
    public boolean projectNummerBestaat(String projectNummer) {
        return projectRepository.existsByProjectNummer(projectNummer);
    }
    
    /**
     * Controleert of een projectnummer bestaat voor een ander project (bij updates)
     */
    public boolean projectNummerBestaatVoorAnderProject(String projectNummer, Long projectId) {
        Optional<Project> bestaandProject = projectRepository.findByProjectNummer(projectNummer);
        return bestaandProject.isPresent() && !bestaandProject.get().getId().equals(projectId);
    }
    
    /**
     * Slaat een nieuw project op
     */
    public Project opslaanProject(Project project) {
        if (project.getId() != null) {
            throw new IllegalArgumentException("Nieuw project mag geen ID hebben");
        }
        
        if (projectNummerBestaat(project.getProjectNummer())) {
            throw new IllegalArgumentException("Projectnummer " + project.getProjectNummer() + " bestaat al");
        }
        
        return projectRepository.save(project);
    }
    
    /**
     * Werkt een bestaand project bij
     */
    public Project bijwerkenProject(Project project) {
        if (project.getId() == null) {
            throw new IllegalArgumentException("Project moet een ID hebben voor update");
        }
        
        // Controleer of project bestaat
        vindProjectById(project.getId());
        
        return projectRepository.save(project);
    }
    
    /**
     * Verwijdert een project
     */
    public void verwijderProject(Long id) {
        Project project = vindProjectById(id);
        projectRepository.delete(project);
    }
    
    /**
     * Zoekt projecten op basis van naam (voor zoekfunctionaliteit)
     */
    public List<Project> zoekProjectenOpNaam(String zoekterm) {
        return projectRepository.findByProjectNaamContainingIgnoreCase(zoekterm);
    }
    
    /**
     * Haalt projecten op van een specifieke gebruiker
     */
    public List<Project> getProjectenVanGebruiker(String gebruikerEmail) {
        return projectRepository.findByGebruikerEmailOrderByAangemaaktOpDesc(gebruikerEmail);
    }
    
    /**
     * Haalt recente projecten op (laatste 10)
     */
    public List<Project> getRecenteProjecten() {
        return projectRepository.findTop10ByOrderByAangemaaktOpDesc();
    }
    
    /**
     * Haalt projecten op waar nog geen document voor is gegenereerd
     */
    public List<Project> getProjectenZonderDocument() {
        return projectRepository.findByDocumentGenereerdFalseOrderByAangemaaktOpDesc();
    }
    
    /**
     * Genereert een uniek projectnummer (helper methode)
     */
    public String genereersUniekeProjectNummer(String prefix) {
        String basisNummer = prefix + System.currentTimeMillis();
        
        // Controleer of nummer al bestaat (zeer onwaarschijnlijk, maar veiligheid)
        int teller = 1;
        String projectNummer = basisNummer;
        while (projectNummerBestaat(projectNummer)) {
            projectNummer = basisNummer + "-" + teller;
            teller++;
        }
        
        return projectNummer;
    }
    
    /**
     * Telt het totaal aantal projecten
     */
    public long getTotaalAantalProjecten() {
        return projectRepository.count();
    }
}