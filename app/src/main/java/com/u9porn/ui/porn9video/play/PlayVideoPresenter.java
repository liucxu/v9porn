package com.u9porn.ui.porn9video.play;

import android.annotation.SuppressLint;
import android.app.Application;
import android.arch.lifecycle.Lifecycle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.hannesdorfmann.mosby3.mvp.MvpBasePresenter;
import com.orhanobut.logger.Logger;
import com.sdsmdg.tastytoast.TastyToast;
import com.trello.rxlifecycle2.LifecycleProvider;
import com.u9porn.MyApplication;
import com.u9porn.data.DataManager;
import com.u9porn.data.db.entity.V9PornItem;
import com.u9porn.data.db.entity.VideoResult;
import com.u9porn.data.model.User;
import com.u9porn.data.network.Api;
import com.u9porn.data.network.okhttp.HeaderUtils;
import com.u9porn.exception.VideoException;
import com.u9porn.rxjava.CallBackWrapper;
import com.u9porn.rxjava.RetryWhenProcess;
import com.u9porn.rxjava.RxSchedulersHelper;
import com.u9porn.ui.download.DownloadPresenter;
import com.u9porn.ui.porn9video.favorite.FavoritePresenter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.internal.operators.observable.ObservableFromCallable;
import io.reactivex.schedulers.Schedulers;

/**
 * @author flymegoc
 * @date 2017/11/15
 * @describe play
 */
public class PlayVideoPresenter extends MvpBasePresenter<PlayVideoView> implements IPlay {

    private static final String TAG = PlayVideoPresenter.class.getSimpleName();

    private FavoritePresenter favoritePresenter;
    private DownloadPresenter downloadPresenter;

    private LifecycleProvider<Lifecycle.Event> provider;

    private DataManager dataManager;

//    class InJavaScriptLocalObj {
//        @JavascriptInterface
//        public void showSource(String html) {
//            Logger.d("HTML", html);
//        }
//    }
//
//    private Handler mHandler=new Handler(){
//        @Override
//        public void handleMessage(Message msg) {
//            if(msg.what==0){
//                V9PornItem v9PornItem=(V9PornItem)msg.obj;
//                ifViewAttached(view -> view.parseVideoUrlSuccess(v9PornItem));
//            }
//            else if(msg.what==1){
//                ifViewAttached(view -> view.errorParseVideoUrl("解析视频链接失败了"));
//            }
//        }
//    };

//    private final WebView webView;

    @Inject
    public PlayVideoPresenter(FavoritePresenter favoritePresenter, DownloadPresenter downloadPresenter, LifecycleProvider<Lifecycle.Event> provider, DataManager dataManager) {
        this.favoritePresenter = favoritePresenter;
        this.downloadPresenter = downloadPresenter;
        this.provider = provider;
        this.dataManager = dataManager;
    }

    @SuppressLint("JavascriptInterface")
    @Override
    public void loadVideoUrl(final V9PornItem v9PornItem) {
        String viewKey = v9PornItem.getViewKey();
        dataManager.loadPorn9VideoUrl(viewKey)
                .map(videoResult -> {
                    if (TextUtils.isEmpty(videoResult.getVideoUrl())) {
                        if (VideoResult.OUT_OF_WATCH_TIMES.equals(videoResult.getId())) {
                            //尝试强行重置，并上报异常
                            dataManager.resetPorn91VideoWatchTime(true);
                            // Bugsnag.notify(new Throwable(TAG + "Ten videos each day address: " + dataManager.getPorn9VideoAddress()), Severity.WARNING);
                            throw new VideoException("观看次数达到上限了,请更换地址或者代理服务器！");
                        } else if (VideoResult.VIDEO_NOT_EXIST_OR_DELETE.equals(videoResult.getId())) {
                            throw new VideoException("视频不存在,可能已经被删除或者被举报为不良内容!");
                        } else {
                            throw new VideoException("解析视频链接失败了");
                        }
                    }
                    return videoResult;
                })
                .retryWhen(new RetryWhenProcess(RetryWhenProcess.PROCESS_TIME))
                .compose(RxSchedulersHelper.ioMainThread())
               // .subscribeOn(AndroidSchedulers.mainThread()).observeOn(AndroidSchedulers.mainThread())
                .compose(provider.bindUntilEvent(Lifecycle.Event.ON_DESTROY))
                .subscribe(new CallBackWrapper<VideoResult>() {
                    @Override
                    public void onBegin(Disposable d) {
                        ifViewAttached(PlayVideoView::showParsingDialog);
                    }

                    @Override
                    public void onSuccess(final VideoResult videoResult) {
                        dataManager.resetPorn91VideoWatchTime(false);
                        ifViewAttached(view -> view.parseVideoUrlSuccess(saveVideoUrl(videoResult, v9PornItem)));
                        if(videoResult.getUid()!=0){
                            dataManager.getUser().setUserId(videoResult.getUid());
                        }
                    }

                    @Override
                    public void onError(final String msg, int code) {
                        ifViewAttached(view -> view.errorParseVideoUrl(msg));
                    }
                });
    }

