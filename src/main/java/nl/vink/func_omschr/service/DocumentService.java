package nl.vink.func_omschr.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.vink.func_omschr.model.Project;

@Service
@Transactional
public class DocumentService {
    
    @Autowired
    private ProjectService projectService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String DOCUMENT_OUTPUT_PATH = "C:\\Users\\sander.nales\\OneDrive - Vink\\Bureaublad\\installatie_omschrijvingen";
    
    /**
     * Genereert een Word document voor een project
     */
    public String genereerDocument(Long projectId) throws Exception {
        System.out.println("Project proberen op te halen met id: " + projectId);
        // 1. Haal project op
        Project project = projectService.vindProjectById(projectId);
        System.out.println(project);

        // 2. Maak JSON configuratie
        String configuratieJson = maakTijdelijkeConfiguratie(project);

        // 3. Parse JSON
        JsonNode configuratie = objectMapper.readTree(configuratieJson);
        System.out.println(configuratie);

        // 4. Genereer Word document
        String bestandsnaam = genereersWordDocument(configuratie);
        System.out.println(bestandsnaam);

        // 5. Update project in database
        project.setConfiguratieJson(configuratieJson);
        project.markeerDocumentAlsGegenereerd();
        projectService.bijwerkenProject(project);
        
        return bestandsnaam;
    }
    
    /**
     * Maakt tijdelijke JSON configuratie op basis van project data
     */
    private String maakTijdelijkeConfiguratie(Project project) throws Exception {
        ObjectNode configuratie = objectMapper.createObjectNode();
        
        // Tekstvelden - 1:1 overnemen
        configuratie.put("project_naam", project.getProjectNaam());
        configuratie.put("project_nummer", project.getProjectNummer());
        configuratie.put("aanmaak_datum_document", LocalDateTime.now().toString());
        
        // Meerkeuze velden - voor nu tijdelijke waarden
        // Later halen we dit uit de echte configuratie JSON
        if (project.getConfiguratieJson() != null && !project.getConfiguratieJson().isEmpty()) {
            try {
                JsonNode bestaandeConfig = objectMapper.readTree(project.getConfiguratieJson());
                configuratie.put("type_installatie", 
                    bestaandeConfig.has("installatie_type") ? 
                    mapInstallatieTekst(bestaandeConfig.get("installatie_type").asText()) : "Niet gespecificeerd");
                configuratie.put("aantal_units", 
                    bestaandeConfig.has("aantal_units") ? 
                    bestaandeConfig.get("aantal_units").asText() : "Niet gespecificeerd");
            } catch (JsonProcessingException e) {
                // Fallback waardes
                configuratie.put("type_installatie", "Niet gespecificeerd");
                configuratie.put("aantal_units", "Niet gespecificeerd");
            }
        } else {
            // Default waardes als er nog geen configuratie is
            configuratie.put("type_installatie", "Nog niet geconfigureerd");
            configuratie.put("aantal_units", "Nog niet geconfigureerd");
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(configuratie);
    }
    
    /**
     * Mapping van installatie type codes naar leesbare tekst
     */
    private String mapInstallatieTekst(String installatieType) {
        return switch (installatieType.toLowerCase()) {
            case "warmtepomp" -> "Warmtepomp";
            case "vrf" -> "VRF Systeem";
            case "hybride" -> "Hybride Systeem";
            case "overig" -> "Overig";
            default -> installatieType;
        };
    }
    
    /**
     * Genereert het Word document met Apache POI
     */
    private String genereersWordDocument(JsonNode configuratie) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // Header/Titel
            XWPFParagraph titel = document.createParagraph();
            XWPFRun titelRun = titel.createRun();
            titelRun.setText("FUNCTIONELE OMSCHRIJVING REGELTECHNIEK");
            titelRun.setBold(true);
            titelRun.setFontSize(16);
            titelRun.setFontFamily("Arial");
            
            // Subtitel met project naam
            XWPFParagraph subtitel = document.createParagraph();
            XWPFRun subtitelRun = subtitel.createRun();
            subtitelRun.setText(configuratie.get("project_naam").asText());
            subtitelRun.setBold(true);
            subtitelRun.setFontSize(14);
            subtitelRun.setFontFamily("Arial");
            
            // Lege regel
            document.createParagraph();
            
            // Project informatie tabel
            XWPFTable table = document.createTable(5, 2);
            
            // Tabel styling
            table.setWidth("100%");
            
            // Rij 1: Project nummer
            XWPFTableRow row1 = table.getRow(0);
            XWPFTableCell cell1_1 = row1.getCell(0);
            cell1_1.setText("Projectnummer:");
            maakCelBold(cell1_1);
            XWPFTableCell cell1_2 = row1.getCell(1);
            cell1_2.setText(configuratie.get("project_nummer").asText());
            
            // Rij 2: Project naam
            XWPFTableRow row2 = table.getRow(1);
            XWPFTableCell cell2_1 = row2.getCell(0);
            cell2_1.setText("Projectnaam:");
            maakCelBold(cell2_1);
            XWPFTableCell cell2_2 = row2.getCell(1);
            cell2_2.setText(configuratie.get("project_naam").asText());
            
            // Rij 3: Datum
            XWPFTableRow row3 = table.getRow(2);
            XWPFTableCell cell3_1 = row3.getCell(0);
            cell3_1.setText("Datum document:");
            maakCelBold(cell3_1);
            XWPFTableCell cell3_2 = row3.getCell(1);
            LocalDateTime datum = LocalDateTime.parse(configuratie.get("aanmaak_datum_document").asText());
            cell3_2.setText(datum.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));
            
