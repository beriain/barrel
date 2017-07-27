package beriain.barrel;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Patterns;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebResourceResponse;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import android.widget.ListView;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.Editable;

public class MainActivity extends Activity
{

	WebView webview;
	Map<String, String> headers = new HashMap<String, String>();
    SharedPreferences preferences;
    ArrayList<String> adHosts = new ArrayList<>();
    ArrayList<String> history = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if(!preferences.contains("pref_accept_language"))
        	PreferenceManager.getDefaultSharedPreferences(this).edit().putString("pref_accept_language", Locale.getDefault().toString()).commit();
        if(!preferences.contains("pref_user_agent"))
        	PreferenceManager.getDefaultSharedPreferences(this).edit().putString("pref_user_agent", new WebView(this).getSettings().getUserAgentString()).commit();

        setAppLang();

        setContentView(R.layout.main);

        loadAdHosts();
        readHistory();
        
        webview = (WebView) findViewById(R.id.webview);
        webview.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        webview.setWebViewClient(new WebViewClient() {
			public boolean shouldOverrideUrlLoading(WebView view, String url)
			{
                if(preferences.getBoolean("pref_clear_cookies_between_domains", true))
                {
                    try {
                        EditText u = (EditText) findViewById(R.id.url);

                        String tmp1 = getDomainName(u.getText().toString());
                        int count = tmp1.length() - tmp1.replace(".", "").length();
                        while(count > 1)
                        {
                            tmp1 = tmp1.substring(tmp1.indexOf(".")+1);
                            count = tmp1.length() - tmp1.replace(".", "").length();
                        }
                        String tmp2 = getDomainName(url);

                        count = tmp2.length() - tmp2.replace(".", "").length();
                        while(count > 1)
                        {
                            tmp2 = tmp2.substring(tmp2.indexOf(".")+1);
                            count = tmp2.length() - tmp2.replace(".", "").length();
                        }
                        if(!tmp1.equalsIgnoreCase(tmp2))
                        {
                            deleteCookies();
                            view.clearCache(true);
                        }
                    } catch (URISyntaxException e) {}
                }

                view.loadUrl(url, headers);
				return false;
			}

            public void onPageFinished(WebView view, String url) {
                EditText u = (EditText) findViewById(R.id.url);
                u.setText(url);
                if(url.startsWith("https")) u.setTextColor(Color.parseColor("#4CAF50"));
                else u.setTextColor(Color.parseColor("#FFFFFF"));
                if(preferences.getBoolean("pref_remember_history", true))
                {
                    if(!history.contains(url)) history.add(url);
                }
				ListView suggestions = (ListView) findViewById(R.id.suggestions);
            	suggestions.setVisibility(View.GONE);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if(preferences.getBoolean("pref_block_ads", true))
                {
                    try {
                        EditText u = (EditText) findViewById(R.id.url);
                        String tmp = getDomainName(url);
                        //https://stackoverflow.com/a/8910767
                        int count = tmp.length() - tmp.replace(".", "").length();
                        while(count > 1)
                        {
                            tmp = tmp.substring(tmp.indexOf(".")+1);
                            count = tmp.length() - tmp.replace(".", "").length();
                        }

                        //allow request to self domain
                        if(getDomainName(u.getText().toString()).equalsIgnoreCase(tmp))
                            return super.shouldInterceptRequest(view, url);
                        else
                        {
                            if (isAd(tmp))
                                return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
                                //return super.shouldInterceptRequest(view, "");
                            else
                                return super.shouldInterceptRequest(view, url);
                        }
                    } catch (URISyntaxException e) {
                        return super.shouldInterceptRequest(view, url);
                    }
                }
                else return super.shouldInterceptRequest(view, url);
            }
		});

