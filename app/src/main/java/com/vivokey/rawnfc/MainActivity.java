package com.vivokey.rawnfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.drawerlayout.widget.DrawerLayout;

import org.apache.commons.codec.binary.Hex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity implements NfcAdapter.ReaderCallback, NfcEditText.OnCommandActionListener {

    private static final String[] BASE_TECHNOLOGIES = {
        "NFC-A (ISO 14443-3A)",
        "NFC-B (ISO 14443-3B)",
        "NFC-F (JIS 6319-4)",
        "NFC-V (ISO 15693)",
        "ISO-DEP (ISO 14443-4)"
    };
    private static final String[] MIFARE_TECHNOLOGIES = {
        "MIFARE Classic",
        "MIFARE Ultralight"
    };
    private static final String[] TECH_CLASS_SIMPLE_NAMES = {
        "NfcA",
        "NfcB",
        "NfcF",
        "NfcV",
        "IsoDep",
        "MifareClassic",
        "MifareUltralight"
    };

    private static final int MENU_ID_TECH = 1;
    private static final int MENU_ID_SAVE_ALL = 2;

    private List<String> technologies;
    private Integer selectedTechnology;
    private NfcEditText inputView;
    private EditText outputView;
    private DrawerLayout drawerLayout;
    private StorageManager storageManager;
    private CommandDrawerManager drawerManager;
    private TagTechnology connectedTag;
    private String connectedTagUid;
    private List<String> lastTxCommands;
    private ToneGenerator toneGenerator;
    private View connectionIndicator;
    private final Handler connectionCheckHandler = new Handler(Looper.getMainLooper());
    private static final long CONNECTION_CHECK_INTERVAL_MS = 500;
    private MenuItem saveAllMenuItem;
    private final Handler saveAllDebounceHandler = new Handler(Looper.getMainLooper());
    private static final long SAVE_ALL_DEBOUNCE_MS = 500;

    // NFC-V header shorthand
    private ImageView nfcvCalcButton;
    private boolean nfcvHeaderEnabled = false;
    private boolean nfcvAddr = false, nfcvOpt = false, nfcvInv = false;
    private boolean nfcvHi = true, nfcvDual = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setupWindowInsets();

        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeAsUpIndicator(R.drawable.ic_vivokey_logo);
        getActionBar().setDisplayShowTitleEnabled(false);
        getActionBar().setDisplayShowCustomEnabled(true);

        View customView = LayoutInflater.from(this).inflate(R.layout.action_bar_title, null);
        TextView titleView = customView.findViewById(R.id.action_bar_title);
        TextView versionView = customView.findViewById(R.id.action_bar_version);
        versionView.setText("v" + BuildConfig.VERSION_NAME);
        titleView.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://vivokey.com/rawnfc"));
            startActivity(browserIntent);
        });
        getActionBar().setCustomView(customView);

        inputView = findViewById(R.id.input);
        outputView = findViewById(R.id.output);
        connectionIndicator = findViewById(R.id.connection_indicator);
        inputView.addTextChangedListener(new HexTextWatcher());
        inputView.setOnCommandActionListener(this);
        inputView.setShowActionIcons(true);

        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50);

        outputView.setOnLongClickListener(v -> {
            shareDump();
            return true;
        });

        // Setup drawer
        drawerLayout = findViewById(R.id.drawer_layout);
        storageManager = new StorageManager(this);
        View drawerPanel = findViewById(R.id.drawer_panel);
        drawerManager = new CommandDrawerManager(this, drawerPanel, storageManager);
        drawerManager.setOnCommandSelectedListener((protocol, commands, append) -> {
            runOnUiThread(() -> loadCommands(protocol, commands, append));
        });

        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                drawerManager.refresh();
            }
        });

        // NFC-V header calculator button
        nfcvCalcButton = findViewById(R.id.nfcv_calc_button);
        nfcvCalcButton.setOnClickListener(v -> showNfcvHeaderDialog());

        // Share deep link button
        findViewById(R.id.share_link_button).setOnClickListener(v -> copyDeepLink());

        initializeTechnologies();

        handleIntent(getIntent());

        if (selectedTechnology == null) {
            showTechnologySelectionDialog();
        }

        enableNfcReaderMode();
    }

    private void loadCommands(String protocol, String[] commands, boolean append) {
        // Find and select the matching protocol
        for (int i = 0; i < technologies.size(); i++) {
            if (technologies.get(i).equals(protocol)) {
                selectedTechnology = i;
                invalidateOptionsMenu();
                updateNfcvConfigVisibility();
                break;
            }
        }

        String commandText = String.join("\n", commands);
        if (append) {
            String existing = inputView.getText().toString();
            if (!existing.isEmpty() && !existing.endsWith("\n")) {
                inputView.append("\n");
            }
            inputView.append(commandText);
        } else {
            inputView.setText(commandText);
        }

        if (!append) {
            drawerLayout.closeDrawers();
        }
    }

    // OnCommandActionListener implementation
    @Override
    public void onSave(int line, String hex) {
        if (!isCompleteHex(hex)) {
            Toast.makeText(this, "Incomplete byte — finish typing first", Toast.LENGTH_SHORT).show();
            return;
        }
        String protocol = selectedTechnology != null ? technologies.get(selectedTechnology) : "Unknown";
        String defaultLabel = hex.length() > 20 ? hex.substring(0, 20) + "..." : hex;
        List<String> groups = storageManager.getSavedGroups();

        SaveDialogHelper.show(this, defaultLabel, groups, (label, group) -> {
            SavedCommand cmd = new SavedCommand(label, protocol, group, new String[]{hex});
            storageManager.addSavedCommand(cmd);
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onReplay(int line, String hex) {
        boolean connected = false;
        try {
            connected = connectedTag != null && connectedTag.isConnected();
        } catch (SecurityException e) {
            connectedTag = null;
            connectedTagUid = null;
        }
        if (!connected) {
            setConnected(false);
            Toast.makeText(this, "No tag connected — tap to connect first", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                byte[] commandBytes = hexStringToByteArray(hex);
                if (isNfcVSelected()) {
                    commandBytes = prepareNfcVCommand(commandBytes, connectedTag.getTag());
                }
                byte[] response = transceive(connectedTag, commandBytes);
                String responseHex = formatHexWithSpaces(Hex.encodeHexString(response, false));
                runOnUiThread(() -> {
                    appendReplayOutputText(responseHex);
                    playNfcSound();
                });
            } catch (IOException e) {
                connectedTag = null;
                connectedTagUid = null;
                setConnected(false);
                runOnUiThread(() -> {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 100);
                    Toast.makeText(this, "Tag lost: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void saveAllCommands() {
        String input = getInputText().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "No commands to save", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] commands = input.split("\\r?\\n");
        List<String> nonEmpty = new ArrayList<>();
        for (String cmd : commands) {
            if (!cmd.trim().isEmpty()) nonEmpty.add(cmd.trim());
        }
        if (nonEmpty.isEmpty()) return;

        String protocol = selectedTechnology != null ? technologies.get(selectedTechnology) : "Unknown";
        String firstCmd = nonEmpty.get(0);
        String defaultLabel = firstCmd.length() > 20 ? firstCmd.substring(0, 20) + "..." : firstCmd;
        if (nonEmpty.size() > 1) {
            defaultLabel += " (" + nonEmpty.size() + " cmds)";
        }
        List<String> groups = storageManager.getSavedGroups();

        String finalDefaultLabel = defaultLabel;
        SaveDialogHelper.show(this, finalDefaultLabel, groups, (label, group) -> {
            SavedCommand cmd = new SavedCommand(label, protocol, group, nonEmpty.toArray(new String[0]));
            storageManager.addSavedCommand(cmd);
            Toast.makeText(this, "Set saved", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupWindowInsets() {
        LinearLayout rootLayout = findViewById(R.id.root_layout);
        final int basePadding = rootLayout.getPaddingTop();

        rootLayout.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset;
            int bottomInset;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                int systemBars = WindowInsets.Type.systemBars();
                int ime = WindowInsets.Type.ime();
                topInset = insets.getInsets(systemBars).top;
                bottomInset = Math.max(insets.getInsets(systemBars).bottom, insets.getInsets(ime).bottom);
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }

            v.setPadding(
                v.getPaddingLeft(),
                basePadding + topInset,
                v.getPaddingRight(),
                basePadding + bottomInset
            );

            return insets;
        });

        rootLayout.requestApplyInsets();
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            Uri uri = intent.getData();
            if ("rawnfc".equals(uri.getScheme())) {
                int techIndex = Arrays.asList(TECH_CLASS_SIMPLE_NAMES).indexOf(uri.getHost());
                if (techIndex != -1) {
                    selectedTechnology = techIndex;
                }
                String path = uri.getPath();
                if (path != null) {
                    String[] pathParts = path.split("/+");
                    for (int i = 1; i < pathParts.length; i++) {
                        inputView.append(i < pathParts.length - 1 ? pathParts[i] + "\n" : pathParts[i]);
                    }
                }

                // NFC-V header shorthand via query params
                if ("NfcV".equals(uri.getHost()) && "1".equals(uri.getQueryParameter("vheader"))) {
                    nfcvHeaderEnabled = true;
                    nfcvAddr = "1".equals(uri.getQueryParameter("addr"));
                    nfcvOpt = "1".equals(uri.getQueryParameter("opt"));
                    nfcvInv = "1".equals(uri.getQueryParameter("inv"));
                    nfcvDual = "1".equals(uri.getQueryParameter("dual"));
                    // Hi defaults to true unless explicitly set to 0
                    nfcvHi = !"0".equals(uri.getQueryParameter("hi"));
                }

                invalidateOptionsMenu();
                updateNfcvConfigVisibility();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void initializeTechnologies() {
        technologies = new ArrayList<>(Arrays.asList(BASE_TECHNOLOGIES));
        if (getPackageManager().hasSystemFeature("com.nxp.mifare")) {
            technologies.addAll(Arrays.asList(MIFARE_TECHNOLOGIES));
        }
    }

    private void enableNfcReaderMode() {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null) {
            int flags = NfcAdapter.FLAG_READER_NFC_A |
                        NfcAdapter.FLAG_READER_NFC_B |
                        NfcAdapter.FLAG_READER_NFC_F |
                        NfcAdapter.FLAG_READER_NFC_V |
                        NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;
            nfcAdapter.enableReaderMode(this, this, flags, null);
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        // If we think we're still connected, check if the tag is actually alive
        if (connectedTag != null) {
            try {
                if (connectedTag.isConnected()) return; // genuinely still connected, skip
            } catch (SecurityException e) {
                // Tag object expired — treat as disconnected
            }
            connectedTag = null;
            connectedTagUid = null;
            setConnected(false);
        }

        TagTechnology tagTech = getTagTechnology(tag);
        if (tagTech == null) return;

        try {
            tagTech.connect();
            SystemClock.sleep(250);
            connectedTag = tagTech;
            connectedTagUid = Hex.encodeHexString(tag.getId(), false);
            setConnected(true);
            processCommands(tagTech);
            // Keep connection alive for replay — tag stays powered
        } catch (IOException e) {
            connectedTag = null;
            connectedTagUid = null;
            setConnected(false);
            appendOutputText(e.getMessage());
        }
    }

    private void processCommands(TagTechnology tagTech) throws IOException {
        clearOutputText();
        String[] commands = getInputText().split("\\r?\\n");
        List<String> executedCommands = new ArrayList<>();
        List<String> txCommands = new ArrayList<>();
        List<String> responses = new ArrayList<>();

        for (String command : commands) {
            String trimmed = command.trim();
            if (!trimmed.isEmpty()) {
                byte[] commandBytes = hexStringToByteArray(trimmed);
                if (isNfcVSelected()) {
                    commandBytes = prepareNfcVCommand(commandBytes, tagTech.getTag());
                }
                byte[] response = transceive(tagTech, commandBytes);
                String responseHex = formatHexWithSpaces(Hex.encodeHexString(response, false));
                appendOutputText(responseHex);
                executedCommands.add(trimmed);
                txCommands.add(formatHexWithSpaces(Hex.encodeHexString(commandBytes, false)));
                responses.add(responseHex);
            }
        }
        lastTxCommands = txCommands;

        // Mark all lines as executed for replay icons
        runOnUiThread(() -> inputView.markAllLinesExecuted());

        // Auto-log to history
        if (!executedCommands.isEmpty() && selectedTechnology != null) {
            String protocol = technologies.get(selectedTechnology);
            HistoryEntry entry = new HistoryEntry(protocol,
                executedCommands.toArray(new String[0]),
                responses.toArray(new String[0]));
            storageManager.addHistoryEntry(entry);
        }
    }

    private void showTechnologySelectionDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Select Tag Technology")
            .setItems(technologies.toArray(new String[0]), (dialog, which) -> {
                selectedTechnology = which;
                invalidateOptionsMenu();
                clearOutputText();
                updateNfcvConfigVisibility();
            })
            .setCancelable(false)
            .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (selectedTechnology != null) {
            menu.add(0, MENU_ID_TECH, 0, technologies.get(selectedTechnology))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        saveAllMenuItem = menu.add(0, MENU_ID_SAVE_ALL, 1, "Save All");
        saveAllMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        saveAllMenuItem.setEnabled(isSaveAllValid());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (drawerLayout.isDrawerOpen(findViewById(R.id.drawer_panel))) {
                drawerLayout.closeDrawers();
            } else {
                drawerLayout.openDrawer(findViewById(R.id.drawer_panel));
            }
            return true;
        } else if (item.getItemId() == MENU_ID_TECH) {
            selectedTechnology = null;
            showTechnologySelectionDialog();
            return true;
        } else if (item.getItemId() == MENU_ID_SAVE_ALL) {
            saveAllCommands();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private TagTechnology getTagTechnology(Tag tag) {
        if (selectedTechnology == null) return null;
        String tech = technologies.get(selectedTechnology);
        switch (tech) {
            case "NFC-A (ISO 14443-3A)": return NfcA.get(tag);
            case "NFC-B (ISO 14443-3B)": return NfcB.get(tag);
            case "NFC-F (JIS 6319-4)": return NfcF.get(tag);
            case "NFC-V (ISO 15693)": return NfcV.get(tag);
            case "ISO-DEP (ISO 14443-4)": return IsoDep.get(tag);
            case "MIFARE Classic": return MifareClassic.get(tag);
            case "MIFARE Ultralight": return MifareUltralight.get(tag);
            default: return null;
        }
    }

    private static byte[] transceive(TagTechnology handle, byte[] payload) throws IOException {
        if (handle instanceof NfcA) return ((NfcA) handle).transceive(payload);
        if (handle instanceof NfcB) return ((NfcB) handle).transceive(payload);
        if (handle instanceof NfcF) return ((NfcF) handle).transceive(payload);
        if (handle instanceof NfcV) return ((NfcV) handle).transceive(payload);
        if (handle instanceof IsoDep) return ((IsoDep) handle).transceive(payload);
        if (handle instanceof MifareClassic) return ((MifareClassic) handle).transceive(payload);
        if (handle instanceof MifareUltralight) return ((MifareUltralight) handle).transceive(payload);
        throw new IOException("Unsupported technology");
    }

    private void copyDeepLink() {
        if (selectedTechnology == null) {
            Toast.makeText(this, "Select a technology first", Toast.LENGTH_SHORT).show();
            return;
        }
        String input = getInputText().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "No commands to share", Toast.LENGTH_SHORT).show();
            return;
        }

        String host = TECH_CLASS_SIMPLE_NAMES[selectedTechnology];
        StringBuilder uri = new StringBuilder("rawnfc://").append(host);

        String[] lines = input.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                uri.append('/').append(trimmed.replaceAll(" ", "+"));
            }
        }

        // Append V-header query params if enabled
        if (isNfcVSelected() && nfcvHeaderEnabled) {
            uri.append("?vheader=1");
            if (nfcvAddr) uri.append("&addr=1");
            if (nfcvOpt) uri.append("&opt=1");
            if (nfcvInv) uri.append("&inv=1");
            if (!nfcvHi) uri.append("&hi=0");
            if (nfcvDual) uri.append("&dual=1");
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("RawNFC Link", uri.toString()));
        Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show();
    }

    private void shareDump() {
        if (selectedTechnology == null || outputView.getText().toString().isEmpty()) return;

        String techText = "android.nfc.tech." + TECH_CLASS_SIMPLE_NAMES[selectedTechnology];
        String dumpText = formatDump(techText, outputView.getText().toString());

        Intent shareIntent = new Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, dumpText);
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    private String formatDump(String techText, String outputText) {
        String[] outputLines = outputText.split("\\r?\\n");
        StringBuilder result = new StringBuilder("-----BEGIN RAW NFC DUMP-----\n")
            .append(techText).append('\n');

        if (connectedTagUid != null) {
            result.append("UID: ").append(formatHexWithSpaces(connectedTagUid)).append('\n');
        }

        int lines = outputLines.length;
        for (int i = 0; i < lines; i++) {
            if (lastTxCommands != null && i < lastTxCommands.size()) {
                result.append("> ").append(lastTxCommands.get(i).replaceAll(" ", "")).append('\n');
            }
            result.append("< ").append(outputLines[i]).append('\n');
        }

        return result.append("-----END RAW NFC DUMP-----").toString().trim();
    }

    private String getInputText() {
        return inputView.getText().toString();
    }

    private void clearOutputText() {
        replayDividerShown = false;
        runOnUiThread(() -> outputView.setText(""));
    }

    private void appendOutputText(final String text) {
        runOnUiThread(() -> {
            if (outputView.getText().toString().isEmpty()) {
                outputView.append(text);
            } else {
                outputView.append("\n" + text);
            }
        });
    }

    private boolean replayDividerShown = false;

    private void appendReplayOutputText(String text) {
        if (!replayDividerShown) {
            // Calculate how many dash chars fit the output width
            float charWidth = outputView.getPaint().measureText("—");
            int availableWidth = outputView.getWidth() - outputView.getPaddingLeft() - outputView.getPaddingRight();
            int dashCount = charWidth > 0 ? (int) (availableWidth / charWidth) : 20;
            StringBuilder divider = new StringBuilder();
            for (int i = 0; i < dashCount; i++) divider.append('—');
            appendOutputText(divider.toString());
            replayDividerShown = true;
        }
        appendOutputText(text);
    }

    private void playNfcSound() {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource("/product/media/audio/ui/NFCInitiated.ogg");
            mp.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.prepare();
            mp.start();
        } catch (Exception ignored) {}
    }

    private static String formatHexWithSpaces(String hex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 2) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(hex, i, Math.min(i + 2, hex.length()));
        }
        return sb.toString();
    }

    private static byte[] hexStringToByteArray(String s) {
        s = s.replaceAll(" ", "");
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private static boolean isCompleteHex(String hex) {
        String stripped = hex.replaceAll("\\s", "");
        if (stripped.isEmpty()) return false;
        if (stripped.length() % 2 != 0) return false;
        for (char c : stripped.toCharArray()) {
            if (Character.digit(c, 16) < 0) return false;
        }
        return true;
    }

    private boolean isSaveAllValid() {
        String input = getInputText().trim();
        if (input.isEmpty()) return false;
        String[] lines = input.split("\\r?\\n");
        int validLineCount = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                if (!isCompleteHex(trimmed)) return false;
                validLineCount++;
            }
        }
        return validLineCount >= 2;
    }

    private void debounceSaveAllUpdate() {
        saveAllDebounceHandler.removeCallbacksAndMessages(null);
        saveAllDebounceHandler.postDelayed(() -> {
            if (saveAllMenuItem != null) {
                saveAllMenuItem.setEnabled(isSaveAllValid());
            }
        }, SAVE_ALL_DEBOUNCE_MS);
    }

    // NFC-V header shorthand

    private boolean isNfcVSelected() {
        return selectedTechnology != null
            && "NFC-V (ISO 15693)".equals(technologies.get(selectedTechnology));
    }

    private void updateNfcvConfigVisibility() {
        nfcvCalcButton.setVisibility(isNfcVSelected() ? View.VISIBLE : View.GONE);
        updateHeaderPrefix();
    }

    private void setConnected(boolean connected) {
        connectionCheckHandler.removeCallbacksAndMessages(null);
        if (connected) {
            scheduleConnectionCheck();
        }
        runOnUiThread(() -> {
            if (connectionIndicator == null) return;
            connectionIndicator.setBackgroundResource(
                connected ? R.drawable.circle_connected : R.drawable.circle_disconnected);
            if (!connected) {
                inputView.clearAllExecutedLines();
            }
        });
    }

    private void scheduleConnectionCheck() {
        connectionCheckHandler.postDelayed(() -> {
            if (connectedTag == null) {
                setConnected(false);
                return;
            }
            boolean alive = false;
            try {
                alive = connectedTag.isConnected();
            } catch (SecurityException e) {
                // tag expired
            }
            if (!alive) {
                connectedTag = null;
                connectedTagUid = null;
                setConnected(false);
            } else {
                scheduleConnectionCheck();
            }
        }, CONNECTION_CHECK_INTERVAL_MS);
    }

    private void updateHeaderPrefix() {
        if (isNfcVSelected() && nfcvHeaderEnabled) {
            String flagsHex = String.format("%02X ", computeNfcVFlags());
            inputView.setHeaderPrefix(flagsHex);
        } else {
            inputView.setHeaderPrefix(null);
        }
    }

    private byte computeNfcVFlags() {
        int flags = 0;
        if (nfcvAddr) flags |= 0x20;
        if (nfcvOpt)  flags |= 0x40;
        if (nfcvInv)  flags |= 0x04;
        if (nfcvHi)   flags |= 0x02;
        if (nfcvDual)  flags |= 0x01;
        return (byte) flags;
    }

    private void showNfcvHeaderDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        CheckBox cbEnabled = new CheckBox(this);
        cbEnabled.setText("Enable V-Header");
        cbEnabled.setChecked(nfcvHeaderEnabled);
        layout.addView(cbEnabled);

        CheckBox cbAddr = new CheckBox(this);
        cbAddr.setText("Addr — Address mode (insert UID)");
        cbAddr.setChecked(nfcvAddr);
        cbAddr.setEnabled(nfcvHeaderEnabled);
        layout.addView(cbAddr);

        CheckBox cbOpt = new CheckBox(this);
        cbOpt.setText("Opt — Option flag");
        cbOpt.setChecked(nfcvOpt);
        cbOpt.setEnabled(nfcvHeaderEnabled);
        layout.addView(cbOpt);

        CheckBox cbInv = new CheckBox(this);
        cbInv.setText("Inv — Inventory mode");
        cbInv.setChecked(nfcvInv);
        cbInv.setEnabled(nfcvHeaderEnabled);
        layout.addView(cbInv);

        CheckBox cbHi = new CheckBox(this);
        cbHi.setText("Hi — High data rate");
        cbHi.setChecked(nfcvHi);
        cbHi.setEnabled(nfcvHeaderEnabled);
        layout.addView(cbHi);

        CheckBox cbDual = new CheckBox(this);
        cbDual.setText("Dual — Dual sub-carrier");
        cbDual.setChecked(nfcvDual);
        cbDual.setEnabled(nfcvHeaderEnabled);
        layout.addView(cbDual);

        TextView preview = new TextView(this);
        preview.setTextSize(14);
        preview.setTypeface(android.graphics.Typeface.MONOSPACE);
        preview.setPadding(0, pad, 0, 0);
        layout.addView(preview);

        Runnable updatePreview = () -> {
            int f = 0;
            if (cbAddr.isChecked()) f |= 0x20;
            if (cbOpt.isChecked())  f |= 0x40;
            if (cbInv.isChecked())  f |= 0x04;
            if (cbHi.isChecked())   f |= 0x02;
            if (cbDual.isChecked()) f |= 0x01;
            String hex = String.format("%02X", f);
            if (cbEnabled.isChecked()) {
                if (cbAddr.isChecked()) {
                    preview.setText("Flags: " + hex + " → [" + hex + "] [cmd] [UID] [params]");
                } else {
                    preview.setText("Flags: " + hex + " → [" + hex + "] [your input]");
                }
            } else {
                preview.setText("Disabled — raw passthrough");
            }
        };

        cbEnabled.setOnCheckedChangeListener((btn, checked) -> {
            cbAddr.setEnabled(checked);
            cbOpt.setEnabled(checked);
            cbInv.setEnabled(checked);
            cbHi.setEnabled(checked);
            cbDual.setEnabled(checked);
            updatePreview.run();
        });
        cbAddr.setOnCheckedChangeListener((btn, checked) -> updatePreview.run());
        cbOpt.setOnCheckedChangeListener((btn, checked) -> updatePreview.run());
        cbInv.setOnCheckedChangeListener((btn, checked) -> updatePreview.run());
        cbHi.setOnCheckedChangeListener((btn, checked) -> updatePreview.run());
        cbDual.setOnCheckedChangeListener((btn, checked) -> updatePreview.run());
        updatePreview.run();

        new AlertDialog.Builder(this)
            .setTitle("ISO 15693 Header")
            .setView(layout)
            .setPositiveButton("OK", (dialog, which) -> {
                nfcvHeaderEnabled = cbEnabled.isChecked();
                nfcvAddr = cbAddr.isChecked();
                nfcvOpt = cbOpt.isChecked();
                nfcvInv = cbInv.isChecked();
                nfcvHi = cbHi.isChecked();
                nfcvDual = cbDual.isChecked();
                updateHeaderPrefix();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private byte[] prepareNfcVCommand(byte[] rawInput, Tag tag) {
        if (!nfcvHeaderEnabled) return rawInput;

        byte flags = computeNfcVFlags();

        if (nfcvAddr) {
            byte[] uid = tag.getId();
            // [flags] [cmd] [UID×8] [params...]
            byte[] result = new byte[1 + 1 + uid.length + (rawInput.length - 1)];
            result[0] = flags;
            result[1] = rawInput[0]; // command code
            System.arraycopy(uid, 0, result, 2, uid.length);
            if (rawInput.length > 1) {
                System.arraycopy(rawInput, 1, result, 2 + uid.length, rawInput.length - 1);
            }
            return result;
        } else {
            // [flags] [rawInput...]
            byte[] result = new byte[1 + rawInput.length];
            result[0] = flags;
            System.arraycopy(rawInput, 0, result, 1, rawInput.length);
            return result;
        }
    }

    private void parsePastedDeepLink(String link) {
        Uri uri = Uri.parse(link);
        if (uri.getHost() == null) return;

        int techIndex = Arrays.asList(TECH_CLASS_SIMPLE_NAMES).indexOf(uri.getHost());
        if (techIndex != -1) {
            selectedTechnology = techIndex;
            invalidateOptionsMenu();
        }

        // Parse NFC-V header params
        if ("NfcV".equals(uri.getHost()) && "1".equals(uri.getQueryParameter("vheader"))) {
            nfcvHeaderEnabled = true;
            nfcvAddr = "1".equals(uri.getQueryParameter("addr"));
            nfcvOpt = "1".equals(uri.getQueryParameter("opt"));
            nfcvInv = "1".equals(uri.getQueryParameter("inv"));
            nfcvDual = "1".equals(uri.getQueryParameter("dual"));
            nfcvHi = !"0".equals(uri.getQueryParameter("hi"));
        }

        // Build command text from path segments
        String path = uri.getPath();
        StringBuilder commands = new StringBuilder();
        if (path != null) {
            String[] parts = path.split("/+");
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) commands.append('\n');
                commands.append(parts[i]);
            }
        }

        // Replace the pasted link with the parsed commands
        inputView.setText(commands.toString());
        inputView.setSelection(inputView.getText().length());

        updateNfcvConfigVisibility();
    }

    private boolean reformatting = false;

    private class HexTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (reformatting) return;
            clearOutputText();
            inputView.clearAllExecutedLines();
            // Allow next tag tap to process commands again
            connectedTag = null;
            connectedTagUid = null;
            setConnected(false);
            debounceSaveAllUpdate();
        }

        @Override
        public void afterTextChanged(Editable s) {
            // Intercept pasted rawnfc:// deep links
            String raw = s.toString().trim();
            if (raw.startsWith("rawnfc://")) {
                parsePastedDeepLink(raw);
                return;
            }
            // Count hex chars before cursor position
            int cursorPos = inputView.getSelectionStart();
            int hexCharsBefore = 0;
            int cursorLine = 0;
            String original = s.toString();
            for (int i = 0; i < cursorPos && i < original.length(); i++) {
                char c = original.charAt(i);
                if (c == '\n') {
                    cursorLine++;
                } else if (Character.digit(c, 16) >= 0) {
                    hexCharsBefore++;
                }
            }

            // Strip non-hex/non-whitespace, uppercase, then auto-space bytes per line
            String[] lines = original.split("\n", -1);
            StringBuilder result = new StringBuilder();
            for (int l = 0; l < lines.length; l++) {
                if (l > 0) result.append('\n');
                StringBuilder hexOnly = new StringBuilder();
                for (char c : lines[l].toCharArray()) {
                    if (Character.digit(c, 16) >= 0) {
                        hexOnly.append(Character.toUpperCase(c));
                    }
                }
                for (int i = 0; i < hexOnly.length(); i++) {
                    if (i > 0 && i % 2 == 0) result.append(' ');
                    result.append(hexOnly.charAt(i));
                }
            }
            String newText = result.toString();
            if (!newText.equals(original)) {
                reformatting = true;
                s.replace(0, s.length(), newText);
                reformatting = false;

                // Restore cursor: find position after hexCharsBefore hex chars on cursorLine
                int newPos = 0;
                int lineIdx = 0;
                int hexCount = 0;
                for (int i = 0; i < newText.length(); i++) {
                    if (lineIdx == cursorLine && hexCount == hexCharsBefore) {
                        newPos = i;
                        break;
                    }
                    char c = newText.charAt(i);
                    if (c == '\n') {
                        lineIdx++;
                    } else if (Character.digit(c, 16) >= 0) {
                        hexCount++;
                    }
                    newPos = i + 1;
                }
                if (newPos > newText.length()) newPos = newText.length();
                inputView.setSelection(newPos);
            }
        }
    }
}
