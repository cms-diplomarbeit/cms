package at.cms.api;
import java.io.IOException;
import java.util.Scanner;


public class OllamaClient {

    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        SelectionHandler();

    }

    public static String Input() throws IOException, InterruptedException {
        scanner = new Scanner(System.in);
        System.out.print("Bitte geben Sie die SubscriptionID ein: ");
        String subscriptionID = scanner.nextLine();
        System.out.print("Bitte geben Sie das Anfangsdatum ein: ");
        String measuredTimeFromUtc = scanner.nextLine();
        System.out.print("Bitte geben Sie das Enddatum ein: ");
        String measuredTimeToUtc = scanner.nextLine();
        System.out.print("Bitte geben Sie die ValueTypes ein (getrennt mit Beistrichen): ");
        String valueTypes = scanner.nextLine();
        System.out.print("Bitte geben Sie ein wie viele Values sie zurückbekommen wollen: ");
        int numberOfValues = Integer.parseInt(scanner.nextLine());
    }

    public static void SelectionHandler() {
        System.out.println("Bitte wählen Sie eines der folgenden Optionen aus: ");
        System.out.println("1) Tagesabruf");
        System.out.println("2) Vergleich zweier Datensätze");
        System.out.println("3) Abruf per SubscriptionTag");
        System.out.print("Ihre Eingabe: ");
        String input = scanner.nextLine();


    }
}
