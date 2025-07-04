package at.cms.api;

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


public class AIRequest {


    public void Choice(int number) {
        try {
            Choices choice = Choices.values()[number];

            if (choice == Choices.DailyCall) {
                DailyCall();
            } else if (choice == Choices.Comparison) {
                Comparison();
            } else if (choice == Choices.StatusPerTag) {
                StatusPerTag();
            } else {
                throw new ChoiceNotFoundException("Choice not found");
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }


    public void DailyCall() {

    }

    public void Comparison() {

    }

    public void StatusPerTag() {

    }

    public static String KICall(String subscriptions, String measuredTimeFromUtc, String measuredTimeToUtc
            , String valueTypes, int numberOfValues) throws IOException, InterruptedException {
        URI uri = URIBuilder(subscriptions, measuredTimeFromUtc, measuredTimeToUtc, valueTypes, numberOfValues);

        String model = "llama3";
        String userPrompt = String.format("""
        Du bist eine Energieverbrauchs-KI!
        %s

        Bitte berechne basierend auf diesen Daten:
        Min:
        Max:
        Average:
    
        Bitte antworte mir zudem nur mit
    
        Min: (Deine Zahl)
        Max: (Deine Zahl)
        Average: (Deine Zahl)
    
        MEHR NICHT!
    
        Die Daten holst du dir über diesen Link :
        """, uri);

        JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", new JSONArray()
                        .put(new JSONObject()
                                .put("role", "user")
                                .put("content", userPrompt)
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
                "subscriptions=%s&measuredFromUtc=%s&measuredToUtc=%s&valueTypes=%s&numberOfValues=%d",
                urlEncode(subscriptions),
                urlEncode(measuredTimeFromUtc),
                urlEncode(measuredTimeToUtc),
                urlEncode(valueTypes),
                numberOfValues
        );

        String endpoint = "https://cms-server.cms-building.at/graphdata";
        return URI.create(endpoint + "?" + params);
    }
    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
