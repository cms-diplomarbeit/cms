package at.cms.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TagSelector {

    public static Map<String, List<Integer>> mapTranslationsToSubs(JSONArray jsonArray) {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            String translation = obj.getString("translation");
            int subId        = obj.getInt("opcUaSubscriptionId");

            map.computeIfAbsent(translation, k -> new ArrayList<>())
                    .add(subId);
        }
        return map;
    }
}
