package com.vivokey.rawnfc;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StorageManager {
    private static final String PREFS_NAME = "rawnfc_data";
    private static final String KEY_SAVED = "saved_commands";
    private static final String KEY_HISTORY = "command_history";
    private static final int HISTORY_CAP = 50;

    private final SharedPreferences prefs;

    public StorageManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<SavedCommand> getSavedCommands() {
        List<SavedCommand> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_SAVED, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                result.add(SavedCommand.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // Return empty list on parse error
        }
        return result;
    }

    public void addSavedCommand(SavedCommand cmd) {
        List<SavedCommand> list = getSavedCommands();
        list.add(cmd);
        saveSavedCommands(list);
    }

    public void deleteSavedCommand(String id) {
        List<SavedCommand> list = getSavedCommands();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        saveSavedCommands(list);
    }

    private void saveSavedCommands(List<SavedCommand> list) {
        JSONArray arr = new JSONArray();
        try {
            for (SavedCommand cmd : list) {
                arr.put(cmd.toJson());
            }
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString(KEY_SAVED, arr.toString()).apply();
    }

    public List<String> getSavedGroups() {
        List<String> groups = new ArrayList<>();
        for (SavedCommand cmd : getSavedCommands()) {
            if (cmd.group != null && !cmd.group.isEmpty() && !groups.contains(cmd.group)) {
                groups.add(cmd.group);
            }
        }
        return groups;
    }

    public List<HistoryEntry> getHistory() {
        List<HistoryEntry> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                result.add(HistoryEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // Return empty list on parse error
        }
        return result;
    }

    public void addHistoryEntry(HistoryEntry entry) {
        List<HistoryEntry> list = getHistory();
        if (!list.isEmpty()) {
            HistoryEntry prev = list.get(0);
            if (prev.protocol.equals(entry.protocol)
                    && Arrays.equals(prev.commands, entry.commands)) {
                return;
            }
        }
        list.add(0, entry);
        while (list.size() > HISTORY_CAP) {
            list.remove(list.size() - 1);
        }
        saveHistory(list);
    }

    public void deleteHistoryEntry(String id) {
        List<HistoryEntry> list = getHistory();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        saveHistory(list);
    }

    private void saveHistory(List<HistoryEntry> list) {
        JSONArray arr = new JSONArray();
        try {
            for (HistoryEntry entry : list) {
                arr.put(entry.toJson());
            }
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }
}
