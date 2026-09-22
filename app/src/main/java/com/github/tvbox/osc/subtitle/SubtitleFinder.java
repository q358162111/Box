package com.github.tvbox.osc.subtitle;

import androidx.annotation.Nullable;

import com.github.tvbox.osc.subtitle.model.Subtitle;

import java.util.List;

/**
 * @author AveryZhong.
 */

public class SubtitleFinder {
    private SubtitleFinder() {
        throw new AssertionError("No instance for you");
    }

    @Nullable
    public static Subtitle find(long position, List<Subtitle> subtitles) {
        if (subtitles == null || subtitles.isEmpty()) {
            return null;
        }
        // 修复：原实现内部嵌套条件互斥，导致分支永远走 else，导致非顺序数据下结果错误。
        // 规范二分：position < start → 左半；position > end → 右半；否则命中。
        int start = 0;
        int end = subtitles.size() - 1;
        while (start <= end) {
            int middle = start + (end - start) / 2; // 防御 (start+end) 溢出
            Subtitle middleSubtitle = subtitles.get(middle);
            if (middleSubtitle == null) {
                return null;
            }
            long s = middleSubtitle.start != null ? middleSubtitle.start.mseconds : 0L;
            long e = middleSubtitle.end != null ? middleSubtitle.end.mseconds : 0L;
            if (position < s) {
                end = middle - 1;
            } else if (position > e) {
                start = middle + 1;
            } else {
                return middleSubtitle;
            }
        }
        return null;
    }

}