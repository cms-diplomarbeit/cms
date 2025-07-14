package at.cms;

import at.cms.training.db.Repository;
import at.cms.training.Monitor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        Repository.connect();

        String watchDir = args.length > 0 ? args[0] : "./watched";
        Thread monitorThread = new Thread(() -> {
            try {
                new Monitor(watchDir);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();

        SpringApplication.run(Main.class, args);
    }
}