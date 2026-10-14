package DataLayer;

/** 用户数据模型 */
public class User {
    public String username;
    public String nickname;
    public String avatarId;    // 可为 null
    public boolean isAdmin;
    public long createdAt;
    /** 个性化配色主题名，可为 null（表示用默认） */
    public String theme;
}