package ViewLayer;

import InfrastructureLayer.WebResources;

/** 帖子页面：列表 + 详情 */
public class PostPage {

    public static final String LIST_HTML =
            WebResources.load("post-list.html");

    public static String detailHtml(long id) {
        return DETAIL_HTML.replace("__POST_ID__", String.valueOf(id));
    }

    public static final String DETAIL_HTML =
            WebResources.load("post-detail.html");
}