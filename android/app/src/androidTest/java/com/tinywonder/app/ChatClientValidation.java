package com.tinywonder.app;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.json.JSONArray;
import org.json.JSONObject;

/** Boundary regressions; uses synthetic text and never calls the network or a model. */
final class ChatClientValidation {
    static void run() throws Exception {
        JSONObject input = new JSONObject().put("messages", new JSONArray().put(
            new JSONObject().put("role", "user").put("content", "I feel tired after class.")
                .put("privateJournal", "must not travel")))
            .put("mood", 2).put("energy", 1).put("photos", new JSONArray().put("private"))
            .put("circleToken", "must-not-travel").put("systemPrompt", "untrusted instructions");
        JSONObject cleaned = invoke("sanitizeRequest", String.class, input.toString());
        require(cleaned.length() == 3 && !cleaned.has("circleToken") && !cleaned.has("photos")
            && !cleaned.has("systemPrompt"), "unapproved input metadata crossed the boundary");
        require(cleaned.getJSONArray("messages").getJSONObject(0).length() == 2, "message metadata crossed the boundary");

        JSONObject oversized = new JSONObject(input.toString());
        oversized.getJSONArray("messages").getJSONObject(0).put("content", new String(new char[2001]).replace('\0', 'x'));
        rejects("sanitizeRequest", String.class, oversized.toString());
        JSONObject wrongRole = new JSONObject(input.toString());
        wrongRole.getJSONArray("messages").getJSONObject(0).put("role", "system");
        rejects("sanitizeRequest", String.class, wrongRole.toString());
        JSONObject fraction = new JSONObject(input.toString()).put("mood", 2.5);
        rejects("sanitizeRequest", String.class, fraction.toString());

        JSONObject urgent = new JSONObject().put("reply", "Please seek immediate human support.")
            .put("summary", "You shared an immediate danger.").put("suggestedMood", 1)
            .put("suggestedEnergy", 1).put("suggestedQuestId", "outside").put("urgentSupport", true)
            .put("token", "must-not-travel").put("debug", "must-not-travel");
        JSONObject reply = invoke("sanitizeResponse", JSONObject.class, urgent);
        require(reply.length() == 6 && !reply.has("token") && !reply.has("debug"), "server metadata crossed the boundary");
        require(reply.isNull("suggestedMood") && reply.isNull("suggestedEnergy") && reply.isNull("suggestedQuestId"),
            "urgent support retained ratings or a quest");
        rejects("sanitizeResponse", JSONObject.class, new JSONObject(urgent.toString()).put("urgentSupport", "true"));
        rejects("sanitizeResponse", JSONObject.class, new JSONObject(urgent.toString()).put("suggestedQuestId", "invented"));

        JSONObject profile = new JSONObject().put("summary", "I like drawing and quiet breaks.")
            .put("preferences", new JSONArray().put("drawing")).put("avoid", new JSONArray().put("crowds"))
            .put("moodContext", "Busy week").put("energyStyle", "Low effort")
            .put("systemPrompt", "must not travel as instructions").put("circleToken", "must not travel");
        JSONObject personalizedChat = invoke("sanitizeRequest", String.class, new JSONObject(input.toString()).put("profile", profile).toString());
        require(personalizedChat.getJSONObject("profile").length() == 5, "profile metadata crossed the boundary");
        JSONObject reflection = invoke("sanitizeProfileRequest", String.class,
            new JSONObject().put("text", "I like sketching.").put("privateJournal", "must not travel").toString());
        require(reflection.length() == 1 && reflection.getString("text").equals("I like sketching."), "profile request included unrelated data");
        rejects("sanitizeProfileRequest", String.class, new JSONObject().put("text", new String(new char[12001]).replace('\0', 'x')).toString());

        JSONObject questRequest = new JSONObject().put("profile", profile).put("history", new JSONArray().put(
            new JSONObject().put("title", "Notice a leaf").put("action", "Notice the shape of one leaf.")
                .put("activityKey", "notice-leaf").put("id", "local-only-id").put("reflection", "private reflection")))
            .put("mood", JSONObject.NULL).put("energy", 1).put("contacts", new JSONArray().put("private contact"));
        JSONObject questInput = invoke("sanitizeQuestsRequest", String.class, questRequest.toString());
        require(questInput.length() == 4 && questInput.getJSONObject("profile").length() == 5
            && questInput.getJSONArray("history").getJSONObject(0).length() == 3, "quest history included unrelated data");
        JSONObject generated = new JSONObject().put("id", "generated-0123456789abcdef0123456789abcdef")
            .put("title", "Sketch a leaf").put("action", "Draw the outline of a leaf for one minute.")
            .put("mechanism", "strengths").put("minutes", 1).put("activityKey", "draw-leaf")
            .put("principle", "Character strengths").put("icon", "spark").put("color", "yellow")
            .put("why", "An optional activity inspired by using strengths.")
            .put("evidence", "https://doi.org/10.1037/0003-066X.60.5.410").put("token", "must not travel");
        JSONObject questOutput = new JSONObject().put("quests", new JSONArray().put(generated))
            .put("duplicatesFiltered", 1).put("exhausted", true).put("debug", "must not travel");
        JSONObject quests = invoke("sanitizeQuestsResponse", JSONObject.class, questOutput);
        require(quests.length() == 3 && !quests.getJSONArray("quests").getJSONObject(0).has("token"), "quest response leaked metadata");
        generated.put("evidence", "javascript:alert(1)");
        rejects("sanitizeQuestsResponse", JSONObject.class, questOutput);
    }

    private static JSONObject invoke(String name, Class<?> parameter, Object value) throws Exception {
        Method method = ChatClient.class.getDeclaredMethod(name, parameter);
        method.setAccessible(true);
        return (JSONObject) method.invoke(null, value);
    }

    private static void rejects(String name, Class<?> parameter, Object value) throws Exception {
        try { invoke(name, parameter, value); }
        catch (InvocationTargetException expected) {
            require("ChatException".equals(expected.getCause().getClass().getSimpleName()), "unexpected validation failure");
            return;
        }
        throw new AssertionError("invalid boundary input was accepted: " + name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
