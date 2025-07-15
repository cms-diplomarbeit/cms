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
        System.out.println("Starting monitor thread for directory: " + watchDir);

        Thread monitorThread = new Thread(() -> {
            try {
                System.out.println("Monitor thread started, creating Monitor instance...");
                new Monitor(watchDir);
                System.out.println("Monitor instance created and running - checking files every 5 minutes");
            } catch (Exception e) {
                System.err.println("Error in monitor thread: " + e.getMessage());
                e.printStackTrace();
            }
        });
        monitorThread.start();

        // Keep the main thread alive
        try {
            System.out.println("Main thread waiting. Press Ctrl+C to exit.");
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("Main thread interrupted, shutting down...");
        }
    }
}