/*
 * Copyright (C) 2016 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.klinker.android.article;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.customtabs.CustomTabsIntent;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import org.jsoup.select.Elements;

import xyz.klinker.android.article.api.Article;

/**
 * Activity that will display an article grabbed from the server or redirect to a chrome custom
 * tab if the article cannot be displayed appropriately or the content is not an article at all.
 *
 * You should create this activity using the {@link ArticleIntent.Builder}, not invoke it directly.
 *
 * NOTE: Not all options in the builder by be applied in this activity. However, all options will
 * be forwarded to a chrome custom tab if the user chooses to view it there.
 */
public class ArticleActivity extends AppCompatActivity implements ArticleLoadedListener {

    private static final String TAG = "ArticleActivity";
    private static final boolean DEBUG = false;

    private static final int MIN_NUM_ELEMENTS = 3;

    private String url;
    private ArticleUtils utils;
    private RecyclerView recyclerView;
    private ArticleAdapter adapter;
    private ProgressBar progressBar;
    private int primaryColor;
    private int accentColor;

    static {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.url = getIntent().getDataString();

        if (url == null) {
            throw new RuntimeException("EXTRA_URL must not be null.");
        }

        if (DEBUG) {
            Log.v(TAG, "loading article: " + url);
        }

        int theme = getIntent().getIntExtra(ArticleIntent.EXTRA_THEME, ArticleIntent.THEME_AUTO);
        if (theme == ArticleIntent.THEME_LIGHT) {
            getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (theme == ArticleIntent.THEME_DARK) {
            getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_AUTO);
        }

        this.utils = new ArticleUtils();
        this.utils.loadArticle(url, this);

        this.primaryColor = getIntent().getIntExtra(ArticleIntent.EXTRA_TOOLBAR_COLOR,
                getResources().getColor(R.color.colorPrimary));
        this.accentColor = getIntent().getIntExtra(ArticleIntent.EXTRA_ACCENT_COLOR,
                getResources().getColor(R.color.colorAccent));

        setContentView(R.layout.activity_article);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        View statusBar = findViewById(R.id.status_bar);

        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addOnScrollListener(
                new ArticleScrollListener(toolbar, statusBar, primaryColor));

        progressBar = (ProgressBar) findViewById(R.id.loading);

        Utils.changeRecyclerOverscrollColors(recyclerView, primaryColor);
        Utils.changeProgressBarColors(progressBar, primaryColor);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(null);
        }

        findViewById(R.id.transparent_side_1).setOnClickListener(sideClickListener);
        findViewById(R.id.transparent_side_2).setOnClickListener(sideClickListener);
    }

    @Override
    public void onArticleLoaded(Article article) {
        if (article == null || !article.isArticle) {
            if (DEBUG) {
                Log.v(TAG, "not an article or couldn't fetch url");
            }

            openChromeCustomTab();
        } else {
            if (DEBUG) {
                Log.v(TAG, "finished loading article at " + article.url);
                Log.v(TAG, "\t" + article.title);
                Log.v(TAG, "\t" + article.author);
                Log.v(TAG, "\t" + article.description);
            }

            adapter = new ArticleAdapter(article, accentColor);
            recyclerView.setAdapter(adapter);

            utils.parseArticleContent(article, this);
            progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onArticleParsed(Elements elements) {
        if (elements != null && elements.size() >= MIN_NUM_ELEMENTS) {
            adapter.addElements(elements);
        } else {
            openChromeCustomTab();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_article, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        } else if (item.getItemId() == R.id.share) {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, url);
            sendIntent.setType("text/plain");
            startActivity(Intent.createChooser(sendIntent,
                    getResources().getText(R.string.share_with)));
        } else if (item.getItemId() == R.id.open_in_chrome) {
            openChromeCustomTab();
        }

        return true;
    }

    private void openChromeCustomTab() {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        CustomTabsIntent intent = builder.build();

        // get the extras that we passed into this activity (these will be all that were created
        // in the intent builder)
        intent.intent.putExtras(getIntent().getExtras());

        // launch the url with the specified intent
        intent.launchUrl(this, Uri.parse(url));

        // finish the current activity so that the back button takes us back
        finish();
    }

    private View.OnClickListener sideClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            finish();
        }
    };

}
