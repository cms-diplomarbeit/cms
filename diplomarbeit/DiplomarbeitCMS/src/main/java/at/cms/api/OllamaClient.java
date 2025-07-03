package at.cms.api;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class OllamaClient {
    public static void main(String[] args) throws Exception {
        String model = "llama3";  // oder dein exakter Modellname
        // 1) JSON-Daten mit Verbrauchswerten für zwei Tage
        String jsonData = """
                [
                  { "timestamp": "2025-07-01T00:00:00", "value": 120 },
                  { "timestamp": "2025-07-01T01:00:00", "value": 115 },
                  { "timestamp": "2025-07-01T02:00:00", "value": 130 },
                  { "timestamp": "2025-07-01T03:00:00", "value": 125 },
                  { "timestamp": "2025-07-01T04:00:00", "value": 110 },
                  { "timestamp": "2025-07-01T05:00:00", "value": 100 },
                  { "timestamp": "2025-07-01T06:00:00", "value": 140 },
                  { "timestamp": "2025-07-01T07:00:00", "value": 160 },
                  { "timestamp": "2025-07-01T08:00:00", "value": 180 },
                  { "timestamp": "2025-07-01T09:00:00", "value": 175 },
                  { "timestamp": "2025-07-01T10:00:00", "value": 190 },
                  { "timestamp": "2025-07-01T11:00:00", "value": 200 },
                  { "timestamp": "2025-07-01T12:00:00", "value": 210 },
                  { "timestamp": "2025-07-01T13:00:00", "value": 205 },
                  { "timestamp": "2025-07-01T14:00:00", "value": 195 },
                  { "timestamp": "2025-07-01T15:00:00", "value": 185 },
                  { "timestamp": "2025-07-01T16:00:00", "value": 170 },
                  { "timestamp": "2025-07-01T17:00:00", "value": 160 },
                  { "timestamp": "2025-07-01T18:00:00", "value": 150 },
                  { "timestamp": "2025-07-01T19:00:00", "value": 145 },
                  { "timestamp": "2025-07-01T20:00:00", "value": 155 },
                  { "timestamp": "2025-07-01T21:00:00", "value": 165 },
                  { "timestamp": "2025-07-01T22:00:00", "value": 155 },
                  { "timestamp": "2025-07-01T23:00:00", "value": 140 }
                ]
        """;

        // 2) Prompt, der das Modell anweist, beide Tage zu vergleichen
        String userPrompt = String.format("""
    Du bist eine Energieverbrauchs-KI! Hier sind die Verbrauchsdaten für einen Tag:
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
    """, jsonData);

        // 3) JSON-Payload bauen
        JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", new JSONArray()
                        .put(new JSONObject()
                                .put("role", "user")
                                .put("content", userPrompt)
                        )
                );

        // 4) HTTP-Request abschicken
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://file1.lan.elite-zettl.at:11434/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // 5) Antwort parsen und ausgeben
        if (response.statusCode() != 200) {
            System.err.println("Fehler: " + response.body());
            return;
        }
        JSONObject json = new JSONObject(response.body());
        String content = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");

        System.out.println(">> KI Antwortet: \n" + content);
    }
}
