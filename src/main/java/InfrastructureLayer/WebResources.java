package InfrastructureLayer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 页面模板加载。
 *
 * 模板存放在 src/main/resources/web/*.html，运行时读取，不再编译进 class。
 * 这么做的原因：
 * - JVM 单个字符串常量上限 65535 字节，页面继续长大就会撞上，且 javac 报的是
 *   误导性的「未闭合字符串文字」，很难查；
 * - 模板放成真正的 .html 文件后，IDE 有语法高亮、diff 可读、改完不必重编译。
 *
 * 兼容两种运行方式：
 * 1. classpath（Maven 打包成 jar、IDEA 运行）——优先走这条；
 * 2. 直接读文件（start.bat 手工 javac 到 out 再 java，resources 不在 classpath 上）。
 */
public final class WebResources {

    private static final String CLASSPATH_PREFIX = "/web/";
    private static final Path[] FALLBACK_DIRS = {
            Path.of("src", "main", "resources", "web"),
            Path.of("resources", "web"),
            Path.of("web")
    };

    private WebResources() {}

    /**
     * 读取模板；失败时返回一个可读的错误页而不是抛异常，
     * 避免整个服务因为一个模板缺失而起不来。
     */
    public static String load(String name) {
        String v = fromClasspath(name);
        if (v != null) return decorate(name, v);

        for (Path dir : FALLBACK_DIRS) {
            Path p = dir.resolve(name);
            try {
                if (Files.exists(p)) {
                    return decorate(name,
                            new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
            }
        }

        System.err.println("[WebResources] 找不到模板：" + name
                + "（classpath 的 /web/ 和 " + FALLBACK_DIRS[0] + " 都没有）");
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>模板缺失</title></head>"
                + "<body style=\"font-family:sans-serif;padding:40px\">"
                + "<h2>页面模板缺失</h2><p>找不到 <code>" + name + "</code>。</p>"
                + "<p>请确认 <code>src/main/resources/web/" + name + "</code> 存在，"
                + "或把该目录加入 classpath。</p></body></html>";
    }

    /** 需要登录的页面统一在 head 里插入守卫脚本，其它页面原样返回 */
    private static String decorate(String name, String html) {
        return GUARDED_PAGES.contains(name) ? injectGuard(html) : html;
    }

    private static String fromClasspath(String name) {
        try (InputStream is = WebResources.class.getResourceAsStream(CLASSPATH_PREFIX + name)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 需要登录才能看的页面。这些模板在 &lt;head&gt; 开头会被插入一段同步守卫脚本。
     *
     * 为什么要插在 head：页面是静态 HTML，服务端不认 token（token 在
     * localStorage 里，服务端拿不到），只能由前端判断。如果等到 body 末尾的
     * app.js 才检查，页面内容已经渲染出来了 —— 用户会先看到"我进来了"，
     * 然后才被弹回登录页，观感就是「明明退出了还能进」。
     *
     * head 里的脚本在 body 解析前同步执行，没有 token 直接 replace，不会留下残影。
     * token 存在但已在服务端失效的情况，仍由 app.js 异步校验兜底（浏览器必须
     * 发出请求才知道会话还在不在，这一层无法提前）。
     */
    private static final java.util.Set<String> GUARDED_PAGES = java.util.Set.of(
            "post-list.html", "post-detail.html", "chat.html",
            "profile.html", "admin.html", "user.html");

    private static final String GUARD_SCRIPT =
            "\n<script>/*auth-guard*/(function(){try{"
            // 带 token 的链接（/admin?token=…）先把它收进本地，否则会把自己踢回登录页
            + "var t=new URLSearchParams(location.search).get('token');"
            + "if(t&&!localStorage.getItem('token'))localStorage.setItem('token',t);"
            + "if(!localStorage.getItem('token')){"
            + "location.replace('/login');}}catch(e){}})();</script>";

    private static String injectGuard(String html) {
        // 页面自己的脚本里也有 localStorage.getItem('token')，
        // 拿它当标记会误判，这里用一个只出现在注入片段里的注释。
        if (html == null || html.indexOf("/*auth-guard*/") >= 0) return html;
        int head = html.indexOf("<head");
        if (head < 0) return html;
        int end = html.indexOf('>', head);
        if (end < 0) return html;
        return html.substring(0, end + 1) + GUARD_SCRIPT + html.substring(end + 1);
    }
}
