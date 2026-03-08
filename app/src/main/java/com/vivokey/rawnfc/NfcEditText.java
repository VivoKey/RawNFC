package com.vivokey.rawnfc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.EditText;

import java.util.HashSet;
import java.util.Set;

public class NfcEditText extends EditText {
    private static final int LINE_NUMBER_PADDING = 75;
    private static final int LINE_NUMBER_MARGIN = 25;
    private static final int ICON_SIZE_DP = 14;
    private static final int ICON_TOUCH_TARGET_DP = 44;
    private static final long REPLAY_HOLD_THRESHOLD_MS = 150;

    private final Rect rect = new Rect();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint replayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path iconPath = new Path();

    private boolean showActionIcons = false;
    private final Set<Integer> executedLines = new HashSet<>();
    private OnCommandActionListener actionListener;
    private String headerPrefix = null;
    private float headerPrefixWidth = 0;
    private int basePaddingLeft = -1;
    private final Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private long touchDownTime;
    private int touchDownLine = -1;
    private boolean touchInSaveZone = false;
    private boolean touchInReplayZone = false;

    public interface OnCommandActionListener {
        void onSave(int line, String hex);
        void onReplay(int line, String hex);
    }

    public NfcEditText(Context context) {
        super(context);
        init();
    }

    public NfcEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NfcEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        paint.setColor(Color.GRAY);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(getTextSize());

        iconPaint.setColor(0xFF69848F);
        iconPaint.setStyle(Paint.Style.FILL);

        replayPaint.setColor(0xFF6493B3);
        replayPaint.setStyle(Paint.Style.FILL);

        headerPaint.setColor(0xFF69848F);
        headerPaint.setStyle(Paint.Style.FILL);
        headerPaint.setTypeface(Typeface.MONOSPACE);

