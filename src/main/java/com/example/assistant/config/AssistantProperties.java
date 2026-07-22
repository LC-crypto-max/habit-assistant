package com.example.assistant.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant")
public class AssistantProperties {

    private String userId = "me";
    private String dailyCron = "0 0 8 * * ?";
    private List<String> keywords = new ArrayList<>();
    private HistoryImport historyImport = new HistoryImport();
    private Collectors collectors = new Collectors();
    private Feishu feishu = new Feishu();
    private RecommendationRefresh recommendationRefresh = new RecommendationRefresh();
    private LocalWorker localWorker = new LocalWorker();
    private Auth auth = new Auth();

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDailyCron() {
        return dailyCron;
    }

    public void setDailyCron(String dailyCron) {
        this.dailyCron = dailyCron;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public HistoryImport getHistoryImport() {
        return historyImport;
    }

    public void setHistoryImport(HistoryImport historyImport) {
        this.historyImport = historyImport;
    }

    public Collectors getCollectors() {
        return collectors;
    }

    public void setCollectors(Collectors collectors) {
        this.collectors = collectors;
    }

    public Feishu getFeishu() {
        return feishu;
    }

    public void setFeishu(Feishu feishu) {
        this.feishu = feishu;
    }

    public RecommendationRefresh getRecommendationRefresh() {
        return recommendationRefresh;
    }

    public void setRecommendationRefresh(RecommendationRefresh recommendationRefresh) {
        this.recommendationRefresh = recommendationRefresh;
    }

    public LocalWorker getLocalWorker() {
        return localWorker;
    }

    public void setLocalWorker(LocalWorker localWorker) {
        this.localWorker = localWorker;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public static class HistoryImport {
        private boolean enabled = false;
        private String cron = "0 30 * * * ?";
        private String directory = "./imports/history";
        private String archiveDirectory = "./imports/history/archive";
        private String failedDirectory = "./imports/history/failed";
        private String filePattern = "*.csv";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public String getArchiveDirectory() {
            return archiveDirectory;
        }

        public void setArchiveDirectory(String archiveDirectory) {
            this.archiveDirectory = archiveDirectory;
        }

        public String getFailedDirectory() {
            return failedDirectory;
        }

        public void setFailedDirectory(String failedDirectory) {
            this.failedDirectory = failedDirectory;
        }

        public String getFilePattern() {
            return filePattern;
        }

        public void setFilePattern(String filePattern) {
            this.filePattern = filePattern;
        }
    }

    public static class Collectors {
        private Rss rss = new Rss();
        private JsonApi jsonApi = new JsonApi();
        private X x = new X();

        public Rss getRss() {
            return rss;
        }

        public void setRss(Rss rss) {
            this.rss = rss;
        }

        public JsonApi getJsonApi() {
            return jsonApi;
        }

        public void setJsonApi(JsonApi jsonApi) {
            this.jsonApi = jsonApi;
        }

        public X getX() {
            return x;
        }

        public void setX(X x) {
            this.x = x;
        }
    }

    public static class Rss {
        private boolean enabled = false;
        private List<RssFeed> feeds = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<RssFeed> getFeeds() {
            return feeds;
        }

        public void setFeeds(List<RssFeed> feeds) {
            this.feeds = feeds;
        }
    }

    public static class RssFeed {
        private String platform = "rss";
        private String url;
        private List<String> tags = new ArrayList<>();

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    public static class JsonApi {
        private boolean enabled = false;
        private List<JsonSource> sources = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<JsonSource> getSources() {
            return sources;
        }

        public void setSources(List<JsonSource> sources) {
            this.sources = sources;
        }
    }

    public static class JsonSource {
        private String platform = "json";
        private String endpointTemplate;
        private Map<String, String> headers = new LinkedHashMap<>();
        private String resultsPointer = "/items";
        private String titlePointer = "/title";
        private String urlPointer = "/link";
        private String summaryPointer = "/snippet";
        private String authorPointer = "/author";
        private String publishedAtPointer = "/publishedAt";
        private List<String> tags = new ArrayList<>();

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

        public String getEndpointTemplate() {
            return endpointTemplate;
        }

        public void setEndpointTemplate(String endpointTemplate) {
            this.endpointTemplate = endpointTemplate;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }

        public String getResultsPointer() {
            return resultsPointer;
        }

        public void setResultsPointer(String resultsPointer) {
            this.resultsPointer = resultsPointer;
        }

        public String getTitlePointer() {
            return titlePointer;
        }

        public void setTitlePointer(String titlePointer) {
            this.titlePointer = titlePointer;
        }

        public String getUrlPointer() {
            return urlPointer;
        }

        public void setUrlPointer(String urlPointer) {
            this.urlPointer = urlPointer;
        }

        public String getSummaryPointer() {
            return summaryPointer;
        }

        public void setSummaryPointer(String summaryPointer) {
            this.summaryPointer = summaryPointer;
        }

        public String getAuthorPointer() {
            return authorPointer;
        }

        public void setAuthorPointer(String authorPointer) {
            this.authorPointer = authorPointer;
        }

        public String getPublishedAtPointer() {
            return publishedAtPointer;
        }

        public void setPublishedAtPointer(String publishedAtPointer) {
            this.publishedAtPointer = publishedAtPointer;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    public static class X {
        private boolean enabled = false;
        private String bearerToken;
        private int maxResults = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBearerToken() {
            return bearerToken;
        }

        public void setBearerToken(String bearerToken) {
            this.bearerToken = bearerToken;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }
    }

    public static class Feishu {
        private String webhookUrl;

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }
    }

    public static class RecommendationRefresh {
        private int activityWindowHours = 24;
        private int mediumActivityThreshold = 20;
        private int highActivityThreshold = 80;
        private int lowActivityHours = 12;
        private int mediumActivityHours = 12;
        private int highActivityHours = 12;

        public int getActivityWindowHours() {
            return activityWindowHours;
        }

        public void setActivityWindowHours(int activityWindowHours) {
            this.activityWindowHours = activityWindowHours;
        }

        public int getMediumActivityThreshold() {
            return mediumActivityThreshold;
        }

        public void setMediumActivityThreshold(int mediumActivityThreshold) {
            this.mediumActivityThreshold = mediumActivityThreshold;
        }

        public int getHighActivityThreshold() {
            return highActivityThreshold;
        }

        public void setHighActivityThreshold(int highActivityThreshold) {
            this.highActivityThreshold = highActivityThreshold;
        }

        public int getLowActivityHours() {
            return lowActivityHours;
        }

        public void setLowActivityHours(int lowActivityHours) {
            this.lowActivityHours = lowActivityHours;
        }

        public int getMediumActivityHours() {
            return mediumActivityHours;
        }

        public void setMediumActivityHours(int mediumActivityHours) {
            this.mediumActivityHours = mediumActivityHours;
        }

        public int getHighActivityHours() {
            return highActivityHours;
        }

        public void setHighActivityHours(int highActivityHours) {
            this.highActivityHours = highActivityHours;
        }
    }

    public static class LocalWorker {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:8080";
        private String scriptPath = "scripts/codex_query_worker.py";
        private String pythonCommand = "auto";
        private int limit = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getScriptPath() {
            return scriptPath;
        }

        public void setScriptPath(String scriptPath) {
            this.scriptPath = scriptPath;
        }

        public String getPythonCommand() {
            return pythonCommand;
        }

        public void setPythonCommand(String pythonCommand) {
            this.pythonCommand = pythonCommand;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }
    }

    public static class Auth {
        private boolean enabled = false;
        private List<AuthUser> users = new ArrayList<>(List.of(
                new AuthUser("alice", "alice123", false),
                new AuthUser("bob", "bob123", false),
                new AuthUser("admin", "admin123", true)));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<AuthUser> getUsers() {
            return users;
        }

        public void setUsers(List<AuthUser> users) {
            this.users = users;
        }
    }

    public static class AuthUser {
        private String userId;
        private String password;
        private boolean admin;

        public AuthUser() {
        }

        public AuthUser(String userId, String password, boolean admin) {
            this.userId = userId;
            this.password = password;
            this.admin = admin;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isAdmin() {
            return admin;
        }

        public void setAdmin(boolean admin) {
            this.admin = admin;
        }
    }
}
