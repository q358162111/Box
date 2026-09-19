package com.github.tvbox.osc.bean;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class Epginfo {

    public Date startdateTime;
    public Date enddateTime;
    public int datestart;
    public int dateend;
    public String title;
    public String originStart;
    public String originEnd;
    public String start;
    public String end;
    public int index;
    public Date epgDate;
    public String currentEpgDate = null;
    SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd");

    public Epginfo(Date Date, String str, Date date, String str1, String str2, int pos) {
        epgDate = Date;
        currentEpgDate = timeFormat.format(epgDate);
        title = str;
        originStart = str1;
        originEnd = str2;
        index = pos;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        SimpleDateFormat userSimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        userSimpleDateFormat.setTimeZone(TimeZone.getDefault());
        // parse 失败返回 null 而非抛异常，需判空防止后续 format(null) 崩溃
        startdateTime = userSimpleDateFormat.parse(simpleDateFormat.format(date) + " " + str1 + ":00 GMT+8:00", new ParsePosition(0));
        enddateTime = userSimpleDateFormat.parse(simpleDateFormat.format(date) + " " + str2 + ":00 GMT+8:00", new ParsePosition(0));
        SimpleDateFormat zoneFormat = new SimpleDateFormat("HH:mm");
        start = startdateTime != null ? zoneFormat.format(startdateTime) : str1;
        end = enddateTime != null ? zoneFormat.format(enddateTime) : str2;
        datestart = parseIntSafe(start);
        dateend = parseIntSafe(end);
    }

    private static int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.replace(":", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
