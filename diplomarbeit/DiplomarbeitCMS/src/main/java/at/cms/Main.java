package at.cms;

import at.cms.training.db.Repository;
import at.cms.training.Monitor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
        Repository.connect();
        String watchDir = args.length > 0 ? args[0] : "./watched";
        new Monitor(watchDir);
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}