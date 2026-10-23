package com.github.catvod.crawler;

import android.util.Log;
import com.github.catvod.crawler.python.IPyLoader;

public class pyLoader implements IPyLoader {

    @Override
    public void clear() {
        Log.i("PyLoader", "normal flavor: clear() 调用，但不支持 Python 功能。");
    }

    @Override
    public Spider getSpider(String key, String cls, String ext) {
        Log.i("PyLoader", "normal flavor: getSpider() 调用，但不支持 Python 功能。");
        return new SpiderNull();
    }
}
