package at.cms.api;
import java.util.Scanner;


public class OllamaClient {

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        SelectionHandler();
    }

    public static void SelectionHandler() {
        System.out.println("Bitte wählen Sie eines der folgenden Optionen aus: ");
        System.out.println("1) Tagesabruf");
        System.out.println("2) Vergleich zweier Datensätze");
        System.out.println("3) Abruf per SubscriptionTag");
        System.out.print("Ihre Eingabe: ");
        String input = scanner.nextLine();

        AIRequest.Choice(Integer.parseInt(input));
    }
}
