package nl.vink.func_omschr;

import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import nl.vink.func_omschr.service.DocumentService;

@SpringBootApplication
public class FunctioneleOmschrijvingApplication {

	public static void main(String[] args) {		
		ConfigurableApplicationContext context = SpringApplication.run(FunctioneleOmschrijvingApplication.class, args);
		// DEBUG: Test of DocumentService wordt geladen
        try {
            DocumentService docService = context.getBean(DocumentService.class);
            System.out.println("✅ DocumentService successfully loaded: " + docService.getClass().getName());
        } catch (BeansException e) {
            System.out.println("❌ DocumentService FAILED to load: " + e.getMessage());
        }
	}

}