        int paddingLeft = getPaddingLeft() + LINE_NUMBER_PADDING;
        setPadding(paddingLeft, getPaddingTop(), getPaddingRight(), getPaddingBottom());
    }

    public void setOnCommandActionListener(OnCommandActionListener listener) {
        this.actionListener = listener;
    }

    public void setShowActionIcons(boolean show) {
        this.showActionIcons = show;
        invalidate();
    }

    public void markLineExecuted(int line) {
        executedLines.add(line);
        invalidate();
    }

    public void markAllLinesExecuted() {
        String text = getText().toString();
        String[] lines = text.split("\\r?\\n");
        executedLines.clear();
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                executedLines.add(i);
            }
        }
        invalidate();
    }

    public void clearExecutedLine(int line) {
        executedLines.remove(line);
        invalidate();
    }

    public void clearAllExecutedLines() {
        executedLines.clear();
        invalidate();
    }

    public void setHeaderPrefix(String prefix) {
        if (basePaddingLeft < 0) {
            basePaddingLeft = getPaddingLeft();
        }
        this.headerPrefix = prefix;
        headerPaint.setTextSize(getTextSize());
        if (prefix != null) {
            headerPrefixWidth = headerPaint.measureText(prefix);
            setPadding(basePaddingLeft + (int) headerPrefixWidth, getPaddingTop(), getPaddingRight(), getPaddingBottom());
        } else {
            headerPrefixWidth = 0;
            setPadding(basePaddingLeft, getPaddingTop(), getPaddingRight(), getPaddingBottom());
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isFocused() || getText().length() > 0) {
            drawLineNumbers(canvas);
            if (showActionIcons) {
                drawActionIcons(canvas);
            }
        }

        // Draw header prefix as ghost text on each non-empty line
        if (headerPrefix != null && getLayout() != null) {
            float textX = basePaddingLeft >= 0 ? basePaddingLeft : getPaddingLeft();
            int lineCount = getLineCount();
            int lineNumber = 0;
            for (int i = 0; i < lineCount; i++) {
                if (i > 0 && getText().charAt(getLayout().getLineStart(i) - 1) != '\n') {
                    continue;
                }
                int lineStart = getLayout().getLineStart(i);
                int lineEnd = getLayout().getLineEnd(i);
                String lineText = getText().subSequence(lineStart, lineEnd).toString().trim();
                if (!lineText.isEmpty()) {
                    int baseline = getLineBounds(i, null);
                    canvas.drawText(headerPrefix, textX, baseline, headerPaint);
                }
                lineNumber++;
            }
        }

        super.onDraw(canvas);
    }

    private void drawLineNumbers(Canvas canvas) {
        int lineCount = getLineCount();
        int lineNumber = 1;
        for (int i = 0; i < lineCount; i++) {
            int baseline = getLineBounds(i, null);
            if (i == 0 || getText().charAt(getLayout().getLineStart(i) - 1) == '\n') {
                String lineNumberStr = String.format("%2d", lineNumber);
                canvas.drawText(lineNumberStr, rect.left + LINE_NUMBER_MARGIN, baseline, paint);
                lineNumber++;
            }
        }
    }

    private void drawActionIcons(Canvas canvas) {
        int lineCount = getLineCount();
        float density = getResources().getDisplayMetrics().density;
        float iconSize = ICON_SIZE_DP * density;
        int lineNumber = 0;

        for (int i = 0; i < lineCount; i++) {
            if (i > 0 && getText().charAt(getLayout().getLineStart(i) - 1) != '\n') {
                continue;
            }

            int lineStart = getLayout().getLineStart(i);
            int lineEnd = getLayout().getLineEnd(i);
            String lineText = getText().subSequence(lineStart, lineEnd).toString().trim();

            if (!lineText.isEmpty()) {
                int baseline = getLineBounds(i, null);
                float lineTop = baseline - getLineHeight() * 0.75f;
                float centerY = lineTop + getLineHeight() * 0.5f;

                // Save icon (bookmark) — left side, before line number
                float saveX = rect.left + 4 * density;
                drawBookmarkIcon(canvas, saveX, centerY, iconSize * 0.7f, iconPaint);

                // Replay icon (circular arrow) — right side, only if line was executed
                if (executedLines.contains(lineNumber)) {
                    float replayCx = getWidth() - getPaddingRight() - iconSize * 0.5f;
                    drawReplayIcon(canvas, replayCx, centerY, iconSize, replayPaint);
                }
            }
            lineNumber++;
        }
    }

    private void drawBookmarkIcon(Canvas canvas, float x, float cy, float size, Paint p) {
        iconPath.reset();
        float halfW = size * 0.4f;
        float halfH = size * 0.55f;
        iconPath.moveTo(x, cy - halfH);
        iconPath.lineTo(x + halfW * 2, cy - halfH);
        iconPath.lineTo(x + halfW * 2, cy + halfH);
        iconPath.lineTo(x + halfW, cy + halfH * 0.4f);
        iconPath.lineTo(x, cy + halfH);
        iconPath.close();
        canvas.drawPath(iconPath, p);
    }

    private void drawReplayIcon(Canvas canvas, float cx, float cy, float size, Paint p) {
        // Circular arrow (replay icon)
        float radius = size * 0.45f;
        android.graphics.RectF oval = new android.graphics.RectF(
            cx - radius, cy - radius, cx + radius, cy + radius);

        // Draw arc (270 degrees, leaving a gap for the arrowhead)
        p.setStyle(Paint.Style.STROKE);
        float strokeWidth = size * 0.15f;
        p.setStrokeWidth(strokeWidth);
        p.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawArc(oval, -90, 270, false, p);

        // Draw arrowhead at the end of the arc (pointing down-right at the top)
        p.setStyle(Paint.Style.FILL);
        float arrowSize = size * 0.25f;
        float arrowX = cx;
        float arrowY = cy - radius;
        iconPath.reset();
        iconPath.moveTo(arrowX - arrowSize, arrowY - arrowSize * 0.3f);
        iconPath.lineTo(arrowX + arrowSize * 0.3f, arrowY);
        iconPath.lineTo(arrowX - arrowSize * 0.3f, arrowY + arrowSize);
        iconPath.close();
        canvas.drawPath(iconPath, p);

        // Reset style for other icons
        p.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!showActionIcons || actionListener == null) {
            return super.onTouchEvent(event);
        }

        float density = getResources().getDisplayMetrics().density;
        float touchTarget = ICON_TOUCH_TARGET_DP * density;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                float x = event.getX();
                float y = event.getY();
                int line = getLineAtY(y);

                if (line >= 0) {
                    // Save zone: left gutter
                    if (x < LINE_NUMBER_MARGIN * density + touchTarget * 0.5f) {
                        touchDownTime = System.currentTimeMillis();
                        touchDownLine = line;
                        touchInSaveZone = true;
                        touchInReplayZone = false;
                        // Don't consume — let EditText handle for scrolling
                    }
                    // Replay zone: right edge
                    else if (x > getWidth() - getPaddingRight() - touchTarget && executedLines.contains(line)) {
                        touchDownTime = System.currentTimeMillis();
                        touchDownLine = line;
                        touchInReplayZone = true;
                        touchInSaveZone = false;
                    } else {
                        resetTouchState();
                    }
                } else {
                    resetTouchState();
                }
                break;

            case MotionEvent.ACTION_UP:
                if (touchDownLine >= 0) {
                    String lineText = getLineText(touchDownLine);
                    if (lineText != null && !lineText.trim().isEmpty()) {
                        if (touchInSaveZone) {
                            actionListener.onSave(touchDownLine, lineText.trim());
                            resetTouchState();
                            return true;
                        } else if (touchInReplayZone) {
                            long holdTime = System.currentTimeMillis() - touchDownTime;
                            if (holdTime >= REPLAY_HOLD_THRESHOLD_MS) {
                                actionListener.onReplay(touchDownLine, lineText.trim());
                                resetTouchState();
                                return true;
                            }
                        }
                    }
                }
                resetTouchState();
                break;

            case MotionEvent.ACTION_CANCEL:
                resetTouchState();
                break;
        }

        return super.onTouchEvent(event);
    }

    private void resetTouchState() {
        touchDownLine = -1;
        touchInSaveZone = false;
        touchInReplayZone = false;
    }

    private int getLineAtY(float y) {
        int lineCount = getLineCount();
        int lineNumber = 0;
        for (int i = 0; i < lineCount; i++) {
            if (i > 0 && getText().charAt(getLayout().getLineStart(i) - 1) != '\n') {
                continue;
            }
            int baseline = getLineBounds(i, null);
            float lineTop = baseline - getLineHeight();
            float lineBottom = baseline + getLineHeight() * 0.25f;
            if (y >= lineTop && y <= lineBottom) {
                String lineText = getLineText(lineNumber);
                if (lineText != null && !lineText.trim().isEmpty()) {
                    return lineNumber;
                }
            }
            lineNumber++;
        }
        return -1;
    }

    private String getLineText(int lineNumber) {
        String[] lines = getText().toString().split("\\r?\\n", -1);
        if (lineNumber >= 0 && lineNumber < lines.length) {
            return lines[lineNumber];
        }
        return null;
    }
}
