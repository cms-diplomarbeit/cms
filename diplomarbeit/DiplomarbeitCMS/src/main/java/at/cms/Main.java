package at.cms;

import at.cms.training.db.Repository;
import at.cms.training.Monitor;

public class Main {
    public static void main(String[] args) {
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