            // Rij 4: Type installatie
            XWPFTableRow row4 = table.getRow(3);
            XWPFTableCell cell4_1 = row4.getCell(0);
            cell4_1.setText("Type installatie:");
            maakCelBold(cell4_1);
            XWPFTableCell cell4_2 = row4.getCell(1);
            cell4_2.setText(configuratie.get("type_installatie").asText());
            
            // Rij 5: Aantal units
            XWPFTableRow row5 = table.getRow(4);
            XWPFTableCell cell5_1 = row5.getCell(0);
            cell5_1.setText("Aantal units:");
            maakCelBold(cell5_1);
            XWPFTableCell cell5_2 = row5.getCell(1);
            cell5_2.setText(configuratie.get("aantal_units").asText());
            
            // Lege regel
            document.createParagraph();
            
            // Functionele omschrijving sectie
            XWPFParagraph functieHeader = document.createParagraph();
            XWPFRun functieHeaderRun = functieHeader.createRun();
            functieHeaderRun.setText("1. FUNCTIONELE OMSCHRIJVING");
            functieHeaderRun.setBold(true);
            functieHeaderRun.setFontSize(12);
            functieHeaderRun.setFontFamily("Arial");
            
            // Placeholder tekst voor functionele omschrijving
            XWPFParagraph functieTekst = document.createParagraph();
            XWPFRun functieTekstRun = functieTekst.createRun();
            functieTekstRun.setText("Dit project betreft de installatie van een " + 
                configuratie.get("type_installatie").asText().toLowerCase() + 
                " systeem met " + configuratie.get("aantal_units").asText() + 
                " units voor het project " + configuratie.get("project_naam").asText() + ".");
            functieTekstRun.setFontFamily("Arial");
            
            // Lege regel
            document.createParagraph();
            
            // Technische specificaties sectie
            XWPFParagraph techHeader = document.createParagraph();
            XWPFRun techHeaderRun = techHeader.createRun();
            techHeaderRun.setText("2. TECHNISCHE SPECIFICATIES");
            techHeaderRun.setBold(true);
            techHeaderRun.setFontSize(12);
            techHeaderRun.setFontFamily("Arial");
            
            // Placeholder voor technische specificaties
            XWPFParagraph techTekst = document.createParagraph();
            XWPFRun techTekstRun = techTekst.createRun();
            techTekstRun.setText("De technische specificaties worden automatisch gegenereerd op basis van de geselecteerde configuratie. " +
                "Hier komen later de specifieke tekstblokken uit SharePoint.");
            techTekstRun.setFontFamily("Arial");
            
            // Footer
            document.createParagraph();
            XWPFParagraph footer = document.createParagraph();
            XWPFRun footerRun = footer.createRun();
            footerRun.setText("Gegenereerd door Vink Installatie Groep - Functionele Omschrijvingen Generator");
            footerRun.setFontSize(8);
            footerRun.setFontFamily("Arial");
            footerRun.setItalic(true);
            
            // Bestand opslaan
            String bestandsnaam = genereerBestandsnaam(configuratie);
            String volledigPad = DOCUMENT_OUTPUT_PATH + File.separator + bestandsnaam;
            
            // Zorg ervoor dat de directory bestaat
            File directory = new File(DOCUMENT_OUTPUT_PATH);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            
            // Sla bestand op
            try (FileOutputStream out = new FileOutputStream(volledigPad)) {
                document.write(out);
            }
            
            return bestandsnaam;
            
        }
    }
    
    /**
     * Helper methode om tabel cel tekst bold te maken
     */
    private void maakCelBold(XWPFTableCell cell) {
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontFamily("Arial");
    }
    
    /**
     * Genereert unieke bestandsnaam
     */
    private String genereerBestandsnaam(JsonNode configuratie) {
        String projectNummer = configuratie.get("project_nummer").asText().replaceAll("[^a-zA-Z0-9-_]", "");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return "Functionele_Omschrijving_" + projectNummer + "_" + timestamp + ".docx";
    }
}