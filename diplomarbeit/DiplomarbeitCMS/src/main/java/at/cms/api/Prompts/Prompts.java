package at.cms.api.Prompts;

import java.net.URI;

public class Prompts {

    private static final String DAILY_CALL_TEMPLATE = """
        Du bist eine Energieverbrauchs-KI! Ich bin dein Kunde
        
        Du kriegst nun von mir Average, Min und Max.
        
        Bitte interpretiere das Ergebnis.
        
        %s
        
        Antworte auf Deutsch!
        """;

    private static final String COMPARISON_CALL_TEMPLATE = """
            Du bist eine Energieverbrauchs-KI! Ich bin dein Kunde
            %s
            Du kriegst folgende Daten von zwei Zeiträumen:
            Min:
            Max:
            Average:
            
            Bitte antworte mir zudem mit...vom ersten Datensatz:
            Min: (Die Daten die du mitbekommen hast)
            Max: (Die Daten die du mitbekommen hast)
            Average: (Die Daten die du mitbekommen hast)
            
            Und dann antworte mir zudem mit...vom zweiten Datensatz:
            Min: (Die Daten die du mitbekommen hast)
            Max: (Die Daten die du mitbekommen hast)
            Average: (Die Daten die du mitbekommen hast)
            
            Danach analysiere diese Daten gegeneinander, vergleiche Sie miteinander und interpretiere die Daten.
            Diese Daten sind zu deiner Information von meinem Haus, also nimm das mit in dein Context
            Bitte auf Deutsch antworten
            DIe Daten holst du dir über folgende zwei Links:
            """;

    public static String getDailyCallPrompt(String json) {
        // hier liefert String.format genau einen String-Parameter für das eine %s
        return String.format(DAILY_CALL_TEMPLATE, json);
    }

    public static String getComparisonCallPrompt(URI dataLink1, URI dataLink2) {
        return String.format(COMPARISON_CALL_TEMPLATE, dataLink1, dataLink2);
    }
}
