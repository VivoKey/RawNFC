package com.vivokey.rawnfc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class HistoryEntry {
    public final String id;
    public final String protocol;
    public final String[] commands;
    public final String[] responses;
    public final long timestamp;

    public HistoryEntry(String protocol, String[] commands, String[] responses) {
        this.id = UUID.randomUUID().toString();
        this.protocol = protocol;
        this.commands = commands;
        this.responses = responses;
        this.timestamp = System.currentTimeMillis();
    }

    private HistoryEntry(String id, String protocol, String[] commands, String[] responses, long timestamp) {
        this.id = id;
        this.protocol = protocol;
        this.commands = commands;
        this.responses = responses;
        this.timestamp = timestamp;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("protocol", protocol);
        JSONArray cmds = new JSONArray();
        for (String cmd : commands) cmds.put(cmd);
        obj.put("commands", cmds);
        JSONArray resp = new JSONArray();
        for (String r : responses) resp.put(r);
        obj.put("responses", resp);
        obj.put("timestamp", timestamp);
        return obj;
    }

    public static HistoryEntry fromJson(JSONObject obj) throws JSONException {
        JSONArray cmds = obj.getJSONArray("commands");
        String[] commands = new String[cmds.length()];
        for (int i = 0; i < cmds.length(); i++) commands[i] = cmds.getString(i);

        JSONArray resp = obj.getJSONArray("responses");
        String[] responses = new String[resp.length()];
        for (int i = 0; i < resp.length(); i++) responses[i] = resp.getString(i);

        return new HistoryEntry(
            obj.getString("id"),
            obj.getString("protocol"),
            commands,
            responses,
            obj.getLong("timestamp")
        );
    }
}
