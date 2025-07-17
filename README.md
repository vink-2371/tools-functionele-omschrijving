# Tools-functionele-omschrijving

## 📋 PROJECT STATUS OVERZICHT
### 🎯 Doel van het Project:
Automatische generatie van functionele omschrijvingen voor regeltechniek bij Vink Installatie Groep. Engineers vullen een vragenlijst in, systeem genereert automatisch een Word document door SharePoint tekstblokken samen te voegen.

# ✅ WAT IS COMPLEET GEBOUWD:
## 1. Database Structuur (PostgreSQL)

Project Entity met velden:

Basis: projectNaam, projectNummer, omschrijving, aangemaaktOp
Document: documentGegenereerd, documentGenereerdOp, documentSharepointUrl, documentBestandsnaam
Extra: configuratieJson (voor vragenlijst antwoorden), gebruikerEmail


ProjectRepository met query methods
ProjectService met business logica
Database connectie werkend met lokale PostgreSQL

## 2. Complete Web Applicatie (Spring Boot + Thymeleaf)
Templates gemaakt:

overzicht.html - Hoofdpagina met projecten, statistieken, zoeken/filteren
nieuw.html - Project aanmaken formulier met validatie
details.html - Project details pagina met acties
bewerken.html - Project wijzigen formulier
configuratie.html - Intelligente vragenlijst wizard

Controllers:

ProjectController - Alle CRUD operaties
HomeController - Homepage redirect
CustomErrorController - Foutafhandeling

## 3. Intelligente Vragenlijst Engine

Conditionele logica: Warmtepomp → extra vragen, VRF → andere flow
Progress tracking met visuele voortgangsbalk
Multi-choice support voor opties
Real-time validatie en navigation
JSON configuratie opslag in database

## 4. Complete Vink Huisstijl

Kleuren: Vink groen (#12785e), Vink geel (#cccd22), grijs tinten
Consistent design over alle 5 templates
Responsive layout met Bootstrap 5
Logo placeholder aanwezig

## 5. Werkende Features

✅ Project CRUD: Aanmaken, bekijken, bewerken, verwijderen
✅ Database opslag: Alles wordt opgeslagen in PostgreSQL
✅ Vragenlijst flow: Conditie logica werkt perfect
✅ Statistieken: Totaal, klaar, in behandeling
✅ Form validatie: Client + server side
✅ Error handling: Graceful fallbacks

-------

# 🔧 WAT NOG MOET GEBEUREN:
Volgende Stappen:

- SharePoint Integratie 
- Microsoft Graph API voor tekstblokken 
- Document Assembly Apache POI voor Word document generatie
- Vragenlijst Uitbreiden met specifieke technische vragen
- Advanced Features, zoals:
Document download, email, archivering


## 💻 TECHNISCHE STACK:

Backend: Java Spring Boot, JPA/Hibernate
Frontend: Thymeleaf, Bootstrap 5, JavaScript
Database: PostgreSQL (lokaal)
Deployment: Azure App Service ready
Future: Microsoft Graph API, Apache POI


### 📁 BESTANDSSTRUCTUUR:
```
src/main/java/nl/vink/installatie/
├── controller/ProjectController.java
├── entity/Project.java  
├── service/ProjectService.java
├── repository/ProjectRepository.java

src/main/resources/templates/projecten/
├── overzicht.html
├── nieuw.html
├── details.html  
├── bewerken.html
└── configuratie.html
```

------

Status: WERKENDE APPLICATIE KLAAR VOOR UITBREIDING 🚀