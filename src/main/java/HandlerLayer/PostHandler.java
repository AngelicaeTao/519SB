package HandlerLayer;

import DataLayer.Post;
import DataLayer.PostStore;
import DataLayer.User;
import DataLayer.UserStore;
import InfrastructureLayer.HttpUtil;
import InfrastructureLayer.SessionManager;
import ViewLayer.PostPage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import DataLayer.User;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 帖子：页面 + API */
public class PostHandler implements HttpHandler {

    private final PostStore postStore;
    private final SessionManager sessions;
    private final UserStore userStore;

    public PostHandler(PostStore postStore, SessionManager sessions, UserStore userStore) {
        this.postStore = postStore;
        this.sessions = sessions;
        this.userStore = userStore;
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
                HttpUtil.sendHtml(ex, 200, PostPage.LIST_HTML);
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
                HttpUtil.sendHtml(ex, 200, PostPage.detailHtml(id));
            } else {
                ex.sendResponseHeaders(404, -1);
                ex.close();
            }
            return;
        }

        ex.sendResponseHeaders(404, -1);
        ex.close();
    }

    // ---------------- API ----------------

    private void handleApi(HttpExchange ex, String path) throws IOException {
        String method = ex.getRequestMethod();

        // GET /api/posts          列表
        // GET /api/posts?id=123   详情
        if (path.equals("/api/posts") && "GET".equalsIgnoreCase(method)) {
            String idStr = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "id");
            if (idStr == null) {
                handleList(ex);
            } else {
                handleGetOne(ex, idStr);
            }
            return;
        }

        // POST /api/posts?token=  创建
        if (path.equals("/api/posts") && "POST".equalsIgnoreCase(method)) {
            handleCreate(ex);
            return;
        }

        // POST /api/posts/update?token=
        if (path.equals("/api/posts/update") && "POST".equalsIgnoreCase(method)) {
            handleUpdate(ex);
            return;
        }

        // POST /api/posts/delete?token=
        if (path.equals("/api/posts/delete") && "POST".equalsIgnoreCase(method)) {
            handleDelete(ex);
            return;
        }

        // POST /api/posts/like?token=
        if (path.equals("/api/posts/like") && "POST".equalsIgnoreCase(method)) {
            handleToggleLike(ex);
            return;
        }

// POST /api/posts/comment?token=
        if (path.equals("/api/posts/comment") && "POST".equalsIgnoreCase(method)) {
            handleAddComment(ex);
            return;
        }

// GET /api/posts/comments?postId=
        if (path.equals("/api/posts/comments") && "GET".equalsIgnoreCase(method)) {
            handleListComments(ex);
            return;
        }

// POST /api/posts/deleteComment?token=
        if (path.equals("/api/posts/deleteComment") && "POST".equalsIgnoreCase(method)) {
            handleDeleteComment(ex);
            return;
        }

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

    private void handleGetOne(HttpExchange ex, String idStr) throws IOException {
        long id;
        try {
            id = Long.parseLong(idStr.trim());
        } catch (NumberFormatException e) {
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

        String body = HttpUtil.readBody(ex);
        String title = HttpUtil.extractJsonString(body, "title");
        String content = HttpUtil.extractJsonString(body, "content");

        String err = validate(title, content);
        if (err != null) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"" + err + "\"}");
            return;
        }

        long id = postStore.create(title.trim(), content, username);
        if (id > 0) {
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

        String body = HttpUtil.readBody(ex);
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

        if (postStore.update(id, title.trim(), content)) {
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

        String body = HttpUtil.readBody(ex);
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
            HttpUtil.sendJson(ex, 200, "{\"ok\":true}");
        } else {
            HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"msg\":\"删除失败\"}");
        }
    }

    // ---------------- 工具 ----------------

    /** 作者本人或管理员可以管理 */
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

    private String toJson(Post p) {
        return "{\"id\":" + p.id
                + ",\"title\":\"" + HttpUtil.escapeJson(p.title) + "\""
                + ",\"content\":\"" + HttpUtil.escapeJson(p.content) + "\""
                + ",\"author\":\"" + HttpUtil.escapeJson(p.author) + "\""
                + ",\"createdAt\":" + p.createdAt
                + ",\"updatedAt\":" + p.updatedAt
                + ",\"views\":" + p.views + "}";
    }

    // ---------------- 点赞 ----------------

    private void handleToggleLike(HttpExchange ex) throws IOException {
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String username = sessions.getUser(token);
        if (username == null) {
            HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"msg\":\"请先登录\"}");
            return;
        }

        String body = HttpUtil.readBody(ex);
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

        String body = HttpUtil.readBody(ex);
        Long postId = extractLong(body, "postId");
        Long parentId = extractLong(body, "parentId");   // ★ 新增：可为 null
        String content = HttpUtil.extractJsonString(body, "content");

        if (postId == null || content == null || content.isBlank()) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"评论内容不能为空\"}");
            return;
        }
        if (content.length() > 500) {
            HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"msg\":\"评论最多 500 字\"}");
            return;
        }
        if (postStore.getById(postId) == null) {
            HttpUtil.sendJson(ex, 404, "{\"ok\":false,\"msg\":\"帖子不存在\"}");
            return;
        }
        // ★ 如果指定了 parentId，校验它存在
        if (parentId != null && postStore.getCommentAuthor(parentId) == null) {
            HttpUtil.sendJson(ex, 404, "{\"ok\":false,\"msg\":\"被回复的评论不存在\"}");
            return;
        }

        long id = postStore.addComment(postId, username, content, parentId);
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

        String body = HttpUtil.readBody(ex);
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
