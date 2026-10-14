package InfrastructureLayer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 个性化配色。
 *
 * 模板里的主色统一写成 var(--primary) 等 CSS 变量。变量值有两条应用路径：
 *
 * 1. 服务端注入——页面请求带 token 时，直接在 </head> 前插一块覆盖样式。
 *    好处是首屏就是对的，不会闪一下默认色。
 * 2. 客户端应用——前端把配色存进 localStorage，由 theme.js 注入。
 *
 * 两条路径都需要，因为并不是所有页面请求都带 token（例如从聊天页点
 * "个人中心"进 /profile，token 只在 localStorage 里）。只靠服务端注入
 * 会导致那些页面永远显示默认色。
 *
 * 默认配色（green）不注入任何东西——模板自带的 :root 就是绿色。
 */
public final class Theme {

    /** 主题名 -> 描述（给设置界面用） */
    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    /** 主题名 -> 变量定义块 */
    private static final Map<String, String> BLOCKS = new LinkedHashMap<>();
    /** 主题名 -> [primary, dark, light, fade] */
    private static final Map<String, String[]> VARS = new LinkedHashMap<>();

    public static final String DEFAULT_THEME = "green";

    static {
        // 每个主题提供四个变量：
        //   --primary        主色
        //   --primary-dark   悬停/按下时的深色
        //   --primary-light  浅色底（选中态、悬停背景）
        //   --primary-fade   更淡的底（列表悬停）
        put("green",  "默认绿", "#07c160", "#06ad56", "#e8f7f0", "#f0f9f4");
        put("blue",   "天空蓝", "#2f80ed", "#1f6ad4", "#e8f1fd", "#f2f7fe");
        put("purple", "薰衣紫", "#7c5cf0", "#6a49dc", "#efeafd", "#f5f2fe");
        put("orange", "暖阳橙", "#f2913d", "#d97d2c", "#fdf0e4", "#fef6ef");
        put("pink",   "樱粉",   "#ec5f8f", "#d44d7a", "#fdeaf1", "#fef2f6");
        put("red",    "胭脂红", "#e5484d", "#c93b40", "#fdeaea", "#fef3f3");
    }

    private Theme() {}

    private static void put(String name, String label, String primary, String dark, String light, String fade) {
        NAMES.put(name, label);
        VARS.put(name, new String[]{primary, dark, light, fade});
        BLOCKS.put(name, ":root{--primary:" + primary + ";--primary-dark:" + dark
                + ";--primary-light:" + light + ";--primary-fade:" + fade + ";}");
    }

    /** 主题名是否合法；非法一律回退到默认 */
    public static boolean isValid(String name) {
        return name != null && BLOCKS.containsKey(name);
    }

    /** 规范化：非法或 null 都返回默认主题名 */
    public static String normalize(String name) {
        return isValid(name) ? name : DEFAULT_THEME;
    }

    /** 该主题的四个变量值，顺序为 primary/dark/light/fade；非法主题返回默认的值 */
    public static String[] vars(String name) {
        return VARS.get(normalize(name));
    }

    /**
     * 需要注入到页面的样式块。
     * 默认主题返回 null（不注入），让模板自带的绿色生效。
     */
    public static String overrideStyle(String name) {
        String t = normalize(name);
        if (DEFAULT_THEME.equals(t)) return null;
        return "<style id=\"theme-override\">" + BLOCKS.get(t) + "</style>";
    }

    /** 主题清单，供前端渲染选项 */
    public static Map<String, String> list() {
        return NAMES;
    }

    /**
     * 主题选项的 JSON。
     * 带上完整变量（vars），前端才能把配色存进 localStorage 后自行应用，
     * 不必在 JS 里再抄一份调色板。
     */
    public static String listJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, String> e : NAMES.entrySet()) {
            String name = e.getKey();
            String[] v = VARS.get(name);
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"name\":\"").append(name).append("\"")
              .append(",\"label\":\"").append(e.getValue()).append("\"")
              .append(",\"color\":\"").append(v[0]).append("\"")
              .append(",\"vars\":{")
              .append("\"primary\":\"").append(v[0]).append("\"")
              .append(",\"dark\":\"").append(v[1]).append("\"")
              .append(",\"light\":\"").append(v[2]).append("\"")
              .append(",\"fade\":\"").append(v[3]).append("\"")
              .append("}}");
        }
        return sb.append("]").toString();
    }
}