        webview.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                final String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(getApplicationContext(), R.string.downloading,
                        Toast.LENGTH_LONG).show();
            }
        });

        registerForContextMenu(webview);

        TextView url = (TextView) findViewById(R.id.url);
        TextView.OnEditorActionListener listener = new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView url, int actionId, KeyEvent event) {
                load();
                return true;
            }
        };
        url.setOnEditorActionListener(listener);
        
        url.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				displaySuggestions(s.toString());
			} 
		});

		ListView suggestions = (ListView) findViewById(R.id.suggestions);
        suggestions.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick (AdapterView<?> parent, View view, int position, long id) {
            	TextView url = (TextView) findViewById(R.id.url);
            	url.setText((String) parent.getItemAtPosition(position));
                load();
                ListView suggestions = (ListView) findViewById(R.id.suggestions);
            	suggestions.setVisibility(View.GONE);
            }
        });

        if(getIntent().getDataString() != null){
            url.setText(getIntent().getDataString());
            load();
        }

        if(preferences.getString("pref_start_page", "").length() > 2) {
            url.setText(preferences.getString("pref_start_page", " "));
            load();
        }

    }

    public boolean isAd(String url)
    {
    	boolean ad = false;
    		for(int x = 0; x < adHosts.size(); x++) {
    			if(adHosts.get(x).contains(url)) {
    				ad = true;
                    //Log.v("beriain.barrel.log", "AD: " + url);
    				break;
    			}
    		}
    		return ad;
    }
    
    //https://stackoverflow.com/a/9608008
    public static String getDomainName(String url) throws URISyntaxException {
		URI uri = new URI(url);
		String domain = uri.getHost();
		return domain.startsWith("www.") ? domain.substring(4) : domain;
	}
    
    public void displaySettings(View view)
    {
        Intent i = new Intent(MainActivity.this, SettingsActivity.class);
        MainActivity.this.startActivity(i);
    }
    
    public void goBack()
    {
        if(webview.canGoBack()) webview.goBack();
        else
        {
            if(preferences.getBoolean("pref_clear_data_on_exit", true)) {
                webview.clearHistory();
                webview.clearCache(true);
                webview.clearFormData();
            }
            if(preferences.getBoolean("pref_remember_history", true)) saveHistory();
            if(preferences.getBoolean("pref_clear_history_on_exit", false)) {
                history.clear();
                saveHistory();
            }
            finish();
        }
    }

    public void goForward()
    {
        if(webview.canGoForward()) webview.goForward();
    }

    public void reload()
    {
        webview.reload();
    }

    public void load()
    {
        Toast.makeText(getBaseContext(), R.string.loading, Toast.LENGTH_SHORT).show();
        EditText url = (EditText) findViewById(R.id.url);
        if(Patterns.WEB_URL.matcher("http://www." + url.getText()).matches())
            url.setText("http://www." + url.getText());
        else if(Patterns.WEB_URL.matcher(url.getText()).matches())
            url.setText(url.getText());
        else
            url.setText(preferences.getString("pref_search_engine", "https://duckduckgo.com/?q=") + url.getText());
        webview.loadUrl(url.getText().toString(), headers);
        url.clearFocus();
        webview.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(MainActivity.this.INPUT_METHOD_SERVICE);
        if(imm.isActive()) imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
    }

    public void checkPreferences()
    {
        String lang = preferences.getString("pref_accept_language", "");
        Boolean js = preferences.getBoolean("pref_javascript", true);
        String ua = preferences.getString("pref_user_agent", "");
        String dnt = preferences.getString("pref_dnt", "");
        Boolean c = preferences.getBoolean("pref_cookies", true);
        Boolean ls = preferences.getBoolean("pref_enable_localstorage", true);

        /*Toast.makeText(getBaseContext(), "lang=" + lang +
                "\njs=" + js +
                "\nua=" + ua +
                "\ndnt=" + dnt, Toast.LENGTH_LONG).show();*/

        setAppLang();
        webview.getSettings().setJavaScriptEnabled(js);
        webview.getSettings().setUserAgentString(ua);
        headers.put("Accept-Language", lang);
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("DNT", dnt);
        CookieManager.getInstance().setAcceptCookie(c);
        webview.getSettings().setDomStorageEnabled(ls);
    }
    
    @Override
	protected void onResume() {
		super.onResume();
		checkPreferences();
	}

	public void setAppLang()
    {
        String lang = preferences.getString("pref_accept_language", "");
        //Locale locale = new Locale("es");
        Locale locale = new Locale(lang);
        Configuration config = getBaseContext().getResources().getConfiguration();
        Locale.setDefault(locale);
        config.locale = locale;
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
    }

    public void displayMenu(View v){
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenuInflater().inflate(R.menu.menu, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                	case R.id.share:
                		share();
                		return true;
                    case R.id.back:
                        goBack();
                        return true;
                    case R.id.forward:
                        goForward();
                        return true;
                    case R.id.reload:
                        reload();
                        return true;
                    case R.id.exit:
                        if(preferences.getBoolean("pref_clear_data_on_exit", true)) {
                            webview.clearHistory();
                            webview.clearCache(true);
                            webview.clearFormData();
                        }
                        if(preferences.getBoolean("pref_remember_history", true)) saveHistory();
                        if(preferences.getBoolean("pref_clear_history_on_exit", false)) {
                            history.clear();
                            saveHistory();
                        }
                        finish();
                }
                return true;
            }
        });
        popup.show();
    }

    @Override
    public void onBackPressed()
    {
        goBack();
    }

    public void loadAdHosts()
    {
        try {
            InputStreamReader isr = new InputStreamReader(this.getAssets().open("simple_ad.txt"));
            BufferedReader reader = new BufferedReader(isr);
            String h;
            while ((h = reader.readLine()) != null) {
                adHosts.add(h);
            }
            reader.close();
            isr.close();
        } catch (IOException e) {

        }
    }

    public void deleteCookies()
    {
        CookieSyncManager csm = CookieSyncManager.createInstance(getApplicationContext());
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookie();
        cm.removeSessionCookie();
        csm.stopSync();
        csm.sync();
    }
    
    public void share()
    {
    	EditText url = (EditText) findViewById(R.id.url);
    	Intent i = new Intent(Intent.ACTION_SEND);
		i.setType("text/plain");
		i.putExtra(Intent.EXTRA_SUBJECT, url.getText().toString());
		i.putExtra(Intent.EXTRA_TEXT, url.getText().toString());
		startActivity(Intent.createChooser(i, url.getText().toString()));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        checkPreferences();
    }

    public void displaySuggestions(String s)
    {
    	ArrayList<String> tmp = new ArrayList<String>();
    	for(int x = 0; x < history.size(); x++)
    		if(history.get(x).contains(s)) tmp.add(history.get(x));
        ListView suggestions = (ListView) findViewById(R.id.suggestions);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                 this, android.R.layout.simple_list_item_1, tmp);
        suggestions.setAdapter(adapter);
        if(s.length() >= 1) suggestions.setVisibility(View.VISIBLE);
        else suggestions.setVisibility(View.GONE);
    }

    public void saveHistory()
    {
        try {
            FileOutputStream fos = this.openFileOutput("history", this.MODE_PRIVATE);
            ObjectOutputStream of = new ObjectOutputStream(fos);
            of.writeObject(history);
            of.flush();
            of.close();
            fos.close();
        }
        catch (Exception e) {
            Log.e("beriain.barrel.log", "Exception saving history: " + e.getMessage());
        }
    }

    public void readHistory() {
        FileInputStream fis;
        try {
            fis = this.openFileInput("history");
            ObjectInputStream oi = new ObjectInputStream(fis);
            history = (ArrayList<String>) oi.readObject();
            oi.close();
        } catch (Exception e) {
            Log.e("beriain.barrel.log", "Exception reading history: " + e.getMessage());
        }
    }

    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        final WebView.HitTestResult hitTestResult = webview.getHitTestResult();
           if(hitTestResult.getType() == WebView.HitTestResult.IMAGE_TYPE ||
                   hitTestResult.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
               new AlertDialog.Builder(this)
                   .setTitle(R.string.download)
                   .setMessage(getString(R.string.download) + " " + hitTestResult.getExtra() + "?")
                   .setIcon(android.R.drawable.ic_dialog_info)
                   .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int whichButton) {
                           DownloadManager.Request request = new DownloadManager.Request(Uri.parse(hitTestResult.getExtra()));
                           request.allowScanningByMediaScanner();
                           request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                           final String filename = URLUtil.guessFileName(hitTestResult.getExtra(),
                                   null,
                                   hitTestResult.getExtra().substring(hitTestResult.getExtra().lastIndexOf(".")));
                           request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                           DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                           dm.enqueue(request);
                           Toast.makeText(getApplicationContext(), R.string.downloading, Toast.LENGTH_LONG).show();
                       }})
                   .setNegativeButton(android.R.string.no, null).show();
               //Toast.makeText(getBaseContext(), hitTestResult.getExtra(), Toast.LENGTH_SHORT).show();
           }
    }
}
