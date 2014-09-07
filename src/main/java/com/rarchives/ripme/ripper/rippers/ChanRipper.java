package com.rarchives.ripme.ripper.rippers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.rarchives.ripme.ripper.AbstractHTMLRipper;
import com.rarchives.ripme.ripper.rippers.ripperhelpers.ChanSite;
import com.rarchives.ripme.utils.Http;
import java.util.Arrays;

public class ChanRipper extends AbstractHTMLRipper {
    
    //ArrayList<String> explicit_domains = new ArrayList<String>();
    public static List<ChanSite> explicit_domains = Arrays.asList(
        //Tested (main boards)
        //Untested (main boards)
        new ChanSite(Arrays.asList("anon-ib.com")),
        new ChanSite(Arrays.asList("boards.4chan.org"),Arrays.asList("4cdn.org")),
        //Tested (archives)
        new ChanSite(Arrays.asList("archive.moe"),Arrays.asList("data.archive.moe")), //4chan archive (successor of foolz archive) Archives: [ a / biz / c / co / diy / gd / i / int / jp / m / mlp / out / po / q / s4s / sci / sp / tg / tv / v / vg / vp / vr / wsg ]  
        //Untested (archives)new ChanSite(Arrays.asList("anon-ib.com")),
        new ChanSite(Arrays.asList("4archive.org"),Arrays.asList("imgur.com")), //4chan archive (on demand)
        new ChanSite(Arrays.asList("archive.4plebs.org"),Arrays.asList("img.4plebs.org")), //4chan archive Archives: [ adv / f / hr / o / pol / s4s / tg / trv / tv / x ] Boards: [ plebs ]  
        new ChanSite(Arrays.asList("fgts.jp"),Arrays.asList("dat.fgts.jp")) //4chan archive Archives: [ asp / cm / h / hc / hm / n / p / r / s / soc / y ]
        );
    public static List<String> url_piece_blacklist = Arrays.asList(
        "=http",
        "http://imgops.com/",
        "iqdb.org",
        "saucenao.com"
        );
    
    public ChanSite chanSite;
    public Boolean generalChanSite = true;
    
    public ChanRipper(URL url) throws IOException {
        super(url);
        for (ChanSite _chanSite : explicit_domains) {
            for (String host : _chanSite.domains) {
                if (url.getHost().equals(host)) {
                    chanSite = _chanSite;
                    generalChanSite = false;
                }
            }
        }
        if(chanSite==null){
            chanSite = new ChanSite(Arrays.asList("url.getHost()"));
        }        
    }

    @Override
    public String getHost() {
        String host = this.url.getHost();
        host = host.substring(0, host.lastIndexOf('.'));
        if (host.contains(".")) {
            // Host has subdomain (www)
            host = host.substring(host.lastIndexOf('.') + 1);
        }
        String board = this.url.toExternalForm().split("/")[3];
        return host + "_" + board;
    }

    @Override
    public boolean canRip(URL url) {        
        //explicit_domains testing 
        for (ChanSite _chanSite : explicit_domains) {
            for (String host : _chanSite.domains) {
                if (url.getHost().equals(host)) {
                    return true;
                }
            } 
        }
        //It'll fail further down the road.
        return  url.toExternalForm().contains("/res/")      // Most chans
               || url.toExternalForm().contains("/thread/"); // 4chan, archive.moe
    }
    /**
     * For example the achrives are all known. (Check 4chan-x)
     * Should be based on the software the specific chan uses.
     * FoolFuuka uses the same (url) layout as 4chan
     * */
    @Override
    public String getGID(URL url) throws MalformedURLException {
        Pattern p; Matcher m;

        String u = url.toExternalForm();        
        if (u.contains("/thread/")||u.contains("/res/")) {
            p = Pattern.compile("^.*\\.[a-z]{1,3}/[a-zA-Z0-9]+/(thread|res)/([0-9]+)(\\.html|\\.php)?.*$");
            m = p.matcher(u);
            if (m.matches()) {
                return m.group(2);
            }
        }

        throw new MalformedURLException(
                "Expected *chan URL formats: "
                        + ".*/@/(res|thread)/####.html"
                        + " Got: " + u);
    }

    @Override
    public String getDomain() {
        return this.url.getHost();
    }

    @Override
    public Document getFirstPage() throws IOException {
        return Http.url(this.url).get();
    }

    @Override
    public List<String> getURLsFromPage(Document page) {
        List<String> imageURLs = new ArrayList<String>();
        Pattern p; Matcher m;
        elementloop:
        for (Element link : page.select("a")) {
            if (!link.hasAttr("href")) { 
                continue;
            }
            String href = link.attr("href");
            
            //Check all blacklist items
            for(String blacklist_item : url_piece_blacklist){                
                if (href.contains(blacklist_item)){
                    logger.debug("Skipping link that contains '"+blacklist_item+"': " + href);
                    continue elementloop;
                }            
            }
            Boolean self_hosted = false;
            if(!generalChanSite){              
                for(String cdnDomain : chanSite.cdnDomains){                
                    if (href.contains(cdnDomain)){                    
                        self_hosted = true;
                    }            
                }   
            }
            if(self_hosted||generalChanSite){
                p = Pattern.compile("^.*\\.(jpg|jpeg|png|gif|apng|webp|tif|tiff|webm)$", Pattern.CASE_INSENSITIVE);
                m = p.matcher(href);
                if (m.matches()) {                
                    if (href.startsWith("//")) {
                        href = "http:" + href;
                    }
                    if (href.startsWith("/")) {
                        href = "http://" + this.url.getHost() + href;
                    }
                    // Don't download the same URL twice
                    if (imageURLs.contains(href)) {
                        logger.debug("Already attempted: " + href);
                        continue;
                    }
                    imageURLs.add(href);
                }
            } else {
                //TODO also grab imgur/flickr albums (And all other supported rippers) Maybe add a setting?
            }            
        }
        return imageURLs;
    }

    @Override
    public void downloadURL(URL url, int index) {
        addURLToDownload(url, getPrefix(index));
    } 
}
