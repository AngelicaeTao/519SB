package ViewLayer;

import ConfigurationLayer.Config;

/** 存放前端 HTML 模板 */
public class WebPages {

    private static final String INDEX_HTML_PART1 = """
        <!doctype html>
        <html lang="zh-CN"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>__SITE_TITLE__</title>
        <style>
          * { box-sizing: border-box; margin: 0; padding: 0; }
          body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #e5e5e5; height: 100vh; display: flex; flex-direction: column; overflow: hidden; }
          #header { background: #fff; padding: 10px 15px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); z-index: 10; display: flex; align-items: center; justify-content: space-between; }
          #header .title { font-size: 15px; font-weight: bold; }
          #header .right { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #666; }
          #header .right a, #header .right button { font-size: 12px; padding: 5px 10px; border: 1px solid #ddd; background: #fff; border-radius: 6px; cursor: pointer; color: #666; text-decoration: none; }
          #header .right a:hover, #header .right button:hover { background: #f5f5f5; }
          #header .right button.online-btn { border-color: #07c160; color: #07c160; }
          #header .right button.online-btn.active { background: #07c160; color: #fff; }
          .my-avatar { width: 28px; height: 28px; border-radius: 50%; background: #07c160; color: #fff; display: inline-flex; align-items: center; justify-content: center; font-weight: bold; font-size: 13px; overflow: hidden; cursor: pointer; text-decoration: none; }
          .my-avatar img { width: 100%; height: 100%; object-fit: cover; }

          #main { flex: 1; overflow: hidden; position: relative; display: flex; }
          #chat-box { flex: 1; overflow-y: auto; padding: 15px 15px 80px 15px; display: flex; flex-direction: column; position: relative; }
          #loading-tip { text-align: center; color: #999; font-size: 12px; padding: 8px; display: none; }
          #loading-tip.show { display: block; }

          #sidebar {
            position: absolute;
            top: 0; right: 0; bottom: 0;
            width: 220px;
            background: #fff;
            border-left: 1px solid #e5e5e5;
            overflow-y: auto;
            padding: 12px 10px;
            font-size: 13px;
            box-shadow: -2px 0 8px rgba(0,0,0,0.08);
            transform: translateX(100%);
            transition: transform 0.25s ease;
            z-index: 20;
          }
          #sidebar.show { transform: translateX(0); }
          #sidebar h3 { font-size: 12px; color: #999; font-weight: normal; margin-bottom: 10px; display: flex; align-items: center; justify-content: space-between; }
          #sidebar h3 .close-btn { font-size: 16px; color: #999; cursor: pointer; background: none; border: none; padding: 0 4px; line-height: 1; }
          #sidebar h3 .close-btn:hover { color: #333; }
          .online-user { display: flex; align-items: center; gap: 8px; padding: 6px 4px; border-radius: 6px; color: #333; }
          .online-user .dot { width: 8px; height: 8px; border-radius: 50%; background: #07c160; flex-shrink: 0; }
          .online-user .name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
          .online-user .avatar { width: 24px; height: 24px; border-radius: 50%; background: #bbb; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: bold; overflow: hidden; flex-shrink: 0; }
          .online-user .avatar img { width: 100%; height: 100%; object-fit: cover; }

          #sidebar-mask { position: absolute; inset: 0; background: rgba(0,0,0,0.15); display: none; z-index: 15; }
          #sidebar-mask.show { display: block; }

          .msg { max-width: 78%; margin-bottom: 15px; display: flex; gap: 8px; position: relative; }
          .msg.self { align-self: flex-end; flex-direction: row-reverse; }
          .msg.other { align-self: flex-start; }
          .msg .avatar { width: 36px; height: 36px; border-radius: 50%; background: #bbb; color: #fff; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 14px; flex-shrink: 0; overflow: hidden; }
          .msg .avatar img { width: 100%; height: 100%; object-fit: cover; }
          .msg .body { display: flex; flex-direction: column; min-width: 0; }
          .msg.self .body { align-items: flex-end; }
          .msg .bubble { padding: 10px 14px; border-radius: 12px; word-wrap: break-word; font-size: 15px; line-height: 1.4; white-space: pre-wrap; }
          .self .bubble { background: #07c160; color: #fff; border-top-right-radius: 2px; }
          .other .bubble { background: #fff; color: #333; border-top-left-radius: 2px; }
          .msg.mention .bubble { box-shadow: 0 0 0 2px #ffb300; }
          .meta { font-size: 12px; color: #999; margin-bottom: 4px; display: flex; align-items: center; gap: 6px; }
          .self .meta { justify-content: flex-end; }
          .recall-btn { font-size: 11px; color: #999; cursor: pointer; opacity: 0; transition: opacity 0.2s; padding: 0 4px; }
          .msg:hover .recall-btn { opacity: 1; }
          .recall-btn:hover { color: #c33; }
          .mention-badge { background: #ffb300; color: #fff; font-size: 10px; padding: 1px 5px; border-radius: 8px; }
          #input-area { background: #fff; padding: 10px; display: flex; box-shadow: 0 -1px 3px rgba(0,0,0,0.1); align-items: flex-end; gap: 8px; position: relative; }
          /* 不设 max-height：上限交给 JS，这样拖拽调整才不会被 CSS 截断。
             默认高度由 autoResizeInput() 按内容自动设置 */
          #input { flex: 1; border: 1px solid #ddd; border-radius: 6px; padding: 10px; font-size: 15px; resize: none; min-height: 42px; font-family: inherit; line-height: 1.4; outline: none; }
          /* 拖拽条：只在上边缘一条窄带，靠 cursor 与 hover 提示可拖 */
          #input-resize {
            position: absolute; top: 0; left: 0; right: 0; height: 7px;
            cursor: ns-resize; z-index: 5;
          }
          #input-resize:hover, #input-resize.dragging { background: rgba(7,193,96,0.12); }
          #input:focus { border-color: #07c160; }
          #img-btn { background: #fff; border: 1px solid #ddd; border-radius: 6px; padding: 10px 12px; font-size: 15px; cursor: pointer; color: #333; }
          #send-btn { background: #07c160; color: #fff; border: none; border-radius: 6px; padding: 10px 20px; font-size: 15px; cursor: pointer; }
          #send-btn:disabled, #img-btn:disabled { opacity: 0.5; cursor: not-allowed; }
          .bubble img { max-width: 100%; max-height: 40vh; width: auto; height: auto; border-radius: 4px; display: block; cursor: zoom-in; object-fit: contain; }
          #mention-dropdown { position: absolute; bottom: 100%; left: 10px; margin-bottom: 4px; background: #fff; border: 1px solid #ddd; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15); max-height: 240px; overflow-y: auto; min-width: 200px; display: none; z-index: 50; }
          #mention-dropdown.show { display: block; }
          .mention-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; cursor: pointer; font-size: 14px; }
          .mention-item:hover, .mention-item.active { background: #f0f9f4; }
          .mention-item .avatar { width: 24px; height: 24px; border-radius: 50%; background: #07c160; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: bold; overflow: hidden; }
          .mention-item .avatar img { width: 100%; height: 100%; object-fit: cover; }

          /* ===== 右侧中部悬浮按钮组 ===== */
          #float-buttons {
            position: absolute;
            right: 15px;
            top: 50%;
            transform: translateY(-50%);
            display: flex;
            flex-direction: column;
            gap: 8px;
            z-index: 30;
            /* 鼠标不在上面时淡化，移上去恢复清晰 */
            opacity: 0.22;
            transition: opacity 0.2s ease;
          }
          #float-buttons:hover,
          #float-buttons:focus-within {
            opacity: 1;
          }
          #float-buttons button {
            position: relative;
            width: 44px; height: 44px;
            border-radius: 50%;
            border: none;
            background: #fff;
            box-shadow: 0 2px 8px rgba(0,0,0,0.15);
            cursor: pointer;
            font-size: 18px;
            color: #666;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: all 0.15s;
          }
          #float-buttons button:hover { background: #f0f0f0; transform: scale(1.05); }
          #float-buttons button.active { background: #07c160; color: #fff; }
          #float-buttons button .badge {
            position: absolute;
            top: -4px; right: -4px;
            background: #ff5757; color: #fff;
            border-radius: 10px; padding: 1px 6px;
            font-size: 10px; line-height: 1.4;
          }

          /* ===== 筛选面板 ===== */
          #filter-mask { position: absolute; inset: 0; display: none; z-index: 35; }
          #filter-mask.show { display: block; }

          #filter-panel {
            position: absolute;
            right: 70px;
            top: 50%;
            transform: translateY(-50%);
            width: 300px;
            max-height: 72vh;
            background: #fff;
            border-radius: 12px;
            box-shadow: 0 4px 24px rgba(0,0,0,0.18);
            display: none;
            flex-direction: column;
            z-index: 40;
          }
          #filter-panel.show { display: flex; }
          .filter-header {
            padding: 12px 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            border-bottom: 1px solid #f0f0f0;
            font-weight: bold;
            font-size: 14px;
            flex-shrink: 0;
          }
          .filter-header .filter-close {
            background: none; border: none;
            font-size: 20px; line-height: 1;
            color: #999; cursor: pointer;
            padding: 0 4px;
          }
          .filter-header .filter-close:hover { color: #333; }

          .filter-body { flex: 1; padding: 8px 0; }
          .filter-section { padding: 10px 16px; border-bottom: 1px solid #f7f7f7; }
          .filter-section:last-child { border-bottom: none; }
          .filter-section .filter-title {
            font-size: 12px; color: #999;
            margin-bottom: 8px;
            display: flex; align-items: center; justify-content: space-between;
          }
          .filter-section .filter-title .clear-btn {
            font-size: 11px; color: #07c160; cursor: pointer;
            background: none; border: none; padding: 0;
          }
          .filter-section .filter-title .clear-btn:hover { text-decoration: underline; }

          .filter-options { display: flex; flex-wrap: wrap; gap: 6px; }
          .filter-options label {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            padding: 4px 10px;
            border: 1px solid #e0e0e0;
            border-radius: 14px;
            font-size: 12px;
            color: #555;
            cursor: pointer;
            background: #fafafa;
            transition: all 0.15s;
            user-select: none;
          }
          .filter-options label:hover { border-color: #07c160; color: #07c160; }
          .filter-options label input[type="checkbox"] {
            display: none;
          }
          .filter-options label.checked {
            background: #07c160; color: #fff; border-color: #07c160;
          }

          .filter-actions {
            padding: 10px 16px;
            display: flex;
            gap: 8px;
            border-top: 1px solid #f0f0f0;
            flex-shrink: 0;
          }
          .filter-actions button {
            flex: 1;
            padding: 8px;
            font-size: 13px;
            border-radius: 6px;
            border: none;
            cursor: pointer;
            font-family: inherit;
          }
          .filter-actions .reset { background: #f0f0f0; color: #333; }
          .filter-actions .reset:hover { background: #e0e0e0; }

          /* ===== 用户搜索 ===== */
          .user-search-wrap { position: relative; }
          .user-search-wrap input {
            width: 100%;
            padding: 8px 10px;
            font-size: 13px;
            border: 1px solid #e0e0e0;
            border-radius: 8px;
            outline: none;
            font-family: inherit;
          }
          .user-search-wrap input:focus { border-color: #07c160; }

          #user-search-dropdown {
            position: absolute;
            left: 0; right: 0;
            top: 100%;
            margin-top: 4px;
            background: #fff;
            border: 1px solid #e0e0e0;
            border-radius: 8px;
            box-shadow: 0 4px 16px rgba(0,0,0,0.12);
            max-height: 180px;
            overflow-y: auto;
            display: none;
            z-index: 100;
          }
          #user-search-dropdown.show { display: block; }
          .user-search-item {
            display: flex; align-items: center; gap: 8px;
            padding: 8px 10px; cursor: pointer; font-size: 13px;
          }
          .user-search-item:hover { background: #f0f9f4; }
          .user-search-item .avatar {
            width: 22px; height: 22px; border-radius: 50%;
            background: #07c160; color: #fff;
            display: flex; align-items: center; justify-content: center;
            font-size: 10px; font-weight: bold; overflow: hidden; flex-shrink: 0;
          }
          .user-search-item .avatar img { width: 100%; height: 100%; object-fit: cover; }
          .user-search-item .nick {
            flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
          }
          .user-search-item .uname { font-size: 11px; color: #999; }
          .user-search-empty {
            padding: 10px; font-size: 12px; color: #999; text-align: center;
          }

          /* ===== 日期按钮 ===== */
          .date-picker-btn {
            width: 100%;
            padding: 9px 12px;
            font-size: 13px;
            font-family: inherit;
            background: #fafafa;
            color: #333;
            border: 1px solid #e0e0e0;
            border-radius: 8px;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            transition: all 0.15s;
          }
          .date-picker-btn:hover { border-color: #07c160; color: #07c160; background: #f0f9f4; }

          /* ===== 日历弹窗 ===== */
          .calendar-mask {
            position: fixed; inset: 0;
            background: rgba(0,0,0,0.45);
            display: none;
            align-items: center; justify-content: center;
            z-index: 1000;
            padding: 20px;
          }
          .calendar-mask.show { display: flex; }
          .calendar {
            background: #fff;
            border-radius: 14px;
            padding: 18px;
            width: 100%;
            max-width: 320px;
            box-shadow: 0 10px 40px rgba(0,0,0,0.25);
          }
          .calendar-header {
            display: flex; align-items: center; justify-content: space-between;
            margin-bottom: 12px;
          }
          .calendar-header button {
            background: none; border: none; cursor: pointer;
            font-size: 18px; color: #666; padding: 4px 10px;
            border-radius: 6px; font-family: inherit;
          }
          .calendar-header button:hover { background: #f0f0f0; }
          .calendar-header #cal-title {
            font-size: 15px; font-weight: 500; color: #333;
            background: none; border: none; cursor: pointer;
            padding: 4px 10px; border-radius: 6px; font-family: inherit;
          }
          .calendar-header #cal-title:hover { background: #f0f0f0; }
          .calendar-header #cal-title::after { content: ' ▾'; font-size: 11px; color: #999; }
          .calendar-years {
            display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px;
          }
          .cal-year-cell {
            padding: 12px 0; text-align: center; font-size: 14px;
            border: 1px solid #e5e5e5; border-radius: 8px;
            background: #fff; cursor: pointer; font-family: inherit;
            color: #333;
          }
          .cal-year-cell:hover { border-color: #07c160; color: #07c160; }
          .cal-year-cell.current { background: #07c160; color: #fff; border-color: #07c160; }
          .calendar-weekdays {
            display: grid; grid-template-columns: repeat(7, 1fr);
            text-align: center; font-size: 12px; color: #999;
            margin-bottom: 6px;
          }
          .calendar-days {
            display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px;
          }
          .cal-day {
            aspect-ratio: 1;
            display: flex; align-items: center; justify-content: center;
            font-size: 13px;
            border-radius: 8px;
            color: #ddd;
          }
          .cal-day.has-data {
            color: #333; cursor: pointer;
            background: #f0f9f4; font-weight: 500;
          }
          .cal-day.has-data:hover { background: #07c160; color: #fff; }
          .calendar-footer {
            margin-top: 12px; display: flex; justify-content: flex-end;
          }
          .calendar-footer button {
            padding: 7px 18px; font-size: 13px;
            border: none; border-radius: 6px;
            background: #f0f0f0; color: #333;
            cursor: pointer; font-family: inherit;
          }
          .calendar-footer button:hover { background: #e0e0e0; }

          /* ===== 定位到某条消息时的高亮 ===== */
          .msg.highlight-flash .bubble {
            animation: flashHighlight 1.8s ease-out;
          }
          @keyframes flashHighlight {
            0%   { box-shadow: 0 0 0 4px rgba(7,193,96,0.9); }
            100% { box-shadow: 0 0 0 0 rgba(7,193,96,0); }
          }

          #image-preview { position: fixed; inset: 0; background: rgba(0,0,0,0.92); display: none; align-items: center; justify-content: center; z-index: 999; padding: 20px; overflow: hidden; }
          #image-preview.show { display: flex; }
          .preview-content { display: flex; flex-direction: column; align-items: center; gap: 16px; max-width: 95vw; max-height: 95vh; }
          .preview-content img { max-width: 92vw; max-height: 82vh; object-fit: contain; border-radius: 4px; cursor: grab; user-select: none; -webkit-user-drag: none; will-change: transform; transform: translateZ(0); backface-visibility: hidden; }
          .preview-content img.dragging { cursor: grabbing; transition: none; }
          .preview-content img.smooth { transition: transform 0.1s ease-out; }
          .preview-actions { display: flex; gap: 12px; }
          .preview-actions a, .preview-actions button { padding: 9px 24px; font-size: 14px; border-radius: 6px; border: none; cursor: pointer; text-decoration: none; font-family: inherit; line-height: 1.2; }
          .preview-actions a { background: #07c160; color: #fff; }
          .preview-actions button { background: #fff; color: #333; }
        </style></head><body>
        <div id="header">
          <span class="title">__SITE_TITLE__</span>
          <div class="right">
            <a href="/posts" style="font-size:12px;padding:5px 10px;border:1px solid #07c160;background:#fff;color:#07c160;border-radius:6px;text-decoration:none;">帖子</a>
            <button id="online-btn" class="online-btn">在线 <span id="online-count">0</span></button>
            <a id="admin-link" href="/admin" style="display:none;">管理</a>
            <a href="/profile" title="个人中心"><span class="my-avatar" id="my-avatar"></span></a>
            <span id="my-nickname"></span>
            <button onclick="doLogout()">退出</button>
          </div>
        </div>
        <div id="main">
          <div id="chat-box">
            <div id="loading-tip">加载中...</div>
          </div>
          <div id="sidebar-mask" onclick="toggleSidebar(false)"></div>
          <div id="sidebar">
            <h3>
              <span>在线用户</span>
              <button class="close-btn" onclick="toggleSidebar(false)" title="关闭">×</button>
            </h3>
            <div id="online-list"></div>
          </div>

          <!-- 右侧中部悬浮按钮组 -->
          <div id="float-buttons">
            <button id="btn-top" title="回到最上方">▲</button>
            <button id="btn-filter" title="筛选消息">☰</button>
            <button id="btn-bottom" title="回到最下方">▼</button>
          </div>

          <!-- 筛选遮罩 + 面板 -->
          <div id="filter-mask" onclick="closeFilter()"></div>
          <div id="filter-panel">
            <div class="filter-header">
              <span>筛选消息</span>
              <button class="filter-close" onclick="closeFilter()">×</button>
            </div>
            <div class="filter-body">
              <div class="filter-section">
                <div class="filter-title">
                  <span>搜索用户</span>
                  <button class="clear-btn" onclick="clearUserSearch()">清空</button>
                </div>
                <div class="user-search-wrap">
                  <div id="user-search-dropdown"></div>
                  <input type="text" id="filter-user-search" placeholder="输入昵称或账号..." autocomplete="off">
                </div>
              </div>

              <div class="filter-section">
                <div class="filter-title">
                  <span>跳转到日期</span>
                </div>
                <button type="button" class="date-picker-btn" onclick="openCalendar()">
                  📅 选择年月日
                </button>
              </div>

              <div class="filter-section">
                <div class="filter-title">
                  <span>按类型</span>
                  <button class="clear-btn" onclick="clearSection('filter-type-list')">清空</button>
                </div>
                <div class="filter-options" id="filter-type-list">
                  <label data-value="text"><input type="checkbox">文字</label>
                  <label data-value="image"><input type="checkbox">图片</label>
                </div>
              </div>
            </div>
            <div class="filter-actions">
              <button class="reset" onclick="resetFilters()">重置</button>
            </div>
          </div>
        </div>
        <div id="input-area">
          <div id="input-resize" title="上下拖动调整输入框高度（双击恢复自动高度）"></div>
          <textarea id="input" placeholder="输入消息...（Shift + Enter 换行，Enter 发送，@ 可提醒）" autocomplete="off"></textarea>
          <input type="file" id="image-input" accept="image/*" style="display: none;">
          <button id="img-btn">图</button>
          <button id="send-btn">发送</button>
          <div id="mention-dropdown"></div>
        </div>

        <div id="image-preview" onclick="onMaskClick(event)">
          <div class="preview-content">
            <img id="preview-img" src="" alt="" draggable="false" onwheel="onPreviewWheel(event)">
            <div class="preview-actions">
              <a id="preview-download" href="#" download>下载图片</a>
              <button onclick="resetPreviewZoom()">重置缩放</button>
              <button onclick="closePreview()">关闭</button>
            </div>
          </div>
        </div>

        <div id="calendar-mask" class="calendar-mask" onclick="closeCalendar()">
          <div class="calendar" onclick="event.stopPropagation()">
            <div class="calendar-header">
              <button type="button" onclick="calPrevMonth()">‹</button>
              <button type="button" id="cal-title" title="点击选择年份"></button>
              <button type="button" onclick="calNextMonth()">›</button>
            </div>
            <div class="calendar-weekdays">
              <span>日</span><span>一</span><span>二</span><span>三</span>
              <span>四</span><span>五</span><span>六</span>
            </div>
            <div class="calendar-days" id="cal-days"></div>
            <div class="calendar-years" id="cal-years" style="display:none;"></div>
            <div class="calendar-footer">
              <button type="button" onclick="closeCalendar()">关闭</button>
            </div>
          </div>
        </div>

        <script>
        var TOKEN = localStorage.getItem('token');
        var MY_USERNAME = localStorage.getItem('username') || '';
        var MY_NICKNAME = localStorage.getItem('nickname') || MY_USERNAME;
        var MY_AVATAR = localStorage.getItem('avatarId') || null;
        if (MY_AVATAR === 'null') MY_AVATAR = null;
        var IS_ADMIN = localStorage.getItem('isAdmin') === 'true';
        var BASE_TITLE = document.title;
        var IMAGE_MAX_WIDTH = __IMAGE_MAX_WIDTH__;
        var IMAGE_QUALITY = __IMAGE_QUALITY__;

        if (!TOKEN) { location.href = '/login'; }

        var chatBox = document.getElementById('chat-box');
        var input = document.getElementById('input');
        var sendBtn = document.getElementById('send-btn');
        var imgBtn = document.getElementById('img-btn');
        var imageInput = document.getElementById('image-input');
        var loadingTip = document.getElementById('loading-tip');
        var mentionDropdown = document.getElementById('mention-dropdown');
        var sidebar = document.getElementById('sidebar');
        var sidebarMask = document.getElementById('sidebar-mask');
        var onlineBtn = document.getElementById('online-btn');
        var filterPanel = document.getElementById('filter-panel');
        var filterMask = document.getElementById('filter-mask');
        var btnFilter = document.getElementById('btn-filter');

        var sending = false;
        var unreadCount = 0;
        var isFocused = document.hasFocus();
        var oldestId = null;
        var noMoreHistory = false;
        var loadingHistory = false;
        var onlineUsers = [];
        var historyLoaded = false;
        var lastOnlineSignature = '';
        var filterActive = false;

        function pad2(n) { return n < 10 ? '0' + n : '' + n; }

        function messageDate(data) {
          var t = (typeof data.createdAt === 'number' && isFinite(data.createdAt) && data.createdAt > 0)
              ? data.createdAt
              : Date.now();
          var d = new Date(t);
          return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate());
        }

        function formatTimeNow() {
          var d = new Date();
          return pad2(d.getHours()) + ':' + pad2(d.getMinutes());
        }
        // ==================== 输入框高度 ====================
        var INPUT_MIN_H = 42;                 // 单行高度
        var INPUT_AUTO_MAX_H = 120;           // 自动增高时的上限
        var inputResizeHandle = document.getElementById('input-resize');
        // 用户手动拖过的高度；null 表示仍按内容自动增高
        var userInputHeight = null;

        function inputMaxHeight() {
          return Math.max(INPUT_AUTO_MAX_H, Math.round(window.innerHeight * 0.5));
        }

        /**
         * 内容实际需要的高度（border-box 口径）。
         *
         * 直接用 scrollHeight 即可：它包含 padding，与 box-sizing: border-box 的
         * height 同一口径。不要拿 scrollHeight - clientHeight 去推"文字高度"——
         * 高度为 auto 时 clientHeight 是内容盒高度，两者相减会得出负值。
         *
         * 减去一点经验余量，让空输入框不会比单行高度高出几个像素。
         */
        function inputContentHeight() {
          var prev = input.style.height;
          input.style.height = 'auto';
          var sh = input.scrollHeight;
          input.style.height = prev;
          return Math.max(INPUT_MIN_H, sh - 20);
        }

        function autoResizeInput() {
          var maxH = inputMaxHeight();
          if (userInputHeight !== null) {
            // 用户指定了高度：只保证不低于内容所需，不再自动回落
            input.style.height = Math.min(maxH, Math.max(userInputHeight, inputContentHeight())) + 'px';
            return;
          }
          input.style.height = Math.min(maxH, Math.max(INPUT_MIN_H, inputContentHeight())) + 'px';
        }

        function setUserInputHeight(px) {
          userInputHeight = Math.max(INPUT_MIN_H, Math.min(px, inputMaxHeight()));
          autoResizeInput();
        }
        """;

