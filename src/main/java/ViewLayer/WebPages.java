package ViewLayer;

import ConfigurationLayer.Config;

/** 存放前端 HTML 模板 */
public class WebPages {

    public static final String INDEX_HTML = """
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
          .my-avatar { width: 28px; height: 28px; border-radius: 50%; background: #07c160; color: #fff; display: inline-flex; align-items: center; justify-content: center; font-weight: bold; font-size: 13px; overflow: hidden; cursor: pointer; text-decoration: none; }
          .my-avatar img { width: 100%; height: 100%; object-fit: cover; }
          #main { flex: 1; display: flex; overflow: hidden; }
          #chat-box { flex: 1; overflow-y: auto; padding: 15px; display: flex; flex-direction: column; position: relative; }
          #loading-tip { text-align: center; color: #999; font-size: 12px; padding: 8px; display: none; }
          #loading-tip.show { display: block; }
          #sidebar { width: 180px; background: #fafafa; border-left: 1px solid #e5e5e5; overflow-y: auto; padding: 12px 10px; font-size: 13px; }
          #sidebar h3 { font-size: 12px; color: #999; font-weight: normal; margin-bottom: 10px; }
          .online-user { display: flex; align-items: center; gap: 8px; padding: 6px 4px; border-radius: 6px; color: #333; }
          .online-user .dot { width: 8px; height: 8px; border-radius: 50%; background: #07c160; flex-shrink: 0; }
          .online-user .name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
          .online-user .avatar { width: 24px; height: 24px; border-radius: 50%; background: #bbb; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: bold; overflow: hidden; flex-shrink: 0; }
          .online-user .avatar img { width: 100%; height: 100%; object-fit: cover; }
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
          #input { flex: 1; border: 1px solid #ddd; border-radius: 6px; padding: 10px; font-size: 15px; resize: none; min-height: 42px; max-height: 120px; font-family: inherit; line-height: 1.4; outline: none; }
          #input:focus { border-color: #07c160; }
          #img-btn { background: #fff; border: 1px solid #ddd; border-radius: 6px; padding: 10px 12px; font-size: 15px; cursor: pointer; color: #333; }
          #send-btn { background: #07c160; color: #fff; border: none; border-radius: 6px; padding: 10px 20px; font-size: 15px; cursor: pointer; }
          #send-btn:disabled, #img-btn:disabled { opacity: 0.5; cursor: not-allowed; }
          .bubble img { max-width: 100%; max-height: 40vh; width: auto; height: auto; border-radius: 4px; display: block; cursor: zoom-in; object-fit: contain; }
          #mention-dropdown { position: absolute; bottom: 60px; left: 10px; background: #fff; border: 1px solid #ddd; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15); max-height: 240px; overflow-y: auto; min-width: 200px; display: none; z-index: 50; }
          #mention-dropdown.show { display: block; }
          .mention-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; cursor: pointer; font-size: 14px; }
          .mention-item:hover, .mention-item.active { background: #f0f9f4; }
          .mention-item .avatar { width: 24px; height: 24px; border-radius: 50%; background: #07c160; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: bold; overflow: hidden; }
          .mention-item .avatar img { width: 100%; height: 100%; object-fit: cover; }
          #image-preview { position: fixed; inset: 0; background: rgba(0,0,0,0.92); display: none; align-items: center; justify-content: center; z-index: 999; padding: 20px; overflow: hidden; }
          #image-preview.show { display: flex; }
          .preview-content { display: flex; flex-direction: column; align-items: center; gap: 16px; max-width: 95vw; max-height: 95vh; }
          .preview-content img { max-width: 92vw; max-height: 82vh; object-fit: contain; border-radius: 4px; cursor: grab; user-select: none; -webkit-user-drag: none; }
          .preview-content img.dragging { cursor: grabbing; transition: none; }
          .preview-content img.smooth { transition: transform 0.1s ease-out; }
          .preview-actions { display: flex; gap: 12px; }
          .preview-actions a, .preview-actions button { padding: 9px 24px; font-size: 14px; border-radius: 6px; border: none; cursor: pointer; text-decoration: none; font-family: inherit; line-height: 1.2; }
          .preview-actions a { background: #07c160; color: #fff; }
          .preview-actions button { background: #fff; color: #333; }
          @media (max-width: 600px) { #sidebar { display: none; } }
        </style></head><body>
        <div id="header">
          <span class="title">__SITE_TITLE__</span>
          <div class="right">
            <a href="/posts" style="font-size:12px;padding:5px 10px;border:1px solid #07c160;background:#fff;color:#07c160;border-radius:6px;text-decoration:none;">帖子</a>
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
          <div id="sidebar">
            <h3>在线用户 <span id="online-count">0</span></h3>
            <div id="online-list"></div>
          </div>
        </div>
        <div id="input-area">
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

        var sending = false;
        var unreadCount = 0;
        var isFocused = document.hasFocus();
        var oldestId = null;
        var noMoreHistory = false;
        var loadingHistory = false;
        var onlineUsers = [];

        function formatTimeNow() {
          var d = new Date();
          return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
        }
        function autoResizeInput() {
          input.style.height = 'auto';
          input.style.height = Math.min(input.scrollHeight, 120) + 'px';
        }
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
            gain.gain.setValueAtTime(0.15, ctx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.3);
            osc.start();
            osc.stop(ctx.currentTime + 0.3);
          } catch (e) {}
        }

        document.getElementById('my-nickname').textContent = MY_NICKNAME;
        if (IS_ADMIN) document.getElementById('admin-link').style.display = 'inline-block';
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
            updateOldestId();
            maybeLoadMore();
            return;
          }
          appendMessage(data, false);
          loadOnlineUsers();

          var isSelf = data.username === MY_USERNAME;
          if (!isSelf && !isFocused) {
            unreadCount++;
            updateTitle();
          }
          if (!isSelf && isMentionedBy(data.text)) {
            playDing();
          }
        };

        function appendMessage(data, prepend) {
          var isSelf = data.username === MY_USERNAME;
          var div = document.createElement('div');
          div.className = 'msg ' + (isSelf ? 'self' : 'other');
          if (data.id != null) div.dataset.id = data.id;

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

        async function loadOnlineUsers() {
          try {
            var res = await fetch('/online?token=' + encodeURIComponent(TOKEN));
            var r = await res.json();
            if (!r.ok) return;
            onlineUsers = r.users;
            renderOnlineUsers(r.users);
          } catch (e) {}
        }

        function renderOnlineUsers(users) {
          document.getElementById('online-count').textContent = users.length;
          var list = document.getElementById('online-list');
          list.innerHTML = '';
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

        loadOnlineUsers();
        setInterval(loadOnlineUsers, 30000);

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

        // ==================== 图片预览 + 滚轮缩放 + 拖动 ====================
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
          if (e.key === 'Escape') { closePreview(); hideMentionDropdown(); }
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
        """.replace("__SITE_TITLE__", Config.SITE_TITLE)
            .replace("__IMAGE_MAX_WIDTH__", String.valueOf(Config.IMAGE_MAX_WIDTH))
            .replace("__IMAGE_QUALITY__", String.valueOf(Config.IMAGE_QUALITY));
}