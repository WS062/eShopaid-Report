package com.alkhamis.pos;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.print.*;
import android.webkit.*;
import android.widget.Toast;
import android.widget.EditText;
import android.text.InputType;
import android.webkit.SslErrorHandler;

public class MainActivity extends Activity {
    static final String HOME="https://pos.alkhamisshoes.com/shopaid_ReportDesigner/Login/Login.aspx";
    static final int FILE_REQ=1001;
    WebView web; ValueCallback<Uri[]> fileCb; SharedPreferences prefs; String home;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        web=new WebView(this); setContentView(web); setup(web, null);
        prefs=getSharedPreferences("cfg",MODE_PRIVATE);
        home=prefs.getString("url",null);
        if(b!=null) web.restoreState(b);
        else if(home==null) askUrl(true);
        else web.loadUrl(home);
    }

    void setup(final WebView w, final Dialog owner){
        WebSettings s=w.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true); s.setLoadWithOverviewMode(true); s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true); s.setSupportMultipleWindows(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(s.getUserAgentString().replace("; wv",""));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w,true);

        w.addJavascriptInterface(new Object(){
            @JavascriptInterface public void print(){
                runOnUiThread(()->((PrintManager)getSystemService(PRINT_SERVICE)).print("Report",
                    w.createPrintDocumentAdapter("Report"),new PrintAttributes.Builder().build()));
            }
        },"AndroidPrint");

        w.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){
                if(r.getUrl().toString().startsWith("http")) return false;
                try{startActivity(new Intent(Intent.ACTION_VIEW,r.getUrl()));}catch(Exception e){}
                return true;
            }
            @Override public void onReceivedSslError(WebView v, SslErrorHandler h, android.net.http.SslError e){
                String host=Uri.parse(e.getUrl()).getHost();
                String mine=home==null?null:Uri.parse(home).getHost();
                if(host!=null && (host.endsWith("alkhamisshoes.com") || host.equals(mine))) h.proceed();
                else { h.cancel(); showErr("Certificate error: "+e.getPrimaryError()+"\n"+e.getUrl()); }
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e){
                if(r.isForMainFrame()) showErr("Error "+e.getErrorCode()+": "+e.getDescription()+"\n"+r.getUrl());
            }
            @Override public void onReceivedHttpError(WebView v, WebResourceRequest r, WebResourceResponse e){
                if(r.isForMainFrame()) showErr("Server error "+e.getStatusCode()+" "+e.getReasonPhrase()+"\n"+r.getUrl());
            }
            @Override public void onPageFinished(WebView v,String u){
                CookieManager.getInstance().flush();
                v.evaluateJavascript("window.print=function(){AndroidPrint.print();};",null);
            }
        });

        w.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onCreateWindow(WebView v,boolean d,boolean u,Message msg){
                WebView child=new WebView(MainActivity.this);
                Dialog dlg=new Dialog(MainActivity.this,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
                setup(child, dlg);
                dlg.setContentView(child);
                dlg.setOnKeyListener((di,k,e)->{
                    if(k==android.view.KeyEvent.KEYCODE_BACK&&e.getAction()==android.view.KeyEvent.ACTION_UP){
                        if(child.canGoBack()) child.goBack(); else dlg.dismiss(); return true;}
                    return false;});
                dlg.show();
                ((WebView.WebViewTransport)msg.obj).setWebView(child); msg.sendToTarget();
                return true;
            }
            @Override public void onCloseWindow(WebView v){ if(owner!=null) owner.dismiss(); }
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){
                if(fileCb!=null) fileCb.onReceiveValue(null);
                fileCb=cb;
                try{startActivityForResult(p.createIntent(),FILE_REQ);}catch(Exception e){fileCb=null;return false;}
                return true;
            }
        });

        w.setDownloadListener((url,ua,cd,mime,len)->{
            if(url.startsWith("blob:")){Toast.makeText(this,"Use Print/Export option",Toast.LENGTH_LONG).show();return;}
            try{
                DownloadManager.Request req=new DownloadManager.Request(Uri.parse(url));
                req.setMimeType(mime);
                req.addRequestHeader("Cookie",CookieManager.getInstance().getCookie(url));
                req.addRequestHeader("User-Agent",ua);
                String name=URLUtil.guessFileName(url,cd,mime);
                req.setTitle(name);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name);
                ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                Toast.makeText(this,"Downloading "+name,Toast.LENGTH_SHORT).show();
            }catch(Exception e){ startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
        });
    }

    void askUrl(boolean first){
        final EditText et=new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        et.setSingleLine(true);
        et.setText(home!=null?home:HOME);
        et.setSelectAllOnFocus(true);
        AlertDialog.Builder bld=new AlertDialog.Builder(this)
            .setTitle("Enter server URL").setMessage("Login page address").setView(et)
            .setCancelable(!first)
            .setPositiveButton("Save",(d,w)->{
                String u=et.getText().toString().trim();
                if(u.isEmpty()){ askUrl(first); return; }
                if(!u.startsWith("http")) u="https://"+u;
                home=u; prefs.edit().putString("url",u).apply();
                web.clearHistory(); web.loadUrl(u);
            });
        if(!first) bld.setNegativeButton("Cancel",null);
        bld.show();
    }

    void showErr(String m){
        runOnUiThread(()->{ if(isFinishing()) return;
            new AlertDialog.Builder(this).setTitle("Cannot open page").setMessage(m).setCancelable(false)
            .setPositiveButton("Retry",(d,w)->{ if(home==null) askUrl(true); else if(web.getUrl()==null||web.getUrl().startsWith("about")) web.loadUrl(home); else web.reload(); })
            .setNeutralButton("Change URL",(d,w)->askUrl(false))
            .setNegativeButton("Close",null).show(); });
    }

    @Override protected void onActivityResult(int rq,int rs,Intent d){
        super.onActivityResult(rq,rs,d);
        if(rq==FILE_REQ&&fileCb!=null){fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(rs,d));fileCb=null;}
    }
    @Override protected void onSaveInstanceState(Bundle o){super.onSaveInstanceState(o);web.saveState(o);}
    @Override public void onBackPressed(){
        if(web.canGoBack()){ web.goBack(); return; }
        new AlertDialog.Builder(this).setTitle("eShopaid Mobile Reports")
            .setPositiveButton("Exit",(d,w)->finish())
            .setNeutralButton("Change URL",(d,w)->askUrl(false))
            .setNegativeButton("Cancel",null).show();
    }
}