    /**
     * HTML 模板按片段存放。
     *
     * 原因：JVM 的常量池里，单个字符串常量的 modified-UTF-8 编码不能超过 65535 字节。
     * 整份模板塞进一个文本块会超限，javac 会报出一个很有误导性的 "未闭合字符串文字"。
     * 拆成几段再用 + 拼接，拼接在编译期完成，运行时没有任何额外开销。
     */
    private static final String INDEX_HTML_PART2 = """

        function resetUserInputHeight() {
          userInputHeight = null;
          input.style.height = 'auto';
          autoResizeInput();
        }

        // 拖动上缘调整高度：向上拖变高，向下拖变矮
        (function initInputResize() {
          if (!inputResizeHandle) return;
          var dragging = false;
          var startY = 0;
          var startH = 0;

          inputResizeHandle.addEventListener('mousedown', function (e) {
            dragging = true;
            startY = e.clientY;
            // 拖过之后就以当前实际高度为基准
            startH = input.offsetHeight;
            inputResizeHandle.classList.add('dragging');
            document.body.style.cursor = 'ns-resize';
            e.preventDefault();
          });

          document.addEventListener('mousemove', function (e) {
            if (!dragging) return;
            setUserInputHeight(startH - (e.clientY - startY));
            e.preventDefault();
          });

          document.addEventListener('mouseup', function () {
            if (!dragging) return;
            dragging = false;
            inputResizeHandle.classList.remove('dragging');
            document.body.style.cursor = '';
          });

          // 双击恢复自动高度
          inputResizeHandle.addEventListener('dblclick', function (e) {
            e.preventDefault();
            resetUserInputHeight();
          });

          // 键盘可达：聚焦拖拽条后用上下箭头调整，Esc 复位
          inputResizeHandle.tabIndex = 0;
          inputResizeHandle.addEventListener('keydown', function (e) {
            var base = (userInputHeight === null) ? INPUT_MIN_H : userInputHeight;
            if (e.key === 'ArrowUp') {
              setUserInputHeight(base + 12);      // 上箭头 = 变高
              e.preventDefault();
            } else if (e.key === 'ArrowDown') {
              setUserInputHeight(base - 12);      // 下箭头 = 变矮
              e.preventDefault();
            } else if (e.key === 'Escape') {
              resetUserInputHeight();
              e.preventDefault();
            }
          });
        })();

        // 首次按内容定高，否则会停在浏览器给 textarea 的默认高度上
        autoResizeInput();

        // 窗口尺寸变化时重新夹取，避免高度超出新的上限
        window.addEventListener('resize', function () {
          if (userInputHeight !== null) setUserInputHeight(userInputHeight);
        });

        function updateTitle() {
          document.title = unreadCount > 0 ? '(' + unreadCount + ') ' + BASE_TITLE : BASE_TITLE;
        }
        function isMentionedBy(text) {
          if (!text || !MY_NICKNAME) return false;
          return text.indexOf('@' + MY_NICKNAME) >= 0;
        }
        function isNewline(ch) {
          return ch === ' ' || ch.charCodeAt(0) === 10;
        }
        function playDing() {
          try {
            var ctx = new (window.AudioContext || window.webkitAudioContext)();
            var osc = ctx.createOscillator();
            var gain = ctx.createGain();
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.frequency.value = 880;
            osc.type = 'sine';
            gain.gain.setValueAtTime(0.6, ctx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.3);
            osc.start();
            osc.stop(ctx.currentTime + 0.3);

            var osc2 = ctx.createOscillator();
            var gain2 = ctx.createGain();
            osc2.connect(gain2);
            gain2.connect(ctx.destination);
            osc2.frequency.value = 1320;
            osc2.type = 'sine';
            gain2.gain.setValueAtTime(0.3, ctx.currentTime);
            gain2.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.3);
            osc2.start();
            osc2.stop(ctx.currentTime + 0.3);
          } catch (e) {}
        }

        function toggleSidebar(show) {
          if (typeof show === 'undefined') {
            show = !sidebar.classList.contains('show');
          }
          if (show) {
            sidebar.classList.add('show');
            sidebarMask.classList.add('show');
            onlineBtn.classList.add('active');
          } else {
            sidebar.classList.remove('show');
            sidebarMask.classList.remove('show');
            onlineBtn.classList.remove('active');
          }
        }
        onlineBtn.onclick = function () { toggleSidebar(); };

        document.getElementById('my-nickname').textContent = MY_NICKNAME;
        if (IS_ADMIN) {
          var adminLink = document.getElementById('admin-link');
          adminLink.style.display = 'inline-block';
          adminLink.href = '/admin?token=' + encodeURIComponent(TOKEN);
        }
        renderMyAvatar();

        function renderMyAvatar() {
          var el = document.getElementById('my-avatar');
          el.innerHTML = '';
          if (MY_AVATAR) {
            var img = document.createElement('img');
            img.src = '/image/' + MY_AVATAR;
            el.appendChild(img);
          } else {
            el.textContent = MY_NICKNAME.charAt(0).toUpperCase();
          }
        }

        window.addEventListener('focus', function () {
          MY_NICKNAME = localStorage.getItem('nickname') || MY_USERNAME;
          MY_AVATAR = localStorage.getItem('avatarId');
          if (MY_AVATAR === 'null') MY_AVATAR = null;
          document.getElementById('my-nickname').textContent = MY_NICKNAME;
          renderMyAvatar();
          isFocused = true;
          unreadCount = 0;
          updateTitle();
        });
        window.addEventListener('blur', function () { isFocused = false; });

        if ('Notification' in window && Notification.permission === 'default') {
          Notification.requestPermission();
        }

        var es = new EventSource('/events?token=' + encodeURIComponent(TOKEN));
        es.onmessage = function (e) {
          if (!e || !e.data || typeof e.data !== 'string') return;
          var raw = e.data.trim();
          if (raw.length === 0) return;

          var data;
          try {
            data = JSON.parse(raw);
          } catch (err) {
            console.warn('SSE 解析失败:', raw);
            return;
          }

          if (data.type === 'online_list') {
            onlineUsers = data.users || [];
            renderOnlineUsers(onlineUsers);
            return;
          }

          if (data.type === 'kick') {
            alert(data.reason || '你已被管理员踢出');
            localStorage.clear();
            location.href = '/login';
            return;
          }
          if (data.type === 'recall') {
            var el = document.querySelector('.msg[data-id="' + data.id + '"]');
            if (el) el.remove();
            return;
          }
          if (data.type === 'history_end') {
            historyLoaded = true;
            updateOldestId();
            maybeLoadMore();
            return;
          }
          appendMessage(data, false);

          var isSelf = data.username === MY_USERNAME;
          if (!isSelf && !isFocused) {
            unreadCount++;
            updateTitle();
          }
          if (historyLoaded && !isSelf && isMentionedBy(data.text)) {
            playDing();
          }
        };

        // ==================== 消息渲染 ====================
        function appendMessage(data, prepend) {
          var isSelf = data.username === MY_USERNAME;
          var div = document.createElement('div');
          div.className = 'msg ' + (isSelf ? 'self' : 'other');
          if (data.id != null) div.dataset.id = data.id;

          // 筛选属性
          div.dataset.username = data.username || '';
          div.dataset.nickname = data.nickname || data.username || '';
          div.dataset.type = data.type || 'text';
          div.dataset.time = data.time || '';
          div.dataset.avatar = data.avatarId || '';
          div.dataset.date = messageDate(data);

          var mentioned = !isSelf && isMentionedBy(data.text);
          if (mentioned) div.classList.add('mention');

          var avatarDiv = document.createElement('div');
          avatarDiv.className = 'avatar';
          if (data.avatarId) {
            var av = document.createElement('img');
            av.src = '/image/' + data.avatarId;
            avatarDiv.appendChild(av);
          } else {
            avatarDiv.textContent = (data.nickname || data.username).charAt(0).toUpperCase();
          }

          var bodyDiv = document.createElement('div');
          bodyDiv.className = 'body';
          var metaDiv = document.createElement('div');
          metaDiv.className = 'meta';
          if (mentioned) {
            var badge = document.createElement('span');
            badge.className = 'mention-badge';
            badge.textContent = '@我';
            metaDiv.appendChild(badge);
          }
          var metaText = document.createElement('span');
          metaText.textContent = (data.nickname || data.username) + ' · ' + data.time;
          metaDiv.appendChild(metaText);

          if (isSelf) {
            var recallBtn = document.createElement('span');
            recallBtn.className = 'recall-btn';
            recallBtn.textContent = '撤回';
            recallBtn.onclick = (function (id) { return function () { recallMessage(id); }; })(data.id);
            metaDiv.appendChild(recallBtn);
          }

          var bubbleDiv = document.createElement('div');
          bubbleDiv.className = 'bubble';
          if (data.type === 'image') {
            var img = document.createElement('img');
            img.src = '/image/' + data.text;
            img.loading = 'lazy';
            img.onclick = (function (id) { return function () { openPreview(id); }; })(data.text);
            bubbleDiv.appendChild(img);
          } else {
            bubbleDiv.textContent = data.text;
          }

          bodyDiv.appendChild(metaDiv);
          bodyDiv.appendChild(bubbleDiv);
          div.appendChild(avatarDiv);
          div.appendChild(bodyDiv);

          if (prepend) {
            chatBox.insertBefore(div, chatBox.firstChild);
          } else {
            chatBox.appendChild(div);
            chatBox.scrollTop = chatBox.scrollHeight;
          }

          if (data.id != null) {
            if (oldestId == null || data.id < oldestId) oldestId = data.id;
          }

          if (filterActive) applyFiltersToElement(div);
        }

        function updateOldestId() {
          var all = chatBox.querySelectorAll('.msg[data-id]');
          if (all.length === 0) { oldestId = null; return; }
          var min = Infinity;
          for (var i = 0; i < all.length; i++) {
            var id = parseInt(all[i].dataset.id);
            if (!isNaN(id) && id < min) min = id;
          }
          oldestId = min === Infinity ? null : min;
        }

        async function loadMoreHistory() {
          if (loadingHistory || noMoreHistory || oldestId == null) return;
          loadingHistory = true;
          loadingTip.classList.add('show');
          var oldHeight = chatBox.scrollHeight;
          var oldTop = chatBox.scrollTop;
          try {
            var res = await fetch('/history?token=' + encodeURIComponent(TOKEN) + '&before=' + oldestId);
            var r = await res.json();
            if (r.ok && r.messages.length > 0) {
              for (var i = 0; i < r.messages.length; i++) {
                appendMessage(r.messages[i], true);
              }
              var newHeight = chatBox.scrollHeight;
              chatBox.scrollTop = oldTop + (newHeight - oldHeight);
            } else if (r.ok) {
              noMoreHistory = true;
            }
          } catch (e) {}
          loadingTip.classList.remove('show');
          loadingHistory = false;
        }

        function maybeLoadMore() {
          if (chatBox.scrollHeight <= chatBox.clientHeight + 20) loadMoreHistory();
        }

        chatBox.addEventListener('scroll', function () {
          if (chatBox.scrollTop <= 30) loadMoreHistory();
        });

        async function recallMessage(id) {
          if (!confirm('确定撤回？')) return;
          try {
            var res = await fetch('/recall?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ id: id })
            });
            var r = await res.json();
            if (!r.ok) alert(r.msg || '撤回失败');
          } catch (e) { alert('网络错误'); }
        }

        // ==================== 在线用户渲染 ====================
        function renderOnlineUsers(users) {
          // 兜底：如果服务端返回的在线列表里没有自己，手动补一条
          if (MY_USERNAME) {
            var hasSelf = false;
            for (var k = 0; k < users.length; k++) {
              if (users[k].username === MY_USERNAME) { hasSelf = true; break; }
            }
            if (!hasSelf) {
              users = users.concat([{
                username: MY_USERNAME,
                nickname: MY_NICKNAME,
                avatarId: MY_AVATAR || null
              }]);
            }
          }
          document.getElementById('online-count').textContent = users.length;

          var list = document.getElementById('online-list');
          list.innerHTML = '';
          if (users.length === 0) {
            var empty = document.createElement('div');
            empty.style.color = '#999';
            empty.style.fontSize = '12px';
            empty.style.padding = '10px 4px';
            empty.textContent = '暂无在线用户';
            list.appendChild(empty);
            return;
          }
          for (var i = 0; i < users.length; i++) {
            var u = users[i];
            var div = document.createElement('div');
            div.className = 'online-user';
            var dot = document.createElement('span'); dot.className = 'dot';
            var av = document.createElement('div'); av.className = 'avatar';
            if (u.avatarId) {
              var img = document.createElement('img');
              img.src = '/image/' + u.avatarId;
              av.appendChild(img);
            } else {
              av.textContent = (u.nickname || u.username).charAt(0).toUpperCase();
            }
            var name = document.createElement('span'); name.className = 'name';
            name.textContent = u.nickname || u.username;
            div.appendChild(dot); div.appendChild(av); div.appendChild(name);
            list.appendChild(div);
          }
        }

        // ==================== 悬浮按钮 ====================
        document.getElementById('btn-top').onclick = function () {
          chatBox.scrollTo({ top: 0, behavior: 'smooth' });
        };
        document.getElementById('btn-bottom').onclick = function () {
          chatBox.scrollTo({ top: chatBox.scrollHeight, behavior: 'smooth' });
        };
        btnFilter.onclick = function () {
          if (filterPanel.classList.contains('show')) closeFilter();
          else openFilter();
        };

        // 悬浮按钮的淡化/恢复完全由 CSS 的 :hover / :focus-within 控制，无需 JS。
        // 容器默认 opacity 0.22，鼠标移上去变 1，因此不占视觉但始终可点。

        // ==================== 筛选面板 ====================
        var userSearchInput = document.getElementById('filter-user-search');
        var userSearchDropdown = document.getElementById('user-search-dropdown');
        var selectedUserFilter = null;
        var allFilterUsers = [];
        var userSearchMatches = [];
        // 服务端返回的完整用户目录，用于「搜索用户」下拉
        var userDirectory = [];

        function openFilter() {
          rebuildFilterUsers();
          filterPanel.classList.add('show');
          filterMask.classList.add('show');
          btnFilter.classList.add('active');
        }

        function closeFilter() {
          filterPanel.classList.remove('show');
          filterMask.classList.remove('show');
          btnFilter.classList.remove('active');
          hideUserSearchDropdown();
        }

        /**
         * 收集候选用户。
         *
         * 优先用服务端返回的完整用户目录——只从「已加载的消息」里收集的话，
         * 刷新后只有最近 30 条，没在这个范围里发过言的人就永远搜不到。
         * 目录拿不到时退回原来的 DOM 收集方式。
         */
        function rebuildFilterUsers() {
          var map = {};

          if (userDirectory && userDirectory.length) {
            for (var d = 0; d < userDirectory.length; d++) {
              var du = userDirectory[d];
              if (!du || !du.username) continue;
              map[du.username] = {
                username: du.username,
                nickname: du.nickname || du.username,
                avatarId: du.avatarId || ''
              };
            }
          }

          // 已加载消息里出现过的人一并纳入，兼容目录请求失败的情况
          var msgs = chatBox.querySelectorAll('.msg');
          for (var i = 0; i < msgs.length; i++) {
            var m = msgs[i];
            var uname = m.getAttribute('data-username');
            if (!uname || map[uname]) continue;
            map[uname] = {
              username: uname,
              nickname: m.getAttribute('data-nickname') || uname,
              avatarId: m.getAttribute('data-avatar') || ''
            };
          }

          var arr = [];
          for (var k in map) {
            if (Object.prototype.hasOwnProperty.call(map, k)) arr.push(map[k]);
          }
          arr.sort(function (a, b) {
            return (a.nickname || '').localeCompare(b.nickname || '');
          });
          allFilterUsers = arr;
        }

        // 页面加载后拉一次完整用户目录，让搜索不依赖消息加载进度
        function loadUserDirectory() {
          fetch('/users?token=' + encodeURIComponent(TOKEN))
            .then(function (res) { return res.json(); })
            .then(function (r) {
              if (r && r.ok && r.users) {
                userDirectory = r.users;
                allFilterUsers = [];
              }
            })
            .catch(function () {});
        }
        loadUserDirectory();
        
        function renderUserSearchDropdown(users) {
          userSearchMatches = users;
          userSearchDropdown.innerHTML = '';
          if (users.length === 0) {
            var empty = document.createElement('div');
            empty.className = 'user-search-empty';
            empty.textContent = '没有匹配的用户';
            userSearchDropdown.appendChild(empty);
            userSearchDropdown.classList.add('show');
            return;
          }
          users.slice(0, 50).forEach(function (u) {
            var item = document.createElement('div');
            item.className = 'user-search-item';

            var av = document.createElement('div');
            av.className = 'avatar';
            if (u.avatarId) {
              var img = document.createElement('img');
              img.src = '/image/' + u.avatarId;
              av.appendChild(img);
            } else {
              av.textContent = (u.nickname || u.username).charAt(0).toUpperCase();
            }

            var nick = document.createElement('span');
            nick.className = 'nick';
            nick.textContent = u.nickname || u.username;

            var uname = document.createElement('span');
            uname.className = 'uname';
            uname.textContent = u.username;

            item.appendChild(av);
            item.appendChild(nick);
            item.appendChild(uname);
            item.onclick = function () { selectFilterUser(u); };
            userSearchDropdown.appendChild(item);
          });
          userSearchDropdown.classList.add('show');
        }

        function hideUserSearchDropdown() {
          userSearchDropdown.classList.remove('show');
          userSearchMatches = [];
        }

        // 点击输入框/下拉以外的地方时收起下拉。
        // 判断用 closest('.user-search-wrap')：输入框和下拉本身都在这个容器里，
        // 所以点输入框不会误关，点面板别处或页面任意位置都会收起。
        document.addEventListener('click', function (e) {
          var t = e.target;
          if (t && t.closest && t.closest('.user-search-wrap')) return;
          if (userSearchDropdown.classList.contains('show')) hideUserSearchDropdown();
        });

        // Esc 也能收起；焦点在输入框上时顺便清掉选中项
        userSearchInput.addEventListener('keydown', function (e) {
          if (e.key === 'Escape' || e.keyCode === 27) {
            hideUserSearchDropdown();
            userSearchInput.blur();
          }
        });

        function selectFilterUser(u) {
          selectedUserFilter = u.username;
          userSearchInput.value = u.nickname || u.username;
          hideUserSearchDropdown();
          applyFilters();
          updateFilterBadge();
        }

        function clearUserSearch() {
          userSearchInput.value = '';
          selectedUserFilter = null;
          hideUserSearchDropdown();
          applyFilters();
          updateFilterBadge();
        }

        // 聚焦时总是重建候选并显示下拉，不管输入框里有没有残留内容
        userSearchInput.addEventListener('focus', function () {
          rebuildFilterUsers();
          var q = userSearchInput.value.trim();
          if (!q) {
            renderUserSearchDropdown(allFilterUsers);
            return;
          }
          var lower = q.toLowerCase();
          var matches = allFilterUsers.filter(function (u) {
            return (u.nickname || '').toLowerCase().indexOf(lower) >= 0
                || u.username.toLowerCase().indexOf(lower) >= 0;
          });
          renderUserSearchDropdown(matches);
        });

        userSearchInput.addEventListener('input', function () {
          selectedUserFilter = null;
          var q = userSearchInput.value.trim();
          if (!q) {
            if (allFilterUsers.length === 0) rebuildFilterUsers();
            renderUserSearchDropdown(allFilterUsers);
            applyFilters();
            updateFilterBadge();
            return;
          }
          if (allFilterUsers.length === 0) rebuildFilterUsers();
          var lower = q.toLowerCase();
          var matches = allFilterUsers.filter(function (u) {
            return (u.nickname || '').toLowerCase().indexOf(lower) >= 0
                || u.username.toLowerCase().indexOf(lower) >= 0;
          });
          renderUserSearchDropdown(matches);
          applyFilters();
          updateFilterBadge();
        });

        function getCheckedValues(container) {
          var arr = [];
          container.querySelectorAll('label').forEach(function (label) {
            var cb = label.querySelector('input[type="checkbox"]');
            if (cb && cb.checked) arr.push(label.dataset.value);
          });
          return arr;
        }

        function clearSection(id) {
          var box = document.getElementById(id);
          box.querySelectorAll('label').forEach(function (label) {
            var cb = label.querySelector('input[type="checkbox"]');
            if (cb) cb.checked = false;
            label.classList.remove('checked');
          });
          applyFilters();
          updateFilterBadge();
        }

        function resetFilters() {
          clearSection('filter-type-list');
          clearUserSearch();
          closeFilter();
        }

        // 类型标签点击
        document.querySelectorAll('#filter-type-list label').forEach(function (label) {
          label.onclick = function (e) {
            e.preventDefault();
            var cb = label.querySelector('input[type="checkbox"]');
            cb.checked = !cb.checked;
            if (cb.checked) label.classList.add('checked');
            else label.classList.remove('checked');
            applyFilters();
            updateFilterBadge();
          };
        });

        function matchMessage(msg) {
          var typeFilter = getCheckedValues(document.getElementById('filter-type-list'));
          var q = userSearchInput.value.trim();
          var hasUserFilter = (selectedUserFilter !== null) || (q.length > 0);

          if (typeFilter.length === 0 && !hasUserFilter) return true;

          if (typeFilter.length > 0 && typeFilter.indexOf(msg.dataset.type) < 0) return false;

          if (hasUserFilter) {
            if (selectedUserFilter !== null) {
              if (msg.dataset.username !== selectedUserFilter) return false;
            } else {
              var lower = q.toLowerCase();
              var nick = (msg.dataset.nickname || '').toLowerCase();
              var uname = (msg.dataset.username || '').toLowerCase();
              if (nick.indexOf(lower) < 0 && uname.indexOf(lower) < 0) return false;
            }
          }
          return true;
        }

        function applyFiltersToElement(msg) {
          if (matchMessage(msg)) msg.style.display = '';
          else msg.style.display = 'none';
        }

        function applyFilters() {
          var typeFilter = getCheckedValues(document.getElementById('filter-type-list'));
          var q = userSearchInput.value.trim();
          var anyFilter = typeFilter.length > 0 || q.length > 0 || selectedUserFilter !== null;
          filterActive = anyFilter;

          chatBox.querySelectorAll('.msg').forEach(function (msg) {
            if (anyFilter) applyFiltersToElement(msg);
            else msg.style.display = '';
          });
        }

        function updateFilterBadge() {
          var n = getCheckedValues(document.getElementById('filter-type-list')).length;
          if (selectedUserFilter !== null || userSearchInput.value.trim().length > 0) n++;

          var oldBadge = btnFilter.querySelector('.badge');
          if (oldBadge) oldBadge.remove();
          if (n > 0) {
            var badge = document.createElement('span');
            badge.className = 'badge';
            badge.textContent = n;
            btnFilter.appendChild(badge);
          }
        }

        // ==================== 日历 ====================
        var calYear = 0;
        var calMonth = 0;   // 0-11
        // 'month' = 日期网格；'year' = 年份网格（点标题切换）
        var calView = 'month';

        // 点击标题在"日期 / 年份"两种视图间切换，用于快速跳到很久以前的记录
        document.getElementById('cal-title').addEventListener('click', calToggleYearView);

        function openCalendar() {
          var now = new Date();
          calYear = now.getFullYear();
          calMonth = now.getMonth();
          renderCalendar();
          document.getElementById('calendar-mask').classList.add('show');
        }

        function closeCalendar() {
          document.getElementById('calendar-mask').classList.remove('show');
        }

        // 左右箭头在两种视图下含义不同：日期视图翻月，年份视图翻 12 年
        function calPrevMonth() {
          if (calView === 'year') { calYear -= 12; renderYearGrid(); return; }
          calMonth--;
          if (calMonth < 0) { calMonth = 11; calYear--; }
          renderCalendar();
        }

        function calNextMonth() {
          if (calView === 'year') { calYear += 12; renderYearGrid(); return; }
          calMonth++;
          if (calMonth > 11) { calMonth = 0; calYear++; }
          renderCalendar();
        }

        // 点标题：日期视图 ⇄ 年份视图
        function calToggleYearView() {
          if (calView === 'year') { renderCalendar(); return; }
          renderYearGrid();
        }

        function renderYearGrid() {
          calView = 'year';
          document.getElementById('cal-title').textContent = '选择年份';

          var base = calYear - 5;   // 让当前年份大致居中
          var box = document.getElementById('cal-years');
          box.innerHTML = '';
          for (var i = 0; i < 12; i++) {
            var y = base + i;
            var el = document.createElement('button');
            el.type = 'button';
            el.className = 'cal-year-cell' + (y === calYear ? ' current' : '');
            el.textContent = y + '';
            (function (yy) {
              el.onclick = function () { calYear = yy; renderCalendar(); };
            })(y);
            box.appendChild(el);
          }

          document.getElementById('cal-days').style.display = 'none';
          document.querySelector('.calendar-weekdays').style.display = 'none';
          box.style.display = 'grid';
        }

        function renderCalendar() {
          calView = 'month';
          document.getElementById('cal-title').textContent = calYear + '年' + (calMonth + 1) + '月';

          // 从年份视图切回来时恢复日期网格
          document.getElementById('cal-years').style.display = 'none';
          document.querySelector('.calendar-weekdays').style.display = '';
          document.getElementById('cal-days').style.display = '';
        """;

