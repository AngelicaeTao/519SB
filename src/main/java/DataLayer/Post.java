package DataLayer;

public class Post {
    public long id;
    public String title;
    public String content;
    public String author;              // 存的是账号（username）
    public String authorNickname;      // 展示用的昵称
    public long createdAt;
    public long updatedAt;
    public int views;
}