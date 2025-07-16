package at.cms;

import at.cms.training.db.Repository;
import at.cms.training.Monitor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        System.out.println("Initializing database connection...");
        Repository.connect();

        String watchDir = args.length > 0 ? args[0] : "./watched";
        System.out.println("Starting monitor for directory: " + watchDir);
        try {
            System.out.println("Creating Monitor instance...");
            new Monitor(watchDir);
            System.out.println("Monitor instance created and running");
        } catch (Exception e) {
            System.err.println("Error in monitor: " + e.getMessage());
            e.printStackTrace();
        }
        SpringApplication.run(Main.class, args);
    }
}