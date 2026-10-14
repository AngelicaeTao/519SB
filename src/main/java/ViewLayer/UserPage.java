package ViewLayer;

import InfrastructureLayer.WebResources;
import InfrastructureLayer.HttpUtil;

/** 他人个人主页（只读） */
public class UserPage {

    public static final String HTML = WebResources.load("user.html");

    /**
     * 填好占位符的页面。
     *
     * 昵称、账号都是不可信输入，一律用 escapeHtml 转义后再插入——
     * 绝不能用 escapeJson（那只管 JSON 语义，`<script>` 会原样带进去并执行）。
     * 头像 ID 只允许十六进制，非法时当作无头像，避免拼出奇怪的 URL。
     */
    public static String render(String username, String nickname, String avatarId,
                                boolean isAdmin, long createdAt,
                                int messageCount, int postCount, String nicknameHistoryJson) {
        String avatar = "";
        if (avatarId != null && avatarId.matches("^[a-f0-9]{32}$")) {
            avatar = "/image/" + avatarId;
        }
        return HTML
                .replace("__U_USERNAME__", HttpUtil.escapeHtml(username))
                .replace("__U_NICKNAME__", HttpUtil.escapeHtml(nickname))
                .replace("__U_AVATAR__", avatar)
                .replace("__U_ADMIN__", isAdmin ? "1" : "0")
                .replace("__U_CREATED__", String.valueOf(createdAt))
                .replace("__U_MSGS__", String.valueOf(messageCount))
                .replace("__U_POSTS__", String.valueOf(postCount))
                .replace("__U_NICKHIST__", nicknameHistoryJson == null ? "[]" : nicknameHistoryJson);
    }
}
