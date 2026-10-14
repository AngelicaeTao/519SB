package HandlerLayer;

import DataLayer.ImageStore;
import DataLayer.Post;
import DataLayer.PostStore;
import DataLayer.User;
import DataLayer.UserStore;
import InfrastructureLayer.HttpUtil;
import InfrastructureLayer.SessionManager;
import InfrastructureLayer.SseManager;
import ViewLayer.PostPage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 帖子：页面 + API */
public class PostHandler implements HttpHandler {

    /** 一条评论最多带几张图 */
    private static final int MAX_COMMENT_IMAGES = 3;

    private final PostStore postStore;
    private final SessionManager sessions;
    private final UserStore userStore;
    private final SseManager sse;

    public PostHandler(PostStore postStore, SessionManager sessions, UserStore userStore, SseManager sse) {
        this.postStore = postStore;
        this.sessions = sessions;
        this.userStore = userStore;
        this.sse = sse;
    }

    /** 通知所有客户端帖子有变动，让帖子列表自动刷新 */
    private void notifyPostsChanged(String action, long postId) {
        if (sse != null) sse.broadcastPostsChanged(action, postId);
    }

    /** 当前登录用户的配色主题；未登录或没设过返回 null（默认配色） */
    private String currentTheme(HttpExchange ex) {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) return null;
        User u = userStore.getUser(username);
        return (u == null) ? null : u.theme;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        if (path.startsWith("/api/posts")) {
            handleApi(ex, path);
            return;
        }

