package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.catvod.crawler.JsLoader;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.adapter.FastListAdapter;
import com.github.tvbox.osc.ui.adapter.FastSearchAdapter;
import com.github.tvbox.osc.ui.adapter.SearchWordAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.SearchHelper;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class FastSearchActivity extends BaseActivity {
    private LinearLayout llLayout;
    private TextView mSearchTitle;
    private TvRecyclerView mGridView;
    private TvRecyclerView mGridViewFilter;
    private TvRecyclerView mGridViewWord;
    private TvRecyclerView mGridViewWordFenci;
    SourceViewModel sourceViewModel;

    private SearchWordAdapter searchWordAdapter;
    private FastSearchAdapter searchAdapter;
    private FastSearchAdapter searchAdapterFilter;
    private FastListAdapter spListAdapter;
    private String searchTitle = "";
    private HashMap<String, String> spNames;
    private boolean isFilterMode = false;
    private String searchFilterKey = "";    // 过滤的key
    private HashMap<String, ArrayList<Movie.Video>> resultVods; // 搜索结果
    private int finishedCount = 0;
    private final List<String> quickSearchWord = new ArrayList<>();
    private HashMap<String, String> mCheckSources = null;

    private final View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View itemView, boolean hasFocus) {
            try {
                if (!hasFocus) {
                    spListAdapter.onLostFocus(itemView);
                } else {
                    int ret = spListAdapter.onSetFocus(itemView);
                    if (ret < 0) return;
                    TextView v = (TextView) itemView;
                    String sb = v.getText().toString();
                    filterResult(sb);
                }
            } catch (Exception e) {
                Toast.makeText(FastSearchActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_fast_search;
    }

    @Override
    protected void init() {
        spNames = new HashMap<String, String>();
        resultVods = new HashMap<String, ArrayList<Movie.Video>>();
        initView();
        initViewModel();
        initData();
    }

    private List<Runnable> pauseRunnable = null;

    @Override
    protected void onResume() {
        super.onResume();
        // Activity 已销毁时不得重放任务:回调会操作已失效的视图并 post EventBus
        if (isFinishing() || isDestroyed()) return;
        // 暂停任务的重放逻辑收敛到 SourceViewModel.resumeAll
        if (pauseRunnable != null && pauseRunnable.size() > 0 && sourceViewModel != null) {
            allRunCount.set(pauseRunnable.size());
            sourceViewModel.resumeAll(pauseRunnable);
            pauseRunnable = null;
        }
    }

    private void initView() {
        EventBus.getDefault().register(this);
        llLayout = findViewById(R.id.llLayout);
        mSearchTitle = findViewById(R.id.mSearchTitle);
        mGridView = findViewById(R.id.mGridView);
        mGridViewWord = findViewById(R.id.mGridViewWord);
        mGridViewFilter = findViewById(R.id.mGridViewFilter);

        mGridViewWord.setHasFixedSize(true);
        mGridViewWord.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        spListAdapter = new FastListAdapter();
        mGridViewWord.setAdapter(spListAdapter);

//        mGridViewWord.setFocusable(true);
//        mGridViewWord.setOnFocusChangeListener(new View.OnFocusChangeListener() {
//            @Override
//            public void onFocusChange(View itemView, boolean hasFocus) {}
//        });

        mGridViewWord.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View child) {
                child.setFocusable(true);
                child.setOnFocusChangeListener(focusChangeListener);
                TextView t = (TextView) child;
                if (t.getText() == getString(R.string.fs_show_all)) {
                    t.requestFocus();
                }
//                if (child.isFocusable() && null == child.getOnFocusChangeListener()) {
//                    child.setOnFocusChangeListener(focusChangeListener);
//                }
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
                view.setOnFocusChangeListener(null);
            }
        });

        spListAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                String spName = spListAdapter.getItem(position);
                filterResult(spName);
            }
        });

        // 聚合搜索结果 item 尺寸固定，恢复固定尺寸优化（原为注释掉的遗留代码）
        mGridView.setHasFixedSize(true);
        mGridView.setItemViewCacheSize(16);
        mGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, isBaseOnWidth() ? 4 : 5));

        searchAdapter = new FastSearchAdapter();
        mGridView.setAdapter(searchAdapter);

        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = searchAdapter.getData().get(position);
                if (video != null) {
                    // 暂停搜索的关停逻辑收敛到 SourceViewModel.pauseAll
                    pauseSearch();
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    jumpActivity(DetailActivity.class, bundle);
                }
            }
        });

        mGridViewFilter.setLayoutManager(new V7GridLayoutManager(this.mContext, isBaseOnWidth() ? 4 : 5));
        searchAdapterFilter = new FastSearchAdapter();
        mGridViewFilter.setAdapter(searchAdapterFilter);
        searchAdapterFilter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = searchAdapterFilter.getData().get(position);
                if (video != null) {
                    // 暂停搜索的关停逻辑收敛到 SourceViewModel.pauseAll
                    pauseSearch();
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    jumpActivity(DetailActivity.class, bundle);
                }
            }
        });

        setLoadSir(llLayout);

        // 分词
        searchWordAdapter = new SearchWordAdapter();
        mGridViewWordFenci = findViewById(R.id.mGridViewWordFenci);
        mGridViewWordFenci.setAdapter(searchWordAdapter);
        mGridViewWordFenci.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        searchWordAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                String str = searchWordAdapter.getData().get(position);
                search(str);
            }
        });
        searchWordAdapter.setNewData(new ArrayList<>());
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
    }

    private void filterResult(String spName) {
        if (spName == getString(R.string.fs_show_all)) {
            mGridView.setVisibility(View.VISIBLE);
            mGridViewFilter.setVisibility(View.GONE);
            return;
        }
        String key = spNames.get(spName);
        if (key.isEmpty()) return;

        if (searchFilterKey == key) return;
        searchFilterKey = key;

        List<Movie.Video> list = resultVods.get(key);
        searchAdapterFilter.setNewData(list);
        mGridView.setVisibility(View.GONE);
        mGridViewFilter.setVisibility(View.VISIBLE);
    }

    private void fenci() {
        if (!quickSearchWord.isEmpty()) return; // 如果经有分词了，不再进行二次分词
        // 分词
        // 防御：searchTitle 含 "&" "?" "#" 等字符时未 URL 编码会导致 URL 注入/服务端解析错乱
        // 使用 URLEncoder 对用户输入进行 UTF-8 编码后再拼接
        String encodedTitle = "";
        try {
            encodedTitle = java.net.URLEncoder.encode(searchTitle, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        OkGo.<String>get("https://api.yesapi.cn/?service=App.Scws.GetWords&text=" + encodedTitle + "&app_key=CEE4B8A091578B252AC4C92FB4E893C3&sign=CB7602F3AC922808AF5D475D8DA33302")
                .tag("fenci")
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() != null) {
                            return response.body().string();
                        } else {
                            throw new IllegalStateException("网络请求错误");
                        }
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        String json = response.body();
                        quickSearchWord.clear();
                        try {
                            JsonObject resJson = JsonParser.parseString(json).getAsJsonObject();
                            JsonElement wordsJson = resJson.get("data").getAsJsonObject().get("words");

                            for (JsonElement je : wordsJson.getAsJsonArray()) {
                                quickSearchWord.add(je.getAsJsonObject().get("word").getAsString());
                            }
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                        quickSearchWord.add(searchTitle);
                        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, quickSearchWord));
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                    }
                });
    }

    private void initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch();
    }

    private void initData() {
        initCheckedSourcesForSearch();
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("title")) {
            String title = intent.getStringExtra("title");
            showLoading();
            search(title);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            String title = (String) event.obj;
            showLoading();
            search(title);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (mSearchTitle != null) {
//            mSearchTitle.setText(String.format(getString(R.string.fs_results) + " : %d/%d", finishedCount, spNames.size()));
            finishedCount = searchAdapter.getData().size();
            mSearchTitle.setText(String.format(getString(R.string.fs_results) + " : %d", finishedCount));
        }
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_WORD) {
            if (event.obj != null) {
                List<String> data = (List<String>) event.obj;
                searchWordAdapter.setNewData(data);
            }
        }
    }

    private void search(String title) {
        cancel();
        showLoading();
        this.searchTitle = title;
        fenci();
        mGridView.setVisibility(View.INVISIBLE);
        mGridViewFilter.setVisibility(View.GONE);
        searchAdapter.setNewData(new ArrayList<>());
        searchAdapterFilter.setNewData(new ArrayList<>());

        spListAdapter.reset();
        resultVods.clear();
        searchFilterKey = "";
        isFilterMode = false;
        spNames.clear();
        finishedCount = 0;

        searchResult();
    }

    private ExecutorService searchExecutorService = null;
    private final AtomicInteger allRunCount = new AtomicInteger(0);

    /**
     * 暂停搜索：先记录未完成的待跑任务（回到本页时 resumeSearch 续跑），再真正关停线程池。
     * 修复：此前只把 searchExecutorService 置 null 却不 shutdownNow —— 该池是本 Activity 自建的
     * 5 线程 non-daemon 池，置 null 后 onDestroy 也拿不到引用，每点一次搜索结果就泄漏 5 个线程，
     * 且队列里剩余任务会继续跑完并 post EventBus 到已跳转走的页面。
     */
    private void pauseSearch() {
        try {
            // 1) ViewModel 侧挂起任务
            List<Runnable> paused = new ArrayList<>();
            List<Runnable> vmPaused = sourceViewModel.pauseAll();
            if (vmPaused != null) paused.addAll(vmPaused);
            // 2) 本 Activity 自建池的挂起任务：shutdownNow() 会返回尚未开始执行的任务，
            //    收集起来供 onResume 重放，避免跳转详情页后剩余源永远搜不出结果。
            if (searchExecutorService != null) {
                List<Runnable> pending = searchExecutorService.shutdownNow();
                if (pending != null) paused.addAll(pending);
                searchExecutorService = null;
            }
            pauseRunnable = paused;
            JsLoader.stopAll();
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private void searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JsLoader.stopAll();
            }
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            searchAdapter.setNewData(new ArrayList<>());
            searchAdapterFilter.setNewData(new ArrayList<>());
            allRunCount.set(0);
        }
        searchExecutorService = Executors.newFixedThreadPool(5);
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);

        ArrayList<String> siteKey = new ArrayList<>();
        ArrayList<String> hots = new ArrayList<>();

        spListAdapter.setNewData(hots);
        spListAdapter.addData(getString(R.string.fs_show_all));
        for (SourceBean bean : searchRequestList) {
            if (!bean.isSearchable()) {
                continue;
            }
            if (mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) {
                continue;
            }
            siteKey.add(bean.getKey());
            this.spNames.put(bean.getName(), bean.getKey());
            allRunCount.incrementAndGet();
        }

        for (String key : siteKey) {
            searchExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        sourceViewModel.getSearch(key, searchTitle);
                    } catch (Exception e) {

                    }
                }
            });
        }
    }

    // 向过滤栏添加有结果的spname
    private String addWordAdapterIfNeed(String key) {
        try {
            String name = "";
            for (String n : spNames.keySet()) {
                if (spNames.get(n) == key) {
                    name = n;
                }
            }
            if (name == "") return key;

            List<String> names = spListAdapter.getData();
            for (int i = 0; i < names.size(); ++i) {
                if (name == names.get(i)) {
                    return key;
                }
            }

            spListAdapter.addData(name);
            return key;
        } catch (Exception e) {
            return key;
        }
    }

    private void searchData(AbsXml absXml) {
        String lastSourceKey = "";

        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> data = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                data.add(video);
                if (!resultVods.containsKey(video.sourceKey)) {
                    resultVods.put(video.sourceKey, new ArrayList<Movie.Video>());
                }
                resultVods.get(video.sourceKey).add(video);
                if (video.sourceKey != lastSourceKey) {
                    lastSourceKey = this.addWordAdapterIfNeed(video.sourceKey);
                }
            }

            if (searchAdapter.getData().size() > 0) {
                searchAdapter.addData(data);
            } else {
                showSuccess();
                if (!isFilterMode)
                    mGridView.setVisibility(View.VISIBLE);
                searchAdapter.setNewData(data);
            }
        }

        int count = allRunCount.decrementAndGet();
        if (count <= 0) {
            if (searchAdapter.getData().size() <= 0) {
                showEmpty();
            }
            cancel();
        }
    }

    private void cancel() {
        OkGo.getInstance().cancelTag("search");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancel();
        // 丢弃待重放任务，避免 onResume 在销毁后仍重放
        if (pauseRunnable != null) pauseRunnable.clear();
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
            // resumeAll 可能在 ViewModel 侧也建了池（重放暂停任务），此处一并关停
            if (sourceViewModel != null) {
                sourceViewModel.shutdownNow();
                sourceViewModel.destroyExecutor();
            }
            //sLoader.load();
            JsLoader.stopAll();
        } catch (Throwable th) {
            th.printStackTrace();
        }
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
    }
}
