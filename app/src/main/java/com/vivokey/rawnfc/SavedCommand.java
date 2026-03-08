package com.vivokey.rawnfc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class SavedCommand {
    public final String id;
    public final String label;
    public final String protocol;
    public final String group;
    public final String[] commands;
    public final long createdAt;

    public SavedCommand(String label, String protocol, String group, String[] commands) {
        this.id = UUID.randomUUID().toString();
        this.label = label;
        this.protocol = protocol;
        this.group = group;
        this.commands = commands;
        this.createdAt = System.currentTimeMillis();
    }

    private SavedCommand(String id, String label, String protocol, String group, String[] commands, long createdAt) {
        this.id = id;
        this.label = label;
        this.protocol = protocol;
        this.group = group;
        this.commands = commands;
        this.createdAt = createdAt;
    }

    public boolean isSet() {
        return commands.length > 1;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("label", label);
        obj.put("protocol", protocol);
        obj.put("group", group);
        JSONArray cmds = new JSONArray();
        for (String cmd : commands) {
            cmds.put(cmd);
        }
        obj.put("commands", cmds);
        obj.put("createdAt", createdAt);
        return obj;
    }

    public static SavedCommand fromJson(JSONObject obj) throws JSONException {
        JSONArray cmds = obj.getJSONArray("commands");
        String[] commands = new String[cmds.length()];
        for (int i = 0; i < cmds.length(); i++) {
            commands[i] = cmds.getString(i);
        }
        return new SavedCommand(
            obj.getString("id"),
            obj.getString("label"),
            obj.getString("protocol"),
            obj.optString("group", null),
            commands,
            obj.getLong("createdAt")
        );
    }
}
