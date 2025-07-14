package at.cms;

import at.cms.config.AppConfig;
import at.cms.processing.DataProcessor;
import at.cms.processing.Vectorizer;
import at.cms.training.db.Repository;
import at.cms.training.Monitor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
        Repository.connect();
        
        // Check if running in vectorizer mode
        if (args.length > 0 && "--vectorizer".equals(args[0])) {
            System.out.println("Starting in Vectorizer mode...");
            new Vectorizer();
        } else {
            // Determine service mode
            AppConfig.ServiceMode mode = AppConfig.getServiceMode();
            String watchDir = args.length > 0 && !args[0].startsWith("--") ? args[0] : AppConfig.getWatchDir();
            
            System.out.println("Starting CMS Application in mode: " + mode);
            System.out.println("Watch directory: " + watchDir);
            
            switch (mode) {
                case DATA_PROCESSOR:
                    System.out.println("Starting Data Processor service...");
                    new DataProcessor(watchDir);
                    break;
                    
                case VECTORIZER:
                    System.out.println("Starting Vectorizer service...");
                    new Vectorizer();
                    break;
                    
                case FULL:
                default:
                    System.out.println("Starting full Monitor service...");
                    new Monitor(watchDir);
                    break;
            }
        }
        
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}