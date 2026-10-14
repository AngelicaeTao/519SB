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
        if (v != null) return v;

        for (Path dir : FALLBACK_DIRS) {
            Path p = dir.resolve(name);
            try {
                if (Files.exists(p)) {
                    return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
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

    private static String fromClasspath(String name) {
        try (InputStream is = WebResources.class.getResourceAsStream(CLASSPATH_PREFIX + name)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