        if (path.equals("/posts") || path.equals("/posts/")) {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                HttpUtil.sendHtmlThemed(ex, 200, PostPage.LIST_HTML, currentTheme(ex));
            } else {
                ex.sendResponseHeaders(405, -1);
                ex.close();
            }
            return;
        }

        if (path.startsWith("/posts/")) {
            String rest = path.substring("/posts/".length());
            if (rest.matches("\\d+") && "GET".equalsIgnoreCase(ex.getRequestMethod())) {
                long id = Long.parseLong(rest);
                HttpUtil.sendHtmlThemed(ex, 200, PostPage.detailHtml(id), currentTheme(ex));
            } else {
                ex.sendResponseHeaders(404, -1);
                ex.close();
            }
            return;
        }

        ex.sendResponseHeaders(404, -1);
        ex.close();
    }

    private void handleApi(HttpExchange ex, String path) throws IOException {
        String method = ex.getRequestMethod();

        if (path.equals("/api/posts") && "GET".equalsIgnoreCase(method)) {
            String idStr = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "id");
            if (idStr == null) handleList(ex);
            else handleGetOne(ex, idStr);
            return;
        }

        if (path.equals("/api/posts") && "POST".equalsIgnoreCase(method)) { handleCreate(ex); return; }
        if (path.equals("/api/posts/search") && "GET".equalsIgnoreCase(method)) { handleSearch(ex); return; }
        if (path.equals("/api/posts/update") && "POST".equalsIgnoreCase(method)) { handleUpdate(ex); return; }
        if (path.equals("/api/posts/delete") && "POST".equalsIgnoreCase(method)) { handleDelete(ex); return; }
        if (path.equals("/api/posts/like") && "POST".equalsIgnoreCase(method)) { handleToggleLike(ex); return; }
        if (path.equals("/api/posts/comment") && "POST".equalsIgnoreCase(method)) { handleAddComment(ex); return; }
        if (path.equals("/api/posts/comments") && "GET".equalsIgnoreCase(method)) { handleListComments(ex); return; }
        if (path.equals("/api/posts/deleteComment") && "POST".equalsIgnoreCase(method)) { handleDeleteComment(ex); return; }

        HttpUtil.sendJson(ex, 404, "{\"ok\":false,\"msg\":\"接口不存在\"}");
    }

    private void handleList(HttpExchange ex) throws IOException {
        List<Post> posts = postStore.listAll();
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"posts\":[");
        for (int i = 0; i < posts.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(posts.get(i)));
        }
        sb.append("]}");
        HttpUtil.sendJson(ex, 200, sb.toString());
    }

    /**
     * GET /api/posts/search?q=关键词&field=all|title|content|author
     *
     * 只读接口，不需要登录。关键词长度限制避免超长串拖慢 LIKE。
     */
    private void handleSearch(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getQuery();
        String q = HttpUtil.getQueryParam(query, "q");
        String field = HttpUtil.getQueryParam(query, "field");

        if (q == null || q.isBlank()) {
            HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"posts\":[],\"q\":\"\"}");
            return;
        }
        if (q.length() > 60) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"关键词最多 60 字\"}");
            return;
        }
        if (field == null || field.isBlank()) field = "all";

        List<Post> posts = postStore.search(q, field, 50);
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"q\":\"")
                .append(HttpUtil.escapeJson(q)).append("\"")
                .append(",\"field\":\"").append(HttpUtil.escapeJson(field)).append("\"")
                .append(",\"posts\":[");
        for (int i = 0; i < posts.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(posts.get(i)));
        }
        sb.append("]}");
        HttpUtil.sendJson(ex, 200, sb.toString());
    }

    private void handleGetOne(HttpExchange ex, String idStr) throws IOException {
        long id;
        try { id = Long.parseLong(idStr.trim()); }
        catch (NumberFormatException e) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"非法 id\"}");
            return;
        }
        Post p = postStore.getById(id);
        if (p == null) {
            HttpUtil.sendJson(ex, 404, "{\"ok\":false,\"msg\":\"帖子不存在\"}");
            return;
        }
        postStore.incrementViews(id);
        p.views++;

        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        boolean liked = username != null && postStore.isLiked(id, username);
        int likeCount = postStore.getLikeCount(id);
        int commentCount = postStore.getCommentCount(id);

        HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"post\":" + toJson(p)
                + ",\"liked\":" + liked
                + ",\"likeCount\":" + likeCount
                + ",\"commentCount\":" + commentCount + "}");
    }

    private void handleCreate(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"请先登录\"}");
            return;
        }

        String body = HttpUtil.readBody(ex, BootstrapLayer.ChatServer.MAX_BODY_NORMAL);
        String title = HttpUtil.extractJsonString(body, "title");
        String content = HttpUtil.extractJsonString(body, "content");

        String err = validate(title, content);
        if (err != null) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"" + err + "\"}");
            return;
        }

        String imagesJson = parseImagesJson(body);

        long id = postStore.create(title.trim(), content, username, imagesJson);
        if (id > 0) {
            notifyPostsChanged("create", id);
            HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"id\":" + id + "}");
        } else {
            HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"msg\":\"发帖失败\"}");
        }
    }

    private void handleUpdate(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"请先登录\"}");
            return;
        }

        String body = HttpUtil.readBody(ex, BootstrapLayer.ChatServer.MAX_BODY_NORMAL);
        Long id = extractLong(body, "id");
        String title = HttpUtil.extractJsonString(body, "title");
        String content = HttpUtil.extractJsonString(body, "content");

        if (id == null) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"缺少 id\"}");
            return;
        }
        Post existing = postStore.getById(id);
        if (existing == null) {
            HttpUtil.sendJson(ex, 404, "{\"ok\":false,\"msg\":\"帖子不存在\"}");
            return;
        }
        if (!canManage(username, existing.author)) {
            HttpUtil.sendJson(ex, 403, "{\"ok\":false,\"msg\":\"无权修改该帖子\"}");
            return;
        }

        String err = validate(title, content);
        if (err != null) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"" + err + "\"}");
            return;
        }

        String imagesJson = parseImagesJson(body);

        if (postStore.update(id, title.trim(), content, imagesJson)) {
            notifyPostsChanged("update", id);
            HttpUtil.sendJson(ex, 200, "{\"ok\":true}");
        } else {
            HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"msg\":\"更新失败\"}");
        }
    }

    private void handleDelete(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"请先登录\"}");
            return;
        }

        String body = HttpUtil.readBody(ex, BootstrapLayer.ChatServer.MAX_BODY_NORMAL);
        Long id = extractLong(body, "id");
        if (id == null) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"缺少 id\"}");
            return;
        }
        Post existing = postStore.getById(id);
        if (existing == null) {
            HttpUtil.sendJson(ex, 404, "{\"ok\":false,\"msg\":\"帖子不存在\"}");
            return;
        }
        if (!canManage(username, existing.author)) {
            HttpUtil.sendJson(ex, 403, "{\"ok\":false,\"msg\":\"无权删除该帖子\"}");
            return;
        }

        if (postStore.delete(id)) {
            notifyPostsChanged("delete", id);
            HttpUtil.sendJson(ex, 200, "{\"ok\":true}");
        } else {
            HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"msg\":\"删除失败\"}");
        }
    }

    // ---------------- 工具 ----------------

    private boolean canManage(String operator, String author) {
        if (operator.equals(author)) return true;
        User u = userStore.getUser(operator);
        return u != null && u.isAdmin;
    }

    private String validate(String title, String content) {
        if (title == null || title.isBlank()) return "标题不能为空";
        if (title.length() > 100) return "标题最多 100 字";
        if (content == null || content.isBlank()) return "内容不能为空";
        if (content.length() > 20000) return "内容最多 20000 字";
        return null;
    }

    /**
     * 从请求体里解析 images 数组，返回 JSON 数组字符串（如 ["id1","id2"]）。
     * 无图片返回 null。会过滤掉非法的图片 ID，防止注入。
     */
    private String parseImagesJson(String body) {
        if (body == null) return null;
        int keyIdx = body.indexOf("\"images\"");
        if (keyIdx < 0) return null;
        int lb = body.indexOf('[', keyIdx);
        int rb = body.indexOf(']', lb);
        if (lb < 0 || rb < 0) return null;
        String inner = body.substring(lb + 1, rb).trim();
        if (inner.isEmpty()) return null;

        // 图片 ID 必须是 32 位小写 hex
        Pattern idPattern = Pattern.compile("\"([a-f0-9]{32})\"");
        Matcher m = idPattern.matcher(inner);
        List<String> ids = new ArrayList<>();
        while (m.find() && ids.size() < 9) {
            ids.add(m.group(1));
        }
        if (ids.isEmpty()) return null;

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(ids.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJson(Post p) {
        return "{\"id\":" + p.id
                + ",\"title\":\"" + HttpUtil.escapeJson(p.title) + "\""
                + ",\"content\":\"" + HttpUtil.escapeJson(p.content) + "\""
                + ",\"author\":\"" + HttpUtil.escapeJson(p.author) + "\""
                + ",\"authorNickname\":\"" + HttpUtil.escapeJson(p.authorNickname == null ? p.author : p.authorNickname) + "\""
                + ",\"images\":" + (p.images == null ? "null" : p.images)
                + ",\"createdAt\":" + p.createdAt
                + ",\"updatedAt\":" + p.updatedAt
                + ",\"views\":" + p.views
                + ",\"likeCount\":" + p.likeCount + "}";
    }

    // ---------------- 点赞 ----------------

    private void handleToggleLike(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"请先登录\"}");
            return;
        }

        String body = HttpUtil.readBody(ex, BootstrapLayer.ChatServer.MAX_BODY_NORMAL);
        Long postId = extractLong(body, "postId");
        if (postId == null) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"缺少 postId\"}");
            return;
        }
        if (postStore.getById(postId) == null) {
            HttpUtil.sendJson(ex, 404, "{\"ok\":false,\"msg\":\"帖子不存在\"}");
            return;
        }

        boolean liked = postStore.toggleLike(postId, username);
        int count = postStore.getLikeCount(postId);
        HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"liked\":" + liked + ",\"likeCount\":" + count + "}");
    }

    // ---------------- 评论 ----------------

    private void handleAddComment(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"请先登录\"}");
            return;
        }

        String body = HttpUtil.readBody(ex, BootstrapLayer.ChatServer.MAX_BODY_NORMAL);
        Long postId = extractLong(body, "postId");
        Long parentId = extractLong(body, "parentId");
        String content = HttpUtil.extractJsonString(body, "content");
        List<String> imageIds = HttpUtil.extractJsonStringArray(body, "images");

        if (content == null) content = "";
        content = content.trim();

        // 有图就可以不带文字，但两者不能都空
        if (content.isBlank() && imageIds.isEmpty()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"评论内容不能为空\"}");
            return;
        }
        if (postId == null) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"缺少 postId\"}");
            return;
        }
        if (content.length() > 500) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"评论最多 500 字\"}");
            return;
        }
        if (imageIds.size() > MAX_COMMENT_IMAGES) {
            HttpUtil.sendJson(ex, 400,
                    "{\"ok\":false,\"msg\":\"一条评论最多 " + MAX_COMMENT_IMAGES + " 张图\"}");
            return;
        }

        // 配图必须确实属于提交者且用途为 comment——
        // 否则可以拿别人的图片 id 把别人的图挂到自己的评论下
        if (!ImageStore.ownsAll(username, imageIds, "comment")) {
            HttpUtil.sendJson(ex, 403, "{\"ok\":false,\"msg\":\"配图无效或不属于你\"}");
            return;
        }

        if (postStore.getById(postId) == null) {
            HttpUtil.sendJson(ex, 404, "{\"ok\":false,\"msg\":\"帖子不存在\"}");
            return;
        }
        if (parentId != null && postStore.getCommentAuthor(parentId) == null) {
            HttpUtil.sendJson(ex, 404, "{\"ok\":false,\"msg\":\"被回复的评论不存在\"}");
            return;
        }

        String imagesJson = imageIds.isEmpty() ? null : HttpUtil.toJsonArray(imageIds);
        long id = postStore.addComment(postId, username, content, parentId, imagesJson);
        if (id > 0) {
            HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"id\":" + id + "}");
        } else {
            HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"msg\":\"评论失败\"}");
        }
    }

    private void handleListComments(HttpExchange ex) throws IOException {
        String idStr = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "postId");
        if (idStr == null) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"缺少 postId\"}");
            return;
        }
        long postId;
        try { postId = Long.parseLong(idStr.trim()); }
        catch (NumberFormatException e) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"非法 postId\"}");
            return;
        }
        List<String> comments = postStore.listComments(postId);
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"comments\":[");
        for (int i = 0; i < comments.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(comments.get(i));
        }
        sb.append("]}");
        HttpUtil.sendJson(ex, 200, sb.toString());
    }

    private void handleDeleteComment(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"请先登录\"}");
            return;
        }

        String body = HttpUtil.readBody(ex, BootstrapLayer.ChatServer.MAX_BODY_NORMAL);
        Long commentId = extractLong(body, "id");
        if (commentId == null) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"缺少 id\"}");
            return;
        }

        String author = postStore.getCommentAuthor(commentId);
        if (author == null) {
            HttpUtil.sendJson(ex, 404, "{\"ok\":false,\"msg\":\"评论不存在\"}");
            return;
        }
        if (!canManage(username, author)) {
            HttpUtil.sendJson(ex, 403, "{\"ok\":false,\"msg\":\"无权删除该评论\"}");
            return;
        }

        if (postStore.deleteComment(commentId)) {
            HttpUtil.sendJson(ex, 200, "{\"ok\":true}");
        } else {
            HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"msg\":\"删除失败\"}");
        }
    }

    private Long extractLong(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try { return Long.parseLong(m.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }
}