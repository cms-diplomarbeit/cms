package at.cms.api;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static at.cms.api.AIRequest.urlEncode;

public class AuthenticationClient {
    private static final HttpClient httpClient = HttpClient.newHttpClient();


    public static String fetchOAuth2Token(String clientId, String tokenUrl, String username, String password) {
        String form = String.format(
                "grant_type=password&client_id=%s&username=%s&password=%s",
                urlEncode(clientId),
                urlEncode(username),
                urlEncode(password)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if(resp.statusCode() != 200) {
                throw new RuntimeException("Unexpected response code: " + resp.statusCode());
            }
            JSONObject obj = new JSONObject(resp.body());
            return obj.getString("access_token");
        } catch(Exception e ) {
            e.printStackTrace();
        }
        return null;
    }
}