    private boolean parseViedoUrl(VideoResult videoResult,String html){
        if(TextUtils.isEmpty(html)){
            return false;
        }
        else{
            Document document = Jsoup.parse(html);
            Element element = document.getElementById("player_one");
            if(element==null){
                return false;
            }
            if(element.getElementById("player_one_html5_api")==null){
                return false;
            }
            String videoUrl=element.getElementById("player_one_html5_api").attr("src");
            if(TextUtils.isEmpty(videoUrl)){
                return false;
            }
            videoResult.setVideoUrl(videoUrl);
            return true;
        }
    }

    /**
     * 需要在UI线程执行
     * 借助webView, 动态加载md5.js，传入相关的参数也是可用解析得到地址
     *
     * @param mWebView webView
     */
    private void decodeUrl(WebView mWebView) {
        String a = "MXoqQlMPfiwrPSYKNCFiWwVRCldRCgZffBdgKTZzBiYiNlU/IgcMQXwuPU8CT2FbLAkTS3hVGAQoHjEQOSFzQBYCKFwOfStgHCECTmZyMhg+YXovMAwdEjw6Lw8GVzQmDBAMIjYSPAsnHQ1YJTUjLx0gTFQFCScoIQQ9RgIlD0wLf3EIbAY9BCF2d0cvcQcf";
        String b = "a2d47W4FqndpWL/bOcbg5BGi0nXQy7SSoL2JoSA41zp8N6X/OMB14/UsfdVgtHF4uFysmNzYKtez57ZIkSKFTKKEfVuUbgXJZGdVcAfgwIHikanWSt+eKMrFhLosabZuAL+x6AkrmDF0";
        //Javascript返回add()函数的计算结果。
        mWebView.evaluateJavascript("parserVideoUrl('" + a + "','" + b + "')", value -> {
            Logger.t(TAG).d(value);
            if (TextUtils.isEmpty(value)) {
                return;
            }
            Document source = Jsoup.parse(value.replace("\\u003C", "<"));
            String videoUrl = source.select("source").first().attr("src");
            Logger.t(TAG).d(videoUrl);
        });
    }

    @Override
    public String getVideoCacheProxyUrl(String originalVideoUrl) {
        return dataManager.getVideoCacheProxyUrl(originalVideoUrl);
    }

    @Override
    public boolean isUserLogin() {
        return dataManager.isUserLogin();
    }

    @Override
    public int getLoginUserId() {
        return dataManager.getUser().getUserId();
    }

    @Override
    public void updateV9PornItemForHistory(V9PornItem v9PornItem) {
        dataManager.updateV9PornItem(v9PornItem);
    }

    @Override
    public V9PornItem findV9PornItemByViewKey(String viewKey) {
        return dataManager.findV9PornItemByViewKey(viewKey);
    }

    @Override
    public void setFavoriteNeedRefresh(boolean favoriteNeedRefresh) {
        dataManager.setFavoriteNeedRefresh(favoriteNeedRefresh);
    }

    private V9PornItem saveVideoUrl(VideoResult videoResult, V9PornItem v9PornItem) {
        dataManager.saveVideoResult(videoResult);
        v9PornItem.setVideoResult(videoResult);
        v9PornItem.setViewHistoryDate(new Date());
        dataManager.saveV9PornItem(v9PornItem);
        return v9PornItem;
    }

    @Override
    public void downloadVideo(V9PornItem v9PornItem, boolean isForceReDownload) {

        downloadPresenter.downloadVideo(v9PornItem, isForceReDownload, new DownloadPresenter.DownloadListener() {
            @Override
            public void onSuccess(final String message) {
                ifViewAttached(view -> view.showMessage(message, TastyToast.SUCCESS));
            }

            @Override
            public void onError(final String message) {
                ifViewAttached(view -> view.showMessage(message, TastyToast.ERROR));
            }
        });
    }

    @Override
    public void favorite(String uId, String videoId, String uvid) {
        favoritePresenter.favorite(uId, videoId, uvid, new FavoritePresenter.FavoriteListener() {
            @Override
            public void onSuccess(String message) {
                ifViewAttached(PlayVideoView::favoriteSuccess);
            }

            @Override
            public void onError(final String message) {
                ifViewAttached(view -> view.showError(message));
            }
        });
    }


    /**
     * 是否需要为了解析uid，只有登录状态下且uid还未解析过才需要解析
     *
     * @return true
     */
    public boolean isLoadForUid() {
        User user = dataManager.getUser();
        return dataManager.isUserLogin() && user.getUserId() == 0;
    }
}
