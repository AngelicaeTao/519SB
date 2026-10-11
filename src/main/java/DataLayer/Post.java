package DataLayer;

public class Post {
    public long id;
    public String title;
    public String content;
    public String author;              // 存的是账号（username）
    public String authorNickname;      // 展示用的昵称
    public String images;              // JSON 数组字符串，如 ["id1","id2"]，可为 null
    public long createdAt;
    public long updatedAt;
    public int views;
    public int likeCount;              // 点赞数（列表页用）
}