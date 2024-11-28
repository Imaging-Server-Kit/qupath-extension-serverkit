package qupath.ext.pyalgos.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import qupath.lib.plugins.parameters.ParameterList;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
//import  java.util.*;


public class ParametersUtils {

    /**
     * Create a {@link ParameterList} from a {@link List<JsonObject>}
     *
     * @param parameters The List containing json objects with the following keys: name, display_name,
     *                   default_value, type (bool, int, float, string or list), unit (opt), and description (opt).
     * @param title      A title for the given parameters (opt)
     * @return The filled {@link ParameterList}
     */
    public static ParameterList createParameterList(JsonObject parameters, String title) {
//    public static ParameterList createParameterList(List<JsonObject> parameters, String title) {
        ParameterList params = new ParameterList();
        if (parameters == null || parameters.isEmpty()) {
            return params;
        }
        if (title != null) {
            params.addTitleParameter(title);
        }

        for (Map.Entry<String, JsonElement> entry : parameters.entrySet()) {
            String key = entry.getKey();
            JsonObject parameterValues = entry.getValue().getAsJsonObject();
            String prompt = parameterValues.get("title").getAsString();
            String description = parameterValues.get("description") != null ? parameterValues.get("description").getAsString() : null;
            JsonElement defaultValue = parameterValues.get("default") != null ? parameterValues.get("default") : null;
            String unit = "[unit]";

            switch (parameterValues.get("widget_type").getAsString()) {
                case "bool":
                    params.addBooleanParameter(key, prompt, defaultValue.getAsBoolean(), description);
                    break;
                case "int":
                    params.addIntParameter(key, prompt, defaultValue.getAsInt(), unit, description);
                    break;
                case "float":
                    params.addDoubleParameter(key, prompt, defaultValue.getAsDouble(), unit, description);
                    break;
                case "str":
                    params.addStringParameter(key, prompt, defaultValue.getAsString(), description);
                    break;
                case "dropdown":
                    JsonArray choicesArray = parameterValues.get("enum").getAsJsonArray();
                    String[] choices = new String[choicesArray.size()];
                    for (int i = 0; i < choicesArray.size(); i++) {
                        choices[i] = choicesArray.get(i).getAsString();
                    }
                    params.addChoiceParameter(key, prompt, choices[0], Arrays.stream(choices).toList(), description);
                    break;
                case "image":
                    break;
                case "labels":
                    break;
                case "points":
                    break;
                case "shapes":
                    break;
                case "vectors":
                    break;
                case "tracks":
                    break;
            }
        }

//        for (JsonObject p : parameters) {
//            String key = p.get("name").getAsString();
//            String prompt = p.get("display_name").getAsString();
//            JsonElement defaultValue = p.get("default_value");
//            String unit = p.get("unit") != null ? p.get("unit").getAsString() : null;
//            String description = p.get("description") != null ? p.get("description").getAsString() : null;
//            switch (p.get("type").getAsString()) {
//                case "bool":
//                    params.addBooleanParameter(key, prompt, defaultValue.getAsBoolean(), description);
//                    break;
//                case "int":
//                    params.addIntParameter(key, prompt, defaultValue.getAsInt(), unit, description);
//                    break;
//                case "float":
//                    params.addDoubleParameter(key, prompt, defaultValue.getAsDouble(), unit, description);
//                    break;
//                case "string":
//                    params.addStringParameter(key, prompt, defaultValue.getAsString(), description);
//                    break;
//                case "list":
//                    JsonArray choicesArray = p.get("values").getAsJsonArray();
//                    String[] choices = new String[choicesArray.size()];
//                    for (int i = 0; i < choicesArray.size(); i++) {
//                        choices[i] = choicesArray.get(i).getAsString();
//                    }
//                    params.addChoiceParameter(key, prompt, choices[0], Arrays.stream(choices).toList(), description);
//                    break;
//            }
//        }

        return params;
    }
}
