package nl.vink.func_omschr.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "projects")
public class Project {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Projectnaam is verplicht")
    @Size(max = 200, message = "Projectnaam mag maximaal 200 karakters zijn")
    @Column(name = "project_naam", nullable = false)
    private String projectNaam;
    
    @NotBlank(message = "Projectnummer is verplicht")
    @Size(max = 50, message = "Projectnummer mag maximaal 50 karakters zijn")
    @Column(name = "project_nummer", nullable = false, unique = true)
    private String projectNummer;
    
    @Size(max = 1000, message = "Omschrijving mag maximaal 1000 karakters zijn")
    @Column(name = "omschrijving", length = 1000)
    private String omschrijving;
    
    @Column(name = "aangemaakt_op", nullable = false)
    private LocalDateTime aangemaaktOp;
    
    @Column(name = "gebruiker_email")
    private String gebruikerEmail;
    
    @Column(name = "document_gegenereerd")
    private boolean documentGegenereerd = false;
    
    @Column(name = "document_gegenereerd_op")
    private LocalDateTime documentGenereerdOp;
    
    @Column(name = "document_sharepoint_url")
    private String documentSharepointUrl;
    
    @Column(name = "document_bestandsnaam")
    private String documentBestandsnaam;
    
    @Column(name = "document_grootte_bytes")
    private Long documentGrootteBytes;
    
    @Column(name = "configuratie_json", columnDefinition = "TEXT")
    private String configuratieJson;
    
    // Constructors
    public Project() {
        this.aangemaaktOp = LocalDateTime.now();
    }
    
    public Project(String projectNaam, String projectNummer, String omschrijving) {
        this();
        this.projectNaam = projectNaam;
        this.projectNummer = projectNummer;
        this.omschrijving = omschrijving;
    }
    
    // Getters en Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getProjectNaam() {
        return projectNaam;
    }
    
    public void setProjectNaam(String projectNaam) {
        this.projectNaam = projectNaam;
    }
    
    public String getProjectNummer() {
        return projectNummer;
    }
    
    public void setProjectNummer(String projectNummer) {
        this.projectNummer = projectNummer;
    }
    
    public String getOmschrijving() {
        return omschrijving;
    }
    
    public void setOmschrijving(String omschrijving) {
        this.omschrijving = omschrijving;
    }
    
    public LocalDateTime getAangemaaktOp() {
        return aangemaaktOp;
    }
    
    public void setAangemaaktOp(LocalDateTime aangemaaktOp) {
        this.aangemaaktOp = aangemaaktOp;
    }
    
    public String getGebruikerEmail() {
        return gebruikerEmail;
    }
    
    public void setGebruikerEmail(String gebruikerEmail) {
        this.gebruikerEmail = gebruikerEmail;
    }
    
    public boolean isDocumentGegenereerd() {
        return documentGegenereerd;
    }
    
    public void setDocumentGegenereerd(boolean documentGegenereerd) {
        this.documentGegenereerd = documentGegenereerd;
    }
    
    public LocalDateTime getDocumentGenereerdOp() {
        return documentGenereerdOp;
    }
    
    public void setDocumentGenereerdOp(LocalDateTime documentGenereerdOp) {
        this.documentGenereerdOp = documentGenereerdOp;
    }
    
    public String getDocumentSharepointUrl() {
        return documentSharepointUrl;
    }
    
    public void setDocumentSharepointUrl(String documentSharepointUrl) {
        this.documentSharepointUrl = documentSharepointUrl;
    }
    
    public String getDocumentBestandsnaam() {
        return documentBestandsnaam;
    }
    
    public void setDocumentBestandsnaam(String documentBestandsnaam) {
        this.documentBestandsnaam = documentBestandsnaam;
    }
    
    public Long getDocumentGrootteBytes() {
        return documentGrootteBytes;
    }
    
    public void setDocumentGrootteBytes(Long documentGrootteBytes) {
        this.documentGrootteBytes = documentGrootteBytes;
    }
    
    public String getConfiguratieJson() {
        return configuratieJson;
    }
    
    public void setConfiguratieJson(String configuratieJson) {
        this.configuratieJson = configuratieJson;
    }
    
    // Helper methods
    public String getGeformateerdeDatum() {
        return aangemaaktOp.toLocalDate().toString();
    }
    
    public void markeerDocumentAlsGegenereerd() {
        this.documentGegenereerd = true;
        this.documentGenereerdOp = LocalDateTime.now();
    }
    
    public void markeerDocumentAlsGegenereerd(String sharepointUrl, String bestandsnaam, Long grootte) {
        this.documentGegenereerd = true;
        this.documentGenereerdOp = LocalDateTime.now();
        this.documentSharepointUrl = sharepointUrl;
        this.documentBestandsnaam = bestandsnaam;
        this.documentGrootteBytes = grootte;
    }
    
    public boolean heeftDocument() {
        return documentGegenereerd && documentSharepointUrl != null;
    }
    
    @Override
    public String toString() {
        return "Project{" +
                "id=" + id +
                ", projectNaam='" + projectNaam + '\'' +
                ", projectNummer='" + projectNummer + '\'' +
                ", aangemaaktOp=" + aangemaaktOp +
                '}';
    }
}