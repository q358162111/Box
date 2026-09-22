package com.github.tvbox.osc.subtitle.widget;

import android.annotation.SuppressLint;
import android.content.Context;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Html;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.tvbox.osc.cache.CacheManager;
import com.github.tvbox.osc.subtitle.DefaultSubtitleEngine;
import com.github.tvbox.osc.subtitle.SubtitleEngine;
import com.github.tvbox.osc.subtitle.model.Subtitle;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.StringUtils;


import org.jetbrains.annotations.Nullable;

import java.util.List;

import xyz.doikki.videoplayer.player.AbstractPlayer;

/**
 * @author AveryZhong.
 */

@SuppressLint("AppCompatCustomView")
public class SimpleSubtitleView extends TextView
        implements SubtitleEngine, SubtitleEngine.OnSubtitleChangeListener,
        SubtitleEngine.OnSubtitlePreparedListener {

    private static final String EMPTY_TEXT = "";

    private SubtitleEngine mSubtitleEngine;

    public boolean isInternal = false;

    public boolean hasInternal = false;

    private TextView backGroundText = null;//用于描边的TextView

    private int backGroundTextColor = Color.BLACK;//用于描边的TextView

    public SimpleSubtitleView(final Context context) {
        super(context);
        backGroundText = new TextView(context);
        init();
    }

    public SimpleSubtitleView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        backGroundText = new TextView(context, attrs);
        init();
    }

    public SimpleSubtitleView(final Context context, final AttributeSet attrs,
                              final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        backGroundText = new TextView(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mSubtitleEngine = new DefaultSubtitleEngine();
        mSubtitleEngine.setOnSubtitlePreparedListener(this);
        mSubtitleEngine.setOnSubtitleChangeListener(this);
    }

    @Override
    public void onSubtitlePrepared(@Nullable final List<Subtitle> subtitles) {
        start();
    }

    @Override
    public void onSubtitleChanged(@Nullable final Subtitle subtitle) {
        if (StringUtils.isEmpty(subtitle) || subtitle.content == null) {
            setText(EMPTY_TEXT);
            return;
        }
        String text = subtitle.content;
        if (text.startsWith("Dialogue:") || text.startsWith("m ")) {
            setText(EMPTY_TEXT);
            return;
        }
        // 性能与稳定性：原 replaceAll 中的正则 ("\\{[\\s\\S]*?\\}", "^.*?,.*?,.*?,..." 等)
        // 在长 ASS/SRT 字幕（>10KB）上可能引发灾难性回溯导致主线程卡顿/ANR。
        // 改为直接字符串替换 + 按字符扫描，避免正则回溯。
        text = text.replace("\r\n", "<br />");
        text = text.replace('\r', '\n');
        text = text.replace("\n", "<br />");
        text = text.replace("\\N", "<br />");
        // 移除 ASS override tags {...}
        text = stripBracedTags(text);
        // ASS Dialogue 格式：Dialogue: Marked,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text
        // 仅当行首以 Dialogue: 开头时切掉前 9 个逗号字段，保留最后一段（Text）。
        text = stripAssDialoguePrefix(text);
        setText(Html.fromHtml(text));
    }

    /**
     * 移除字符串中所有 {...} 配对标签，避免正则回溯
     */
    private static String stripBracedTags(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder sb = new StringBuilder(input.length());
        int len = input.length();
        int i = 0;
        while (i < len) {
            char c = input.charAt(i);
            if (c == '{') {
                int close = input.indexOf('}', i + 1);
                if (close < 0) {
                    // 未闭合，丢弃剩余以避免下次 setText 再次扫到一半
                    break;
                }
                i = close + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * ASS Dialogue 行前 9 个逗号字段移除：仅匹配行首 "Dialogue:" 前缀
     */
    private static String stripAssDialoguePrefix(String input) {
        if (input == null || input.isEmpty()) return input;
        if (!input.startsWith("Dialogue:")) return input;
        int commas = 0;
        int idx = "Dialogue:".length();
        int len = input.length();
        while (idx < len && commas < 9) {
            if (input.charAt(idx) == ',') commas++;
            idx++;
        }
        if (commas < 9) return input;
        return input.substring(idx);
    }

    @Override
    public void setSubtitlePath(final String path) {
        isInternal = false;
        mSubtitleEngine.setSubtitlePath(path);
    }

    @Override
    public void setSubtitleDelay(Integer mseconds) {
        mSubtitleEngine.setSubtitleDelay(mseconds);
    }

    public void setPlaySubtitleCacheKey(String cacheKey) {
        mSubtitleEngine.setPlaySubtitleCacheKey(cacheKey);
    }

    public String getPlaySubtitleCacheKey() {
        return mSubtitleEngine.getPlaySubtitleCacheKey();
    }

    public void clearSubtitleCache() {
        String subtitleCacheKey = getPlaySubtitleCacheKey();
        if (subtitleCacheKey != null && subtitleCacheKey.length() > 0) {
            CacheManager.delete(MD5.string2MD5(subtitleCacheKey), "");
        }
    }

    @Override
    public void reset() {
        mSubtitleEngine.reset();
    }

    @Override
    public void start() {
        mSubtitleEngine.start();
    }

    @Override
    public void pause() {
        mSubtitleEngine.pause();
    }

    @Override
    public void resume() {
        mSubtitleEngine.resume();
    }

    @Override
    public void stop() {
        mSubtitleEngine.stop();
    }

    @Override
    public void destroy() {
        mSubtitleEngine.destroy();
    }

    @Override
    public void bindToMediaPlayer(AbstractPlayer mediaPlayer) {
        mSubtitleEngine.bindToMediaPlayer(mediaPlayer);
    }

    @Override
    public void setOnSubtitlePreparedListener(final OnSubtitlePreparedListener listener) {
        mSubtitleEngine.setOnSubtitlePreparedListener(listener);
    }

    @Override
    public void setOnSubtitleChangeListener(final OnSubtitleChangeListener listener) {
        mSubtitleEngine.setOnSubtitleChangeListener(listener);
    }

    @Override
    protected void onDetachedFromWindow() {
        destroy();
        super.onDetachedFromWindow();
    }

    @Override
    public void setShadowLayer(float radius, float dx, float dy, int color) {
        this.backGroundTextColor = color;
        super.setShadowLayer(radius, dx, dy, color);
    }

    public void setBackGroundTextColor(int backGroundTextColor) {
        if (backGroundTextColor != this.backGroundTextColor) {
            this.backGroundTextColor = backGroundTextColor;
            invalidate();
        }
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        //同步布局参数
        backGroundText.setLayoutParams(params);
        super.setLayoutParams(params);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        CharSequence tt = backGroundText.getText();
        //两个TextView上的文字必须一致
        if (TextUtils.isEmpty(tt) || !tt.equals(this.getText())) {
            backGroundText.setText(getText());
            this.postInvalidate();
        }
        backGroundText.measure(widthMeasureSpec, heightMeasureSpec);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void setTextSize(float size) {
        super.setTextSize(size);
        backGroundText.setTextSize(size);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        if (backGroundText != null) {
            backGroundText.setText(text);
        }
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        backGroundText.layout(left, top, right, bottom);
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //其他地方，backGroundText和super的先后顺序影响不会很大，但是此处必须要先绘制backGroundText，
        drawBackGroundText();
        backGroundText.draw(canvas);
        super.onDraw(canvas);
    }

    private void drawBackGroundText() {
        TextPaint tp = backGroundText.getPaint();
        //设置描边宽度
        tp.setStrokeWidth(4);
        //背景描边并填充全部
        tp.setStyle(Paint.Style.STROKE);
        //设置描边颜色
        backGroundText.setTextColor(backGroundTextColor);
        //将背景的文字对齐方式做同步
        backGroundText.setGravity(getGravity());
    }

}
