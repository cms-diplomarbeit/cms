package at.cms.api;

import lombok.Data;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
public class EnergyStats {

    public static class Stats {
        public final double min;
        public final double max;
        public final double avg;

        public Stats(double min, double max, double avg) {
            this.min = min;
            this.max = max;
            this.avg = avg;
        }

        @Override
        public String toString() {
            return "Stats{" +
                    "min=" + min +
                    ", max=" + max +
                    ", avg=" + avg +
                    '}';
        }
    }

    public static Stats computedStats(String graphJson) {
        JSONObject root = new JSONObject(graphJson);
        JSONArray subs = root
                .getJSONArray("subscriptionData")
                .getJSONObject(0)
                .getJSONArray("values");

        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        int count = 0;

        for (int i = 0; i < subs.length(); i++) {
            JSONObject entry = subs.getJSONObject(i);
            if (!entry.has("average")) continue;  // überspringe leere Objekte
            double avg = entry.getDouble("average");
            sum += avg;
            min = Math.min(min, avg);
            max = Math.max(max, avg);
            count++;
        }
        double average = (count > 0) ? sum / count : 0;
        min = round(min);
        max = round(max);
        average = round(average);
        return new Stats(min, max, average);
    }

    public static double round(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP)
                .doubleValue();
    }

}
