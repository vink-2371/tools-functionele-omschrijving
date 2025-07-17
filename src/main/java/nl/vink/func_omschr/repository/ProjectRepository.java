package nl.vink.func_omschr.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import nl.vink.func_omschr.model.Project;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    
    /**
     * Zoekt project op projectnummer
     */
    Optional<Project> findByProjectNummer(String projectNummer);
    
    /**
     * Controleert of projectnummer bestaat
     */
    boolean existsByProjectNummer(String projectNummer);
    
    /**
     * Haalt alle projecten op gesorteerd op aanmaakdatum (nieuwste eerst)
     */
    List<Project> findAllByOrderByAangemaaktOpDesc();
    
    /**
     * Zoekt projecten op basis van naam (case insensitive)
     */
    List<Project> findByProjectNaamContainingIgnoreCase(String projectNaam);
    
    /**
     * Haalt projecten op van een specifieke gebruiker
     */
    List<Project> findByGebruikerEmailOrderByAangemaaktOpDesc(String gebruikerEmail);
    
    /**
     * Haalt de laatste 10 projecten op
     */
    List<Project> findTop10ByOrderByAangemaaktOpDesc();
    
    /**
     * Haalt projecten op waar nog geen document voor is gegenereerd
     */
    List<Project> findByDocumentGenereerdFalseOrderByAangemaaktOpDesc();
    
    /**
     * Haalt projecten op waar al een document voor is gegenereerd
     */
    List<Project> findByDocumentGenereerdTrueOrderByDocumentGenereerdOpDesc();
    
    /**
     * Custom query: zoek op projectnummer of naam
     */
    @Query("SELECT p FROM Project p WHERE " +
           "LOWER(p.projectNummer) LIKE LOWER(CONCAT('%', :zoekterm, '%')) OR " +
           "LOWER(p.projectNaam) LIKE LOWER(CONCAT('%', :zoekterm, '%'))")
    List<Project> zoekProjecten(@Param("zoekterm") String zoekterm);
    
    /**
     * Custom query: haalt projecten op uit een bepaalde periode
     */
    @Query("SELECT p FROM Project p WHERE " +
           "p.aangemaaktOp >= :startDatum AND p.aangemaaktOp <= :eindDatum " +
           "ORDER BY p.aangemaaktOp DESC")
    List<Project> findProjectenInPeriode(@Param("startDatum") java.time.LocalDateTime startDatum,
                                        @Param("eindDatum") java.time.LocalDateTime eindDatum);
    
    /**
     * Custom query: telt projecten per gebruiker
     */
    @Query("SELECT p.gebruikerEmail, COUNT(p) FROM Project p " +
           "WHERE p.gebruikerEmail IS NOT NULL " +
           "GROUP BY p.gebruikerEmail")
    List<Object[]> telProjectenPerGebruiker();
}