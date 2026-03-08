package com.vivokey.rawnfc;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class SaveDialogHelper {

    public interface OnSaveListener {
        void onSave(String label, String group);
    }

    public static void show(Context context, String defaultLabel, List<String> existingGroups, OnSaveListener listener) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(context, 16);
        layout.setPadding(pad, pad, pad, 0);

        TextView labelLabel = new TextView(context);
        labelLabel.setText("Label");
        labelLabel.setTextColor(0xFF69848F);
        layout.addView(labelLabel);

        EditText labelInput = new EditText(context);
        labelInput.setText(defaultLabel);
        labelInput.setSelectAllOnFocus(true);
        labelInput.setInputType(InputType.TYPE_CLASS_TEXT);
        labelInput.setTextColor(0xFFFFFFFF);
        labelInput.setBackgroundColor(0xFF050708);
        int inputPad = dpToPx(context, 8);
        labelInput.setPadding(inputPad, inputPad, inputPad, inputPad);
        layout.addView(labelInput);

        TextView groupLabel = new TextView(context);
        groupLabel.setText("Group");
        groupLabel.setTextColor(0xFF69848F);
        LinearLayout.LayoutParams groupLabelParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        groupLabelParams.topMargin = dpToPx(context, 12);
        groupLabel.setLayoutParams(groupLabelParams);
        layout.addView(groupLabel);

        List<String> spinnerItems = new ArrayList<>();
        spinnerItems.add("(No group)");
        spinnerItems.addAll(existingGroups);
        spinnerItems.add("New group...");

        Spinner groupSpinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, spinnerItems);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        groupSpinner.setAdapter(adapter);
        layout.addView(groupSpinner);

        EditText newGroupInput = new EditText(context);
        newGroupInput.setHint("Group name");
        newGroupInput.setInputType(InputType.TYPE_CLASS_TEXT);
        newGroupInput.setTextColor(0xFFFFFFFF);
        newGroupInput.setHintTextColor(0xFF69848F);
        newGroupInput.setBackgroundColor(0xFF050708);
        newGroupInput.setPadding(inputPad, inputPad, inputPad, inputPad);
        newGroupInput.setVisibility(android.view.View.GONE);
        layout.addView(newGroupInput);

        groupSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position == spinnerItems.size() - 1) {
                    newGroupInput.setVisibility(android.view.View.VISIBLE);
                    newGroupInput.requestFocus();
                } else {
                    newGroupInput.setVisibility(android.view.View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        new AlertDialog.Builder(context)
            .setTitle("Save Command")
            .setView(layout)
            .setPositiveButton("Save", (dialog, which) -> {
                String label = labelInput.getText().toString().trim();
                if (label.isEmpty()) label = defaultLabel;

                String group = null;
                int selectedPos = groupSpinner.getSelectedItemPosition();
                if (selectedPos == spinnerItems.size() - 1) {
                    String newGroup = newGroupInput.getText().toString().trim();
                    if (!newGroup.isEmpty()) group = newGroup;
                } else if (selectedPos > 0) {
                    group = spinnerItems.get(selectedPos);
                }

                listener.onSave(label, group);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private static int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
