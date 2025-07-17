package at.cms;

import at.cms.config.AppConfig;
import at.cms.processing.DataProcessor;
import at.cms.processing.Vectorizer;
import at.cms.training.db.Repository;
import at.cms.training.Monitor;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Main implements QuarkusApplication {
    
    @Override
    public int run(String... args) throws Exception {
        System.out.println("Initializing database connection...");
        Repository.connect();

        // Determine service mode from environment
        AppConfig.ServiceMode mode = AppConfig.getServiceMode();
        String watchDir = args.length > 0 ? args[0] : AppConfig.getWatchDir();

        System.out.println("Starting CMS Application in mode: " + mode);
        System.out.println("Watch directory: " + watchDir);

        switch (mode) {
            case DATA_PROCESSOR -> {
                System.out.println("Starting Data Processor service...");
                new DataProcessor();
            }
            case VECTORIZER -> {
                System.out.println("Starting in Vectorizer mode...");
                new Vectorizer();
            }
            case FULL -> {
                System.out.println("Starting full Monitor service...");
                new Monitor(watchDir);
            }
        }

        // Keep the application running
        Quarkus.waitForExit();
        return 0;
    }

    public static void main(String... args) {
        Quarkus.run(Main.class, args);
    }
}
