package at.cms;

import at.cms.training.Monitor;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {

        // Only for Testpurposes
        String watchDir = "./watched";
        Monitor monitor = new Monitor(watchDir);
        try {
            monitor.start();
            System.out.println("Watcher läuft. Füge eine PDF in das Verzeichnis ein...");
            Thread.sleep(10 * 60 * 1000); // 10 Minuten laufen lassen
            monitor.stop();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}