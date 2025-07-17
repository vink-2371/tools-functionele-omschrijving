package nl.vink.func_omschr;

import java.sql.Connection;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Test klasse om database verbinding te controleren bij opstarten
 */
@Component
public class DatabaseConnectionTest implements CommandLineRunner {
    
    @Autowired
    private DataSource dataSource;
    
    @Override
    public void run(String... args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            // Stille database verbinding test
            connection.getMetaData().getURL();
        } catch (Exception e) {
            System.err.println("Database verbinding gefaald: " + e.getMessage());
            throw e;
        }
    }
}