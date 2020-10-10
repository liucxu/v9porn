package com.u9porn.parser.v9porn.d20201009;

import android.util.Base64;

import com.orhanobut.logger.Logger;
import com.u9porn.data.db.entity.VideoResult;
import com.u9porn.data.model.User;
import com.u9porn.parser.v9porn.BaseVideoPlayUrlParser;
import com.u9porn.parser.v9porn.VideoPlayUrlParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

public class VideoUrlParser extends BaseVideoPlayUrlParser implements VideoPlayUrlParser {

    @Inject
    public VideoUrlParser() {

    }

    @Override
    public VideoResult parseVideoPlayUrl(String html, User user) {
        VideoResult videoResult = new VideoResult();
        Document document = Jsoup.parse(html);
//        Logger.t("AAA").d(html);
        Element e = document.getElementById("player_one").selectFirst("script");
        String script = e.outerHtml();

        String aac = script.substring(script.indexOf("strencode(") + "strencode(".length() + 1, script.indexOf(")") - 1);
        String[] aar = aac.replaceAll("\"", "").split(",");

        String param1 = aar[0], param2 = aar[1], param3 = aar[2];
        if (param3.substring(param3.length() - 1).equals("2")) {
            String tmp = param1;
            param1 = param2;
            param2 = tmp;
        }
        param1 = new String(Base64.decode(param1.getBytes(), Base64.DEFAULT));
        StringBuilder source_str = new StringBuilder();
        for (int i = 0, k = 0; i < param1.length(); i++) {
            k = i % param2.length();
            source_str.append("").append((char) (param1.codePointAt(i) ^ param2.codePointAt(k)));
        }
        Logger.t(TAG).d("视频source1：" + source_str);
        parserOtherInfo(document, videoResult, user);
        source_str = new StringBuilder(new String(Base64.decode(source_str.toString().getBytes(), Base64.DEFAULT)));
        Logger.t(TAG).d("视频source2：" + source_str);
        String videoUrl = source_str.substring(source_str.indexOf("src='") + 5, source_str.indexOf("' type"));
        System.out.println("视频地址:" + videoUrl);
        videoResult.setVideoUrl(videoUrl);
        return videoResult;
    }
}
