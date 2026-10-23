package com.github.catvod.crawler.python;

import com.github.catvod.crawler.Spider;

public interface IPyLoader {
    void clear();
    Spider getSpider(String key, String cls, String ext);
}
