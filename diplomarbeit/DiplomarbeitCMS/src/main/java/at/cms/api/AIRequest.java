package at.cms.api;

import at.cms.api.Prompts.Prompts;
import at.cms.api.enums.Choices;
import at.cms.api.exceptions.ChoiceNotFoundException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class AIRequest {
    private static final Scanner scanner = new Scanner(System.in);
    private static final String username = "ai-haus@cms-building.at";
    private static final String tokenUrl = "https://auth.cms-building.at/realms/Cms/protocol/openid-connect/token";
    private static final String url = "https://cms-server.cms-building.at/";
    private static final String clientId = "CMS";

    public static void Choice(int number) {
        try {
            Choices choice = Choices.values()[number-1];
            System.out.print("Enter your password for " + username + ": ");
            String password = scanner.nextLine();
            String accessToken = AuthenticationClient.fetchOAuth2Token(clientId, tokenUrl, username, password);
            if (choice == Choices.DailyCall) {
                DailyCall(accessToken);
            } else if (choice == Choices.Comparison) {
                Comparison();
            } else if (choice == Choices.StatusPerTag) {
                StatusPerTag(accessToken);
            } else {
                throw new ChoiceNotFoundException("Choice not found");
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }


    public static void DailyCall(String accessToken) {
        EnergyStats.Stats stats = null;
        System.out.print("Please enter the SubscriptionIDs: ");
        String subscriptionIDs = scanner.nextLine();
        var dates = getDates();
        String valueTypes = "AVERAGE,MIN,MAX";
        int numberOfValues = 10;

        var uri = URIBuilder(subscriptionIDs, dates[1].toString(), dates[0].toString(), valueTypes, numberOfValues);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject root = new JSONObject(response.body());
            var responseStats = root
                    .getJSONArray("subscriptionData")
                    .getJSONObject(0)
                    .getJSONArray("values");
            var name = root
                    .getJSONArray("subscriptionData")
                    .getJSONObject(0)
                    .getString("translatedName");
            var unit = root
                    .getJSONArray("subscriptionData")
                    .getJSONObject(0)
                    .getString("humanReadableUnit");
            stats = EnergyStats.computedStats(response.body());
        } catch (Exception e) {
            e.printStackTrace();
        }

        String dailyPrompt = Prompts.getDailyCallPrompt(stats.toString());
        try {
            String answer = KICall(dailyPrompt);
            System.out.println(answer);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void Comparison() {
        System.out.print("Please enter the SubscriptionIDs: ");
        String subscriptionIDs = scanner.nextLine();
        System.out.print("Please enter the first start date (format 2025-07-03T12:00:00.000): ");
        String date1 = scanner.nextLine();
        System.out.print("Please enter the first end date (format 2025-07-03T12:00:00.000): ");
        String date2 = scanner.nextLine();
        System.out.print("Please enter the second start date (format 2025-07-03T12:00:00.000): ");
        String date3 = scanner.nextLine();
        System.out.print("Please enter the second end date (format 2025-07-03T12:00:00.000): ");
        String date4 = scanner.nextLine();
        String valueTypes = "AVERAGE,MIN,MAX";
        int numberOfValues = 1400;

        var uri = URIBuilder(subscriptionIDs, date1, date2, valueTypes, numberOfValues);
        var uri2 = URIBuilder(subscriptionIDs, date3, date4, valueTypes, numberOfValues);
        String comparisonPrompt = Prompts.getComparisonCallPrompt(uri, uri2);
        try {
            var output = KICall(comparisonPrompt);
            System.out.println(output);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void StatusPerTag(String accessToken) {
        String endpoint = "https://cms-server.cms-building.at/tagtosubscriptions";
        String uriString = endpoint + "?languageKey=de";
        URI uri = URI.create(uriString);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if(response.statusCode() != 200) {
                System.err.println("Fehler beim Aufruf "+ response.statusCode());
                System.err.println(response.body());
                return;
            }

            String body = response.body().trim();
            JSONObject root = new JSONObject(body);
            JSONArray tags = root.getJSONArray("entries");

            var map = TagSelector.mapTranslationsToSubs(tags);
            boolean foundTag = false;
            while(!foundTag) {
                System.out.print("Please enter the tag that you want to analyse: ");
                var input = scanner.nextLine();

                if(!map.containsKey(input)) {
                    System.out.println("Tag not found");
                }
                System.out.println("Hi");
            }

        } catch(IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String KICall(String prompt) throws IOException, InterruptedException {
        String model = "llama3";

        JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", new JSONArray()
                        .put(new JSONObject()
                                .put("role", "user")
                                .put("content", prompt)
                        )
                );

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://file1.lan.elite-zettl.at:11434/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.err.println("Fehler: " + response.body());
        }
        JSONObject json = new JSONObject(response.body());
        String content = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");

        return content;
    }

    public static URI URIBuilder(String subscriptions, String measuredTimeFromUtc, String measuredTimeToUtc,
                                 String valueTypes, int numberOfValues) {
        String params = String.format(
                "subscriptions=%s&measuredFromUtc=%s&measuredToUtc=%s&valueTypes=%s&numberOfValues=%d&languageKey=de",
                urlEncode(subscriptions),
                urlEncode(measuredTimeFromUtc),
                urlEncode(measuredTimeToUtc),
                urlEncode(valueTypes),
                numberOfValues
        );

        String endpoint = "https://cms-server.cms-building.at/graphdata";
        return URI.create(endpoint + "?" + params);
    }
    public static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static LocalDateTime[] getDates() {
        LocalDateTime date = LocalDateTime.now();
        LocalDateTime dateMinusOneDay = date.minusDays(1);

        return new LocalDateTime[] {date, dateMinusOneDay};
    }
}
