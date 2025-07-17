package nl.vink.func_omschr;

import java.sql.Connection;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Test klasse om database verbinding te controleren bij opstarten
 * Deze klasse wordt automatisch uitgevoerd wanneer de applicatie start
 */
@Component
public class DatabaseConnectionTest implements CommandLineRunner {
    
    @Autowired
    private DataSource dataSource;
    
    @Override
    public void run(String... args) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("🔗 TESTING DATABASE CONNECTION...");
        System.out.println("=".repeat(60));
        
        try (Connection connection = dataSource.getConnection()) {
            System.out.println("✅ Database verbinding succesvol!");
            System.out.println("📊 Database URL: " + connection.getMetaData().getURL());
            System.out.println("👤 Database User: " + connection.getMetaData().getUserName());
            System.out.println("🏷️ Database Product: " + connection.getMetaData().getDatabaseProductName());
            System.out.println("📋 Database Version: " + connection.getMetaData().getDatabaseProductVersion());
            
            // Test een simpele query
            var statement = connection.createStatement();
            var resultSet = statement.executeQuery("SELECT current_database(), current_user, version()");
            
            if (resultSet.next()) {
                System.out.println("🗄️ Current Database: " + resultSet.getString(1));
                System.out.println("👨‍💼 Current User: " + resultSet.getString(2));
                System.out.println("ℹ️ PostgreSQL Version: " + resultSet.getString(3));
            }
            
        } catch (Exception e) {
            System.err.println("❌ Database verbinding gefaald!");
            System.err.println("🔍 Error: " + e.getMessage());
            System.err.println("💡 Controleer of PostgreSQL draait en database bestaat");
            throw e;
        }
        
        System.out.println("=".repeat(60));
        System.out.println("🚀 Vink Installatie Generator is klaar voor gebruik!");
        System.out.println("🌐 Open browser: http://localhost:8080/projecten");
        System.out.println("=".repeat(60));
    }
}