package HandlerLayer;

import DataLayer.User;
import DataLayer.UserStore;
import InfrastructureLayer.HttpUtil;
import InfrastructureLayer.SessionManager;
import ViewLayer.UserPage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;

/**
 * 他人个人主页：/user/&lt;username&gt;
 *
 * 只读页面。展示的都是本来就公开的信息（昵称、头像、注册时间、发言统计、
 * 历史昵称）。不显示邮箱、密码等任何敏感项，也不提供任何修改入口。
 *
 * 账号名会显示——聊天室和帖子里本来就以账号作为唯一标识，
 * 藏起来反而会让人分不清"这个昵称是不是同一个人"。
 */
public class UserHandler implements HttpHandler {

    private final UserStore userStore;
    private final SessionManager sessions;

    public UserHandler(UserStore userStore, SessionManager sessions) {
        this.userStore = userStore;
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (HttpUtil.rejectWrongMethod(ex, "GET")) return;

        String path = ex.getRequestURI().getPath();
        if (!path.startsWith("/user/")) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }
        String target = path.substring("/user/".length());
        if (target.isEmpty()) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }

        // 未登录先跳登录页：避免匿名访问者靠遍历用户名探测有哪些账号
        String token = HttpUtil.getQueryParam(ex.getRequestURI().getQuery(), "token");
        String me = sessions.getUser(token);
        if (me == null) {
            ex.getResponseHeaders().set("Location", "/login");
            ex.sendResponseHeaders(302, -1);
            ex.close();
            return;
        }

        User u = userStore.getUser(target);
        if (u == null) {
            HttpUtil.sendHtml(ex, 404, notFound());
            return;
        }

        String historyJson = historyJson(target);
        String html = UserPage.render(
                u.username,
                u.nickname == null || u.nickname.isBlank() ? u.username : u.nickname,
                u.avatarId,
                u.isAdmin,
                u.createdAt,
                userStore.messageCount(target),
                userStore.postCount(target),
                historyJson);

        // 用访问者自己的配色，而不是被访问者的
        String theme = userStore.getTheme(me);
        HttpUtil.sendHtmlThemed(ex, 200, html, theme);
    }

    /** 昵称历史转成 JSON 数组，供页面内嵌 */
    private String historyJson(String username) {
        List<String[]> rows = userStore.nicknameHistory(username, 50);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"nickname\":\"").append(HttpUtil.escapeJson(rows.get(i)[0])).append("\"")
              .append(",\"changedAt\":").append(rows.get(i)[1]).append("}");
        }
        return sb.append("]").toString();
    }

    /** 用户不存在的提示页 */
    private String notFound() {
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>用户不存在</title></head>"
                + "<body style=\"font-family:-apple-system,'Segoe UI',sans-serif;"
                + "background:#e5e5e5;display:flex;align-items:center;justify-content:center;height:100vh;margin:0\">"
                + "<div style=\"background:#fff;border-radius:12px;padding:32px 28px;text-align:center;"
                + "box-shadow:0 1px 3px rgba(0,0,0,0.08)\">"
                + "<div style=\"font-size:16px;color:#333;margin-bottom:16px\">用户不存在</div>"
                + "<a href=\"/chat\" style=\"font-size:13px;color:#07c160;text-decoration:none;"
                + "padding:7px 16px;border:1px solid #07c160;border-radius:6px\">返回聊天室</a>"
                + "</div></body></html>";
    }
}
