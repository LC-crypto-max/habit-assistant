package com.example.assistant.codexagent.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codex-data-agent")
public class CodexAgentProperties {

    private boolean enabled = true;
    private String mode = "local-first";
    private Authorization authorization = new Authorization();
    private Storage storage = new Storage();
    private Sources sources = new Sources();
    private Security security = new Security();
    private Privacy privacy = new Privacy();
    private Limits limits = new Limits();
    private Recommendation recommendation = new Recommendation();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Authorization getAuthorization() {
        return authorization;
    }

    public void setAuthorization(Authorization authorization) {
        this.authorization = authorization;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Sources getSources() {
        return sources;
    }

    public void setSources(Sources sources) {
        this.sources = sources;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Privacy getPrivacy() {
        return privacy;
    }

    public void setPrivacy(Privacy privacy) {
        this.privacy = privacy;
    }

    public Limits getLimits() {
        return limits;
    }

    public void setLimits(Limits limits) {
        this.limits = limits;
    }

    public Recommendation getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(Recommendation recommendation) {
        this.recommendation = recommendation;
    }

    public static class Authorization {
        private int defaultExpireDays = 180;

        public int getDefaultExpireDays() {
            return defaultExpireDays;
        }

        public void setDefaultExpireDays(int defaultExpireDays) {
            this.defaultExpireDays = defaultExpireDays;
        }
    }

    public static class Storage {
        private String rawPath = "data/raw";
        private String importsPath = "data/imports";
        private String processedPath = "data/processed";
        private String profilesPath = "data/profiles";
        private String recommendationsPath = "data/recommendations";

        public String getRawPath() {
            return rawPath;
        }

        public void setRawPath(String rawPath) {
            this.rawPath = rawPath;
        }

        public String getImportsPath() {
            return importsPath;
        }

        public void setImportsPath(String importsPath) {
            this.importsPath = importsPath;
        }

        public String getProcessedPath() {
            return processedPath;
        }

        public void setProcessedPath(String processedPath) {
            this.processedPath = processedPath;
        }

        public String getProfilesPath() {
            return profilesPath;
        }

        public void setProfilesPath(String profilesPath) {
            this.profilesPath = profilesPath;
        }

        public String getRecommendationsPath() {
            return recommendationsPath;
        }

        public void setRecommendationsPath(String recommendationsPath) {
            this.recommendationsPath = recommendationsPath;
        }
    }

    public static class Sources {
        private Source appUsageSummary = new Source("data/imports/app_usage_sample.json");
        private Source browserHistory = new Source("data/imports/browser_history_sample.json");
        private PublicUrlMetadata publicUrlMetadata = new PublicUrlMetadata();
        private LocalNotes localNotes = new LocalNotes();

        public Source getAppUsageSummary() {
            return appUsageSummary;
        }

        public void setAppUsageSummary(Source appUsageSummary) {
            this.appUsageSummary = appUsageSummary;
        }

        public Source getBrowserHistory() {
            return browserHistory;
        }

        public void setBrowserHistory(Source browserHistory) {
            this.browserHistory = browserHistory;
        }

        public PublicUrlMetadata getPublicUrlMetadata() {
            return publicUrlMetadata;
        }

        public void setPublicUrlMetadata(PublicUrlMetadata publicUrlMetadata) {
            this.publicUrlMetadata = publicUrlMetadata;
        }

        public LocalNotes getLocalNotes() {
            return localNotes;
        }

        public void setLocalNotes(LocalNotes localNotes) {
            this.localNotes = localNotes;
        }
    }

    public static class Source {
        private boolean enabled = true;
        private String sampleFile;

        public Source() {
        }

        public Source(String sampleFile) {
            this.sampleFile = sampleFile;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSampleFile() {
            return sampleFile;
        }

        public void setSampleFile(String sampleFile) {
            this.sampleFile = sampleFile;
        }
    }

    public static class PublicUrlMetadata {
        private boolean enabled = true;
        private int maxUrlsPerTask = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxUrlsPerTask() {
            return maxUrlsPerTask;
        }

        public void setMaxUrlsPerTask(int maxUrlsPerTask) {
            this.maxUrlsPerTask = maxUrlsPerTask;
        }
    }

    public static class LocalNotes {
        private boolean enabled = true;
        private List<String> allowedExtensions = new ArrayList<>(List.of(".md", ".txt", ".csv", ".json", ".html"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(List<String> allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }
    }

    public static class Security {
        private List<String> allowedPaths = new ArrayList<>(List.of("data/raw", "data/imports", "data/local-notes"));
        private List<String> allowedDomains = new ArrayList<>(List.of("xiaohongshu.com", "weixin.qq.com",
                "douyin.com", "youtube.com", "bilibili.com", "zhihu.com", "github.com", "csdn.net", "juejin.cn"));
        private List<String> allowedApps = new ArrayList<>(List.of("com.xingin.xhs", "com.tencent.mm",
                "com.ss.android.ugc.aweme", "com.google.android.youtube", "tv.danmaku.bili"));
        private List<String> blockedKeywords = new ArrayList<>(List.of("cookie", "token", "session", "password",
                "passwd", "authorization", "私信", "聊天记录", "通讯录", "支付记录", "微信数据库", "验证码"));

        public List<String> getAllowedPaths() {
            return allowedPaths;
        }

        public void setAllowedPaths(List<String> allowedPaths) {
            this.allowedPaths = allowedPaths;
        }

        public List<String> getAllowedDomains() {
            return allowedDomains;
        }

        public void setAllowedDomains(List<String> allowedDomains) {
            this.allowedDomains = allowedDomains;
        }

        public List<String> getAllowedApps() {
            return allowedApps;
        }

        public void setAllowedApps(List<String> allowedApps) {
            this.allowedApps = allowedApps;
        }

        public List<String> getBlockedKeywords() {
            return blockedKeywords;
        }

        public void setBlockedKeywords(List<String> blockedKeywords) {
            this.blockedKeywords = blockedKeywords;
        }
    }

    public static class Privacy {
        private boolean sanitize = true;
        private boolean keepRawText = false;
        private int maxTextLength = 500;

        public boolean isSanitize() {
            return sanitize;
        }

        public void setSanitize(boolean sanitize) {
            this.sanitize = sanitize;
        }

        public boolean isKeepRawText() {
            return keepRawText;
        }

        public void setKeepRawText(boolean keepRawText) {
            this.keepRawText = keepRawText;
        }

        public int getMaxTextLength() {
            return maxTextLength;
        }

        public void setMaxTextLength(int maxTextLength) {
            this.maxTextLength = maxTextLength;
        }
    }

    public static class Limits {
        private int maxRecordsPerTask = 300;
        private int maxFileSizeMb = 10;
        private int maxUrlPerTask = 20;

        public int getMaxRecordsPerTask() {
            return maxRecordsPerTask;
        }

        public void setMaxRecordsPerTask(int maxRecordsPerTask) {
            this.maxRecordsPerTask = maxRecordsPerTask;
        }

        public int getMaxFileSizeMb() {
            return maxFileSizeMb;
        }

        public void setMaxFileSizeMb(int maxFileSizeMb) {
            this.maxFileSizeMb = maxFileSizeMb;
        }

        public int getMaxUrlPerTask() {
            return maxUrlPerTask;
        }

        public void setMaxUrlPerTask(int maxUrlPerTask) {
            this.maxUrlPerTask = maxUrlPerTask;
        }
    }

    public static class Recommendation {
        private int topK = 10;
        private String defaultLanguage = "zh-CN";

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public String getDefaultLanguage() {
            return defaultLanguage;
        }

        public void setDefaultLanguage(String defaultLanguage) {
            this.defaultLanguage = defaultLanguage;
        }
    }
}