    private static final String INDEX_HTML_PART3 = """

          var daysBox = document.getElementById('cal-days');
          daysBox.innerHTML = '';

          // 收集当前已渲染消息里出现过的日期
          var available = {};
          chatBox.querySelectorAll('.msg').forEach(function (m) {
            if (m.dataset.date) available[m.dataset.date] = true;
          });

          var firstDay = new Date(calYear, calMonth, 1).getDay();
          var daysInMonth = new Date(calYear, calMonth + 1, 0).getDate();

          for (var i = 0; i < firstDay; i++) {
            var blank = document.createElement('div');
            blank.className = 'cal-day';
            daysBox.appendChild(blank);
          }

          for (var d = 1; d <= daysInMonth; d++) {
            var dateStr = calYear + '-' + pad2(calMonth + 1) + '-' + pad2(d);
            var el = document.createElement('div');
            el.className = 'cal-day';
            el.textContent = d;
            if (available[dateStr]) {
              el.classList.add('has-data');
              (function (ds) {
                el.onclick = function () { jumpToDate(ds); };
              })(dateStr);
            }
            daysBox.appendChild(el);
          }
        }

        function jumpToDate(dateStr) {
          closeCalendar();
          closeFilter();

          chatBox.querySelectorAll('.msg').forEach(function (m) { m.style.display = ''; });
          filterActive = false;

          var target = null;
          var msgs = chatBox.querySelectorAll('.msg');
          for (var i = 0; i < msgs.length; i++) {
            if (msgs[i].dataset.date === dateStr) { target = msgs[i]; break; }
          }
          if (!target) {
            alert('没有找到 ' + dateStr + ' 的消息（更早的历史可能还没加载，请先往上滚动加载更多）');
            return;
          }

          chatBox.scrollTop = Math.max(0, target.offsetTop - chatBox.clientHeight / 3);

          target.classList.add('highlight-flash');
          setTimeout(function () { target.classList.remove('highlight-flash'); }, 1800);
        }

        // ==================== @ 下拉 ====================
        var mentionStart = -1;
        var mentionActiveIdx = 0;
        var mentionFiltered = [];

        function renderMentionDropdown() {
          mentionDropdown.innerHTML = '';
          for (var i = 0; i < mentionFiltered.length; i++) {
            var u = mentionFiltered[i];
            var item = document.createElement('div');
            item.className = 'mention-item' + (i === mentionActiveIdx ? ' active' : '');
            var av = document.createElement('div'); av.className = 'avatar';
            if (u.avatarId) {
              var img = document.createElement('img');
              img.src = '/image/' + u.avatarId;
              av.appendChild(img);
            } else {
              av.textContent = (u.nickname || u.username).charAt(0).toUpperCase();
            }
            var name = document.createElement('span');
            name.textContent = u.nickname || u.username;
            item.appendChild(av); item.appendChild(name);
            item.onclick = (function (user) { return function () { selectMention(user); }; })(u);
            mentionDropdown.appendChild(item);
          }
        }

        function hideMentionDropdown() {
          mentionDropdown.classList.remove('show');
          mentionStart = -1;
        }

        function selectMention(u) {
          if (mentionStart < 0) return;
          var before = input.value.substring(0, mentionStart);
          var after = input.value.substring(input.selectionStart);
          var nick = u.nickname || u.username;
          var insert = '@' + nick + ' ';
          input.value = before + insert + after;
          var cursor = before.length + insert.length;
          input.setSelectionRange(cursor, cursor);
          input.focus();
          hideMentionDropdown();
          autoResizeInput();
        }

        function updateMentionDropdown() {
          var pos = input.selectionStart;
          var atIdx = -1;
          for (var i = pos - 1; i >= 0; i--) {
            var c = input.value[i];
            if (c === '@') { atIdx = i; break; }
            if (isNewline(c)) break;
          }
          if (atIdx < 0) { hideMentionDropdown(); return; }
          mentionStart = atIdx;
          var typed = input.value.substring(atIdx + 1, pos).toLowerCase();
          var others = onlineUsers.filter(function (u) { return u.username !== MY_USERNAME; });
          if (typed) {
            mentionFiltered = others.filter(function (u) {
              return (u.nickname || '').toLowerCase().indexOf(typed) >= 0 || u.username.toLowerCase().indexOf(typed) >= 0;
            });
          } else {
            mentionFiltered = others;
          }
          if (mentionFiltered.length === 0) { hideMentionDropdown(); return; }
          if (mentionActiveIdx >= mentionFiltered.length) mentionActiveIdx = 0;
          renderMentionDropdown();
          mentionDropdown.classList.add('show');
        }

        // ==================== 图片预览 ====================
        var previewScale = 1;
        var previewMinScale = 0.2;
        var previewMaxScale = 8;
        var previewX = 0;
        var previewY = 0;
        var dragging = false;
        var dragStartX = 0;
        var dragStartY = 0;
        var dragOffsetX = 0;
        var dragOffsetY = 0;

        function applyPreviewTransform(smooth) {
          var img = document.getElementById('preview-img');
          if (smooth) img.classList.add('smooth');
          else img.classList.remove('smooth');
          img.style.transform = 'translate(' + previewX + 'px, ' + previewY + 'px) scale(' + previewScale + ')';
        }

        function openPreview(id) {
          previewScale = 1;
          previewX = 0;
          previewY = 0;
          dragging = false;
          var img = document.getElementById('preview-img');
          img.src = '/image/' + id;
          img.classList.remove('dragging');
          applyPreviewTransform(false);
          var dl = document.getElementById('preview-download');
          dl.href = '/image/' + id + '?download=1';
          dl.setAttribute('download', 'image_' + id + '.jpg');
          document.getElementById('image-preview').classList.add('show');
        }

        function closePreview() {
          document.getElementById('image-preview').classList.remove('show');
          var img = document.getElementById('preview-img');
          img.src = '';
          img.classList.remove('dragging');
          previewScale = 1;
          previewX = 0;
          previewY = 0;
          dragging = false;
        }

        function resetPreviewZoom() {
          previewScale = 1;
          previewX = 0;
          previewY = 0;
          applyPreviewTransform(true);
        }

        function clampPreviewPosition() {
          var img = document.getElementById('preview-img');
          if (!img.src) return;
          var rect = img.getBoundingClientRect();
          var vw = window.innerWidth;
          var vh = window.innerHeight;
          var margin = 80;
          var dx = 0, dy = 0;
          if (rect.right < margin) dx = margin - rect.right;
          if (rect.left > vw - margin) dx = (vw - margin) - rect.left;
          if (rect.bottom < margin) dy = margin - rect.bottom;
          if (rect.top > vh - margin) dy = (vh - margin) - rect.top;
          if (dx !== 0 || dy !== 0) {
            previewX += dx;
            previewY += dy;
            img.style.transform = 'translate(' + previewX + 'px, ' + previewY + 'px) scale(' + previewScale + ')';
          }
        }

        function onPreviewWheel(e) {
          e.preventDefault();
          var delta = -e.deltaY * 0.002;
          previewScale = previewScale * (1 + delta);
          if (previewScale < previewMinScale) previewScale = previewMinScale;
          if (previewScale > previewMaxScale) previewScale = previewMaxScale;
          applyPreviewTransform(true);
          setTimeout(clampPreviewPosition, 120);
        }

        function onPreviewMouseDown(e) {
          if (e.button !== 0) return;
          dragging = true;
          dragStartX = e.clientX;
          dragStartY = e.clientY;
          dragOffsetX = previewX;
          dragOffsetY = previewY;
          var img = document.getElementById('preview-img');
          img.classList.add('dragging');
          e.preventDefault();
        }

        function onPreviewMouseMove(e) {
          if (!dragging) return;
          previewX = dragOffsetX + (e.clientX - dragStartX);
          previewY = dragOffsetY + (e.clientY - dragStartY);
          applyPreviewTransform(false);
        }

        function onPreviewMouseUp(e) {
          if (!dragging) return;
          dragging = false;
          var img = document.getElementById('preview-img');
          img.classList.remove('dragging');
          clampPreviewPosition();
        }

        function onMaskClick(e) {
          if (e.target.id === 'image-preview') closePreview();
        }

        (function() {
          var img = document.getElementById('preview-img');
          img.addEventListener('dblclick', resetPreviewZoom);
          img.addEventListener('mousedown', onPreviewMouseDown);
          document.addEventListener('mousemove', onPreviewMouseMove);
          document.addEventListener('mouseup', onPreviewMouseUp);
        })();

        // ==================== 发送 ====================
        async function sendText() {
          var text = input.value.trim();
          if (!text || sending) return;
          sending = true;
          var time = formatTimeNow();
          input.value = '';
          autoResizeInput();
          hideMentionDropdown();
          input.focus();
          try {
            await fetch('/send?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              body: JSON.stringify({ text: text, time: time, type: 'text' })
            });
          } catch (e) {}
          setTimeout(function () { sending = false; }, 150);
        }

        async function sendImage(base64) {
          if (sending) return;
          sending = true;
          imgBtn.disabled = true;
          imgBtn.textContent = '...';
          try {
            var res = await fetch('/image/upload?token=' + encodeURIComponent(TOKEN) + '&type=message', { method: 'POST', body: base64 });
            if (!res.ok) { alert('图片上传失败：' + res.status); return; }
            var imageId = (await res.text()).trim();
            var time = formatTimeNow();
            await fetch('/send?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              body: JSON.stringify({ text: imageId, time: time, type: 'image' })
            });
          } catch (e) {
            alert('发送失败：' + e.message);
          } finally {
            imgBtn.disabled = false;
            imgBtn.textContent = '图';
            sending = false;
          }
        }

        function compressImage(file, maxWidth, callback) {
          var reader = new FileReader();
          reader.onload = function (e) {
            var img = new Image();
            img.onload = function () {
              var canvas = document.createElement('canvas');
              var width = img.width, height = img.height;
              if (width > maxWidth) {
                height = Math.round(height * maxWidth / width);
                width = maxWidth;
              }
              canvas.width = width;
              canvas.height = height;
              canvas.getContext('2d').drawImage(img, 0, 0, width, height);
              callback(canvas.toDataURL('image/jpeg', IMAGE_QUALITY));
            };
            img.src = e.target.result;
          };
          reader.readAsDataURL(file);
        }

        function doLogout() {
          if (!confirm('确定退出登录？')) return;
          fetch('/api/logout?token=' + encodeURIComponent(TOKEN), { method: 'POST' }).finally(function () {
            localStorage.clear();
            location.href = '/login';
          });
        }

        input.addEventListener('keydown', function (e) {
          if (mentionDropdown.classList.contains('show')) {
            if (e.key === 'ArrowDown') {
              e.preventDefault();
              mentionActiveIdx = (mentionActiveIdx + 1) % mentionFiltered.length;
              renderMentionDropdown();
              return;
            }
            if (e.key === 'ArrowUp') {
              e.preventDefault();
              mentionActiveIdx = (mentionActiveIdx - 1 + mentionFiltered.length) % mentionFiltered.length;
              renderMentionDropdown();
              return;
            }
            if (e.key === 'Enter' || e.key === 'Tab') {
              e.preventDefault();
              if (mentionFiltered[mentionActiveIdx]) selectMention(mentionFiltered[mentionActiveIdx]);
              return;
            }
            if (e.key === 'Escape') {
              e.preventDefault();
              hideMentionDropdown();
              return;
            }
          }
          if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendText();
          }
        });

        input.addEventListener('input', function () {
          autoResizeInput();
          updateMentionDropdown();
        });

        document.addEventListener('keydown', function (e) {
          if (e.key === 'Escape') {
            closePreview();
            hideMentionDropdown();
            toggleSidebar(false);
            closeFilter();
            closeCalendar();
          }
        });

        sendBtn.onclick = sendText;
        imgBtn.onclick = function () { imageInput.click(); };
        imageInput.onchange = function (e) {
          var file = e.target.files[0];
          if (!file) return;
          compressImage(file, IMAGE_MAX_WIDTH, function (base64) { sendImage(base64); });
          e.target.value = '';
        };
        </script></body></html>
        """;

    /**
     * 完整页面：三段拼接后再替换模板占位符。
     *
     * 这里刻意用 StringBuilder 在**运行时**拼接。如果写成常量表达式相加，
     * javac 会把三段折叠成一个 85KB 的字符串常量，又会撞上 65535 字节上限，
     * 等于白拆。走 StringBuilder 就不会折叠。
     */
    public static final String INDEX_HTML = buildIndexHtml();

    private static String buildIndexHtml() {
        StringBuilder sb = new StringBuilder(90000);
        sb.append(INDEX_HTML_PART1).append(INDEX_HTML_PART2).append(INDEX_HTML_PART3);
        return sb.toString()
                .replace("__SITE_TITLE__", Config.SITE_TITLE)
                .replace("__IMAGE_MAX_WIDTH__", String.valueOf(Config.IMAGE_MAX_WIDTH))
                .replace("__IMAGE_QUALITY__", String.valueOf(Config.IMAGE_QUALITY));
    }
}