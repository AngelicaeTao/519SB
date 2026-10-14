package ViewLayer;

import ConfigurationLayer.Config;
import InfrastructureLayer.WebResources;

/** 存放前端 HTML 模板 */
public class WebPages {

    public static final String INDEX_HTML =
            WebResources.load("chat.html").replace("__SITE_TITLE__", Config.SITE_TITLE)
            .replace("__IMAGE_MAX_WIDTH__", String.valueOf(Config.IMAGE_MAX_WIDTH))
            .replace("__IMAGE_QUALITY__", String.valueOf(Config.IMAGE_QUALITY));
}