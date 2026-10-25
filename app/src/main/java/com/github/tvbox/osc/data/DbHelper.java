package com.github.tvbox.osc.data;


import com.github.tvbox.osc.cache.SearchHistory;

import java.util.ArrayList;

/**
 * creator huangyong
 * createTime 2018/12/17 下午6:39
 * path com.nick.movie.db.dao
 * description:中介类
 */
public class DbHelper {

    public static ArrayList<SearchHistory> getAllHistory() {
        ArrayList<SearchHistory> searchHistories = (ArrayList<SearchHistory>) AppDataManager.get().getSearchDao().getAll();
        if (searchHistories != null && searchHistories.size() > 0) {
            return searchHistories;
        } else {
            return new ArrayList<>();
        }
    }

    public static boolean checkKeyWords(String keyword) {
        ArrayList<SearchHistory> byKeywords = (ArrayList<SearchHistory>) AppDataManager.get().getSearchDao().getByKeywords(keyword);
        return byKeywords != null && byKeywords.size() > 0;
    }

    public static void addKeywords(String keyword) {
        // 去重：重复关键词不再重复入库（原实现 check-then-act 全表读，历史表会堆积重复项）
        if (checkKeyWords(keyword)) {
            return;
        }
        ArrayList<SearchHistory> allHistory = getAllHistory();
        if (allHistory.size() > 29) {
            AppDataManager.get().getSearchDao().delete(allHistory.get(0));
        }

        SearchHistory searchHistory = new SearchHistory();
        searchHistory.searchKeyWords = keyword;
        AppDataManager.get().getSearchDao().insert(searchHistory);
    }

    public static void clearKeywords() {
        ArrayList<SearchHistory> allHistory = getAllHistory();
        if (allHistory != null && allHistory.size() > 0) {
            for (SearchHistory history : allHistory) {
                AppDataManager.get().getSearchDao().delete(history);
            }
        }
    }
}
