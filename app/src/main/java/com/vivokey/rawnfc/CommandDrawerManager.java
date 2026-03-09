package com.vivokey.rawnfc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommandDrawerManager {

    public interface OnCommandSelectedListener {
        void onCommandSelected(String protocol, String[] commands, boolean append);
    }

    private final Context context;
    private final StorageManager storage;
    private final LinearLayout filterChipsContainer;
    private final TextView savedHeader;
    private final LinearLayout savedList;
    private final TextView historyHeader;
    private final LinearLayout historyList;
    private OnCommandSelectedListener listener;

    private boolean savedExpanded = true;
    private boolean historyExpanded = true;
    private String protocolFilter = null;

    public CommandDrawerManager(Context context, View drawerPanel, StorageManager storage) {
        this.context = context;
        this.storage = storage;
        this.filterChipsContainer = drawerPanel.findViewById(R.id.filter_chips);
        this.savedHeader = drawerPanel.findViewById(R.id.saved_header);
        this.savedList = drawerPanel.findViewById(R.id.saved_list);
        this.historyHeader = drawerPanel.findViewById(R.id.history_header);
        this.historyList = drawerPanel.findViewById(R.id.history_list);

        savedHeader.setOnClickListener(v -> {
            savedExpanded = !savedExpanded;
            savedHeader.setText(savedExpanded ? "▾ SAVED" : "▸ SAVED");
            savedList.setVisibility(savedExpanded ? View.VISIBLE : View.GONE);
        });

        historyHeader.setOnClickListener(v -> {
            historyExpanded = !historyExpanded;
            historyHeader.setText(historyExpanded ? "▾ HISTORY" : "▸ HISTORY");
            historyList.setVisibility(historyExpanded ? View.VISIBLE : View.GONE);
        });
    }

    public void setOnCommandSelectedListener(OnCommandSelectedListener listener) {
        this.listener = listener;
    }

    public void refresh() {
        buildFilterChips();
        refreshSaved();
        refreshHistory();
    }

    private void buildFilterChips() {
        filterChipsContainer.removeAllViews();

        List<String> protocols = new ArrayList<>();
        protocols.add("All");
        for (SavedCommand cmd : storage.getSavedCommands()) {
            if (!protocols.contains(cmd.protocol)) protocols.add(cmd.protocol);
        }
        for (HistoryEntry entry : storage.getHistory()) {
            if (!protocols.contains(entry.protocol)) protocols.add(entry.protocol);
        }

        for (String proto : protocols) {
            TextView chip = createChip(proto);
            boolean isSelected = (proto.equals("All") && protocolFilter == null)
                || proto.equals(protocolFilter);
            styleChip(chip, isSelected);

            chip.setOnClickListener(v -> {
                protocolFilter = proto.equals("All") ? null : proto;
                refresh();
            });

            filterChipsContainer.addView(chip);
        }
    }

    private TextView createChip(String text) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        int hPad = dpToPx(10);
        int vPad = dpToPx(6);
        chip.setPadding(hPad, vPad, hPad, vPad);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dpToPx(4), 0, dpToPx(4), 0);
        chip.setLayoutParams(params);
        return chip;
    }

    private void styleChip(TextView chip, boolean selected) {
        if (selected) {
            chip.setBackgroundColor(context.getColor(R.color.chip_selected));
            chip.setTextColor(0xFFFFFFFF);
        } else {
            chip.setBackgroundColor(context.getColor(R.color.chip_unselected));
            chip.setTextColor(0xFF69848F);
        }
    }

    private void refreshSaved() {
        savedList.removeAllViews();
        List<SavedCommand> commands = storage.getSavedCommands();

        // Group by protocol, then by group within protocol
        Map<String, Map<String, List<SavedCommand>>> byProtocol = new LinkedHashMap<>();
        for (SavedCommand cmd : commands) {
            if (protocolFilter != null && !cmd.protocol.equals(protocolFilter)) continue;
            String proto = cmd.protocol;
            byProtocol.computeIfAbsent(proto, k -> new LinkedHashMap<>());
            String group = cmd.group != null ? cmd.group : "";
            byProtocol.get(proto).computeIfAbsent(group, k -> new ArrayList<>()).add(cmd);
        }

        if (byProtocol.isEmpty()) {
            savedList.addView(createEmptyView("No saved commands"));
            return;
        }

        for (Map.Entry<String, Map<String, List<SavedCommand>>> protoEntry : byProtocol.entrySet()) {
            // Protocol section header
            savedList.addView(createProtocolHeader(protoEntry.getKey()));

            for (Map.Entry<String, List<SavedCommand>> groupEntry : protoEntry.getValue().entrySet()) {
                String groupName = groupEntry.getKey();
                if (!groupName.isEmpty()) {
                    savedList.addView(createGroupHeader(groupName));
                }

                for (SavedCommand cmd : groupEntry.getValue()) {
                    savedList.addView(createSavedItemView(cmd, !groupName.isEmpty()));
                }
            }
        }
    }

    private void refreshHistory() {
        historyList.removeAllViews();
        List<HistoryEntry> history = storage.getHistory();

        boolean hasItems = false;
        for (HistoryEntry entry : history) {
            if (protocolFilter != null && !entry.protocol.equals(protocolFilter)) continue;
            historyList.addView(createHistoryItemView(entry));
            hasItems = true;
        }

        if (!hasItems) {
            historyList.addView(createEmptyView("No history"));
        }
    }

    private View createProtocolHeader(String protocol) {
        TextView tv = new TextView(context);
        tv.setText(protocol);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTextColor(0xFF6493B3);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        int pad = dpToPx(8);
        tv.setPadding(dpToPx(12), pad, pad, dpToPx(4));
        return tv;
    }

    private View createGroupHeader(String group) {
        TextView tv = new TextView(context);
        tv.setText("⌐ " + group);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTextColor(0xFF69848F);
        int pad = dpToPx(4);
        tv.setPadding(dpToPx(20), pad, pad, pad);
        return tv;
    }

    private View createSavedItemView(SavedCommand cmd, boolean indented) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int leftPad = dpToPx(indented ? 28 : 16);
        int vPad = dpToPx(10);
        row.setPadding(leftPad, vPad, dpToPx(12), vPad);
        row.setBackgroundColor(context.getColor(R.color.drawer_item_bg));

        // Set icon indicator
        TextView icon = new TextView(context);
        icon.setText(cmd.isSet() ? "☰ " : "▪ ");
        icon.setTextColor(0xFF69848F);
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.addView(icon);

        // Label and command count
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(textParams);

        TextView label = new TextView(context);
        label.setText(cmd.label);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setMaxLines(1);
        textCol.addView(label);

        if (cmd.isSet()) {
            TextView count = new TextView(context);
            count.setText(cmd.commands.length + " commands");
            count.setTextColor(0xFF69848F);
            count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            textCol.addView(count);
        }

        row.addView(textCol);

        // Tap to replace
        row.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCommandSelected(cmd.protocol, cmd.commands, false);
            }
        });

        // Long-press to append
        row.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onCommandSelected(cmd.protocol, cmd.commands, true);
            }
            return true;
        });

        // Bottom divider
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row);
        View divider = new View(context);
        divider.setBackgroundColor(0xFF2A3439);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1));
        wrapper.addView(divider);

        return wrapper;
    }

    private View createHistoryItemView(HistoryEntry entry) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dpToPx(12);
        int vPad = dpToPx(10);
        row.setPadding(dpToPx(16), vPad, pad, vPad);
        row.setBackgroundColor(context.getColor(R.color.drawer_item_bg));

        // Text column
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(textParams);

        // Relative time
        TextView timeView = new TextView(context);
        CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
            entry.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        timeView.setText(relativeTime);
        timeView.setTextColor(0xFFFFFFFF);
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        textCol.addView(timeView);

        // Protocol + command count
        TextView details = new TextView(context);
        details.setText(entry.protocol + " · " + entry.commands.length + " cmd" + (entry.commands.length > 1 ? "s" : ""));
        details.setTextColor(0xFF69848F);
        details.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        textCol.addView(details);

        row.addView(textCol);

        // Tap to replace
        row.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCommandSelected(entry.protocol, entry.commands, false);
            }
        });

        // Long-press to append
        row.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onCommandSelected(entry.protocol, entry.commands, true);
            }
            return true;
        });

        // Bottom divider
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row);
        View divider = new View(context);
        divider.setBackgroundColor(0xFF2A3439);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1));
        wrapper.addView(divider);

        return wrapper;
    }

    private View createEmptyView(String message) {
        TextView tv = new TextView(context);
        tv.setText(message);
        tv.setTextColor(0xFF69848F);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setGravity(Gravity.CENTER);
        int pad = dpToPx(16);
        tv.setPadding(pad, pad, pad, pad);
        return tv;
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
