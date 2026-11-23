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

    public static final String DEFAULT_THEME = "pink";

    static {
        // 每个主题提供四个变量：
        //   --primary        主色
        //   --primary-dark   悬停/按下时的深色
        //   --primary-light  浅色底（选中态、悬停背景）
        //   --primary-fade   更淡的底（列表悬停）
        // light / fade 用半透明而不是不透明浅色：
        // 全站已改为暗色底，浅色块摆上去会变成刺眼的亮斑（底栏头像圆、
        // 点赞选中态、胶囊选中态都中招）。半透明叠在深底上才是柔和的粉雾，
        // 叠在白底上又约等于原来的浅色，两种场景都成立。
        put("green",  "默认绿", "#07c160", "#06ad56", "rgba(7,193,96,0.16)",   "rgba(7,193,96,0.08)");
        put("blue",   "天空蓝", "#2f80ed", "#1f6ad4", "rgba(47,128,237,0.16)",  "rgba(47,128,237,0.08)");
        put("purple", "薰衣紫", "#7c5cf0", "#6a49dc", "rgba(124,92,240,0.16)",  "rgba(124,92,240,0.08)");
        put("orange", "暖阳橙", "#f2913d", "#d97d2c", "rgba(242,145,61,0.16)",  "rgba(242,145,61,0.08)");
        put("pink",   "樱粉",   "#ff4d8d", "#e63c7c", "rgba(255,77,141,0.16)",  "rgba(255,77,141,0.08)");
        put("red",    "胭脂红", "#e5484d", "#c93b40", "rgba(229,72,77,0.16)",   "rgba(229,72,77,0.08)");
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
