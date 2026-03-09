# RawNFC

A minimal Android NFC terminal by [VivoKey](https://vivokey.com/rawnfc) for sending raw hex commands to NFC tags and viewing responses. Supports all standard NFC technologies and provides tools for building, sharing, and replaying command sequences.

## Supported Technologies

- NFC-A (ISO 14443-3A)
- NFC-B (ISO 14443-3B)
- NFC-F (JIS 6319-4)
- NFC-V (ISO 15693)
- ISO-DEP (ISO 14443-4)
- MIFARE Classic (on supported devices)
- MIFARE Ultralight (on supported devices)

## Usage

1. Select a tag technology from the action bar
2. Type hex commands in the input field (one per line) — bytes are auto-spaced as you type
3. Tap a tag to execute all commands and see responses in the output field

## Features

### Command Drawer

Tap the **VivoKey logo** in the action bar to open the command drawer. It contains two sections:

- **Saved** — named command sets you've saved, organized by group
- **History** — automatically logged command sessions (deduplicated against the previous entry)

Commands can be filtered by protocol using the chip bar at the top of the drawer. Tap a command to load it; long-press to append it to the current input.

### Saving Commands

- **Save All** (action bar menu) — saves all input lines as a named command set. Requires 2+ lines of complete hex bytes. The button is hidden until input is valid.
- **Per-line save** — tap the bookmark icon in the left gutter of any input line to save that single command. Rejects incomplete bytes.

Both options let you assign a label and group via a dialog.

### Replaying Commands

After executing commands, each input line gets a circular replay icon on the right edge. Tap the replay icon to re-send that individual command to the still-connected tag without re-running the full sequence. Replay responses appear below a divider line in the output.

### Connection Indicator

A status dot appears in the "Input:" label row (rightmost icon):

- **Green** — tag is connected and ready for replay
- **Grey** — no tag connected

The indicator updates automatically when a tag connects or disconnects. When the tag is lost, replay icons are cleared.

### NFC-V Header Calculator (ISO 15693)

When NFC-V is selected, a **calculator icon** appears next to the "Input:" label. Tapping it opens a dialog to configure ISO 15693 flags:

| Toggle | Bit | Mask | Description |
|--------|-----|------|-------------|
| Addr | 5 | 0x20 | Address mode — auto-inserts tag UID after command byte |
| Opt | 6 | 0x40 | Option flag |
| Inv | 2 | 0x04 | Inventory mode |
| Hi | 1 | 0x02 | High data rate (default ON) |
| Dual | 0 | 0x01 | Dual sub-carrier |

When enabled, a **ghost text prefix** (e.g., `02 `) appears before each input line showing the computed flags byte. The flags byte (and UID if Addr is checked) are automatically prepended to each command on transmit — you only need to type the command code and parameters.

### Deep Links

RawNFC registers the `rawnfc://` URI scheme. Links pre-load a technology and commands:

```
rawnfc://<TechHost>/<cmd1>/<cmd2>/...
```

**Tech hosts:** `NfcA`, `NfcB`, `NfcF`, `NfcV`, `IsoDep`, `MifareClassic`, `MifareUltralight`

Spaces in hex bytes are encoded as `+`:

```
rawnfc://IsoDep/00+A4+04+00+07+D2+76+00+00+85+01+01
```

NFC-V links support header query parameters:

```
rawnfc://NfcV/20+04?vheader=1&addr=1&opt=1
```

| Param | Description |
|-------|-------------|
| `vheader=1` | Enable V-header shorthand |
| `addr=1` | Address mode (insert UID) |
| `opt=1` | Option flag |
| `inv=1` | Inventory mode |
| `hi=0` | Disable high data rate (on by default) |
| `dual=1` | Dual sub-carrier |

**Sharing links:** Tap the **share icon** next to the "Input:" label to copy a `rawnfc://` deep link for the current input and settings to the clipboard.

**Paste-to-parse:** Pasting a `rawnfc://` link directly into the input field automatically parses it — sets the technology, V-header flags, and populates the commands. Useful on devices (e.g., GrapheneOS) where custom URI schemes are blocked.

### NFC Dump

**Long-press the output field** to share a formatted dump of the session. The dump includes:

- Tag technology
- Tag UID (when available)
- Actual transmitted bytes (including any V-header prepended data)
- Received responses

```
-----BEGIN RAW NFC DUMP-----
android.nfc.tech.NfcV
UID: E0 04 01 50 12 34 56 78
> 022004
< 00 01 02 03
-----END RAW NFC DUMP-----
```

## Development

```
./gradlew installDebug && adb shell am start -n com.vivokey.rawnfc/.MainActivity
```

## Release

```
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=$KEYFILE \
  -Pandroid.injected.signing.store.password=$STORE_PASSWORD \
  -Pandroid.injected.signing.key.alias=$KEY_ALIAS \
  -Pandroid.injected.signing.key.password=$KEY_PASSWORD
```
