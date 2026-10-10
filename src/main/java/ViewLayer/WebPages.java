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
          body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #e5e5e5; height: 100vh; display: flex; flex-direction: column; }
          #header { background: #fff; padding: 10px 15px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); z-index: 10; display: flex; align-items: center; justify-content: space-between; }
          #header .left { display: flex; align-items: center; gap: 8px; }
          #header .title { font-size: 15px; font-weight: bold; }
          #header .right { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #666; }
          #header .right a, #header .right button {
            font-size: 12px; padding: 5px 10px; border: 1px solid #ddd; background: #fff;
            border-radius: 6px; cursor: pointer; color: #666; text-decoration: none;
          }
          #header .right a:hover, #header .right button:hover { background: #f5f5f5; }
          .my-avatar {
            width: 28px; height: 28px; border-radius: 50%; background: #07c160; color: #fff;
            display: inline-flex; align-items: center; justify-content: center;
            font-weight: bold; font-size: 13px; overflow: hidden; cursor: pointer;
            text-decoration: none;
          }
          .my-avatar img { width: 100%; height: 100%; object-fit: cover; }
          #chat-box { flex: 1; overflow-y: auto; padding: 15px; display: flex; flex-direction: column; }
          .msg { max-width: 78%; margin-bottom: 15px; display: flex; gap: 8px; }
          .msg.self { align-self: flex-end; flex-direction: row-reverse; }
          .msg.other { align-self: flex-start; }
          .msg .avatar {
            width: 36px; height: 36px; border-radius: 50%; background: #bbb; color: #fff;
            display: flex; align-items: center; justify-content: center;
            font-weight: bold; font-size: 14px; flex-shrink: 0; overflow: hidden;
          }
          .msg .avatar img { width: 100%; height: 100%; object-fit: cover; }
          .msg .body { display: flex; flex-direction: column; min-width: 0; }
          .msg.self .body { align-items: flex-end; }
          .msg .bubble { padding: 10px 14px; border-radius: 12px; word-wrap: break-word; font-size: 15px; line-height: 1.4; }
          .self .bubble { background: #07c160; color: #fff; border-top-right-radius: 2px; }
          .other .bubble { background: #fff; color: #333; border-top-left-radius: 2px; }
          .meta { font-size: 12px; color: #999; margin-bottom: 4px; }
          .self .meta { text-align: right; }
          #input-area { background: #fff; padding: 10px; display: flex; box-shadow: 0 -1px 3px rgba(0,0,0,0.1); }
          #input { flex: 1; border: 1px solid #ddd; border-radius: 6px; padding: 10px; font-size: 15px; resize: none; height: 42px; }
          #input:focus { outline: none; border-color: #07c160; }
          #img-btn { background: #fff; border: 1px solid #ddd; border-radius: 6px; padding: 0 12px; margin-left: 8px; font-size: 15px; cursor: pointer; color: #333; }
          #send-btn { background: #07c160; color: #fff; border: none; border-radius: 6px; padding: 0 20px; margin-left: 8px; font-size: 15px; cursor: pointer; }
          #send-btn:disabled, #img-btn:disabled { opacity: 0.5; cursor: not-allowed; }
          .bubble img { max-width: 100%; border-radius: 4px; display: block; cursor: zoom-in; }

          /* 图片预览遮罩 */
          #image-preview {
            position: fixed; inset: 0; background: rgba(0,0,0,0.92);
            display: none; align-items: center; justify-content: center;
            z-index: 999; padding: 20px;
          }
          #image-preview.show { display: flex; }
          .preview-content { display: flex; flex-direction: column; align-items: center; gap: 16px; max-width: 95vw; max-height: 95vh; }
          .preview-content img {
            max-width: 92vw; max-height: 82vh;
            object-fit: contain; border-radius: 4px;
            user-select: none;
          }
          .preview-actions { display: flex; gap: 12px; }
          .preview-actions a, .preview-actions button {
            padding: 9px 24px; font-size: 14px; border-radius: 6px;
            border: none; cursor: pointer; text-decoration: none;
            font-family: inherit; line-height: 1.2;
          }
          .preview-actions a { background: #07c160; color: #fff; }
          .preview-actions a:hover { background: #06ad56; }
          .preview-actions button { background: #fff; color: #333; }
          .preview-actions button:hover { background: #eee; }
        </style></head><body>
        <div id="header">
          <div class="left">
            <span class="title">__SITE_TITLE__</span>
          </div>
          <div class="right">
            <a id="admin-link" href="/admin" style="display:none;">管理</a>
            <a href="/profile" title="个人中心">
              <span class="my-avatar" id="my-avatar"></span>
            </a>
            <span id="my-nickname"></span>
            <button onclick="doLogout()">退出</button>
          </div>
        </div>
        <div id="chat-box"></div>
        <div id="input-area">
          <input type="text" id="input" placeholder="输入消息..." autocomplete="off">
          <input type="file" id="image-input" accept="image/*" style="display: none;">
          <button id="img-btn">图</button>
          <button id="send-btn">发送</button>
        </div>

        <!-- 图片预览 -->
        <div id="image-preview" onclick="onMaskClick(event)">
          <div class="preview-content">
            <img id="preview-img" src="" alt="">
            <div class="preview-actions">
              <a id="preview-download" href="#" download>下载图片</a>
              <button onclick="closePreview()">关闭</button>
            </div>
          </div>
        </div>

        <script>
        const TOKEN = localStorage.getItem('token');
        const MY_USERNAME = localStorage.getItem('username') || '';
        let MY_NICKNAME = localStorage.getItem('nickname') || MY_USERNAME;
        let MY_AVATAR = localStorage.getItem('avatarId') || null;
        if (MY_AVATAR === 'null') MY_AVATAR = null;
        const IS_ADMIN = localStorage.getItem('isAdmin') === 'true';

        if (!TOKEN) { location.href = '/login'; }

        const chatBox = document.getElementById('chat-box');
        const input = document.getElementById('input');
        const sendBtn = document.getElementById('send-btn');
        const imgBtn = document.getElementById('img-btn');
        const imageInput = document.getElementById('image-input');

        const IMAGE_MAX_WIDTH = __IMAGE_MAX_WIDTH__;
        const IMAGE_QUALITY = __IMAGE_QUALITY__;

        let sending = false;

        // 统一时间格式为 HH:mm（24 小时制）
        function formatTimeNow() {
          const d = new Date();
          const hh = String(d.getHours()).padStart(2, '0');
          const mm = String(d.getMinutes()).padStart(2, '0');
          return hh + ':' + mm;
        }

        document.getElementById('my-nickname').textContent = MY_NICKNAME;
        if (IS_ADMIN) {
          document.getElementById('admin-link').style.display = 'inline-block';
        }
        renderMyAvatar();

        function renderMyAvatar() {
          const el = document.getElementById('my-avatar');
          if (MY_AVATAR) {
            el.innerHTML = `<img src="/image/${MY_AVATAR}">`;
          } else {
            el.textContent = MY_NICKNAME.charAt(0).toUpperCase();
          }
        }

        window.addEventListener('focus', () => {
          MY_NICKNAME = localStorage.getItem('nickname') || MY_USERNAME;
          MY_AVATAR = localStorage.getItem('avatarId');
          if (MY_AVATAR === 'null') MY_AVATAR = null;
          document.getElementById('my-nickname').textContent = MY_NICKNAME;
          renderMyAvatar();
        });

        const es = new EventSource('/events?token=' + encodeURIComponent(TOKEN));
        es.onmessage = (e) => {
          try {
            const data = JSON.parse(e.data);
            // 收到踢出通知
            if (data.type === 'kick') {
              alert(data.reason || '你已被管理员踢出');
              localStorage.clear();
              location.href = '/login';
              return;
            }
            appendMessage(data);
          } catch (err) { console.error('解析消息失败', err); }
        };

        function appendMessage(data) {
          const isSelf = data.username === MY_USERNAME;
          const div = document.createElement('div');
          div.className = 'msg ' + (isSelf ? 'self' : 'other');

          const avatarDiv = document.createElement('div');
          avatarDiv.className = 'avatar';
          if (data.avatarId) {
            avatarDiv.innerHTML = `<img src="/image/${data.avatarId}">`;
          } else {
            avatarDiv.textContent = (data.nickname || data.username).charAt(0).toUpperCase();
          }

          const bodyDiv = document.createElement('div');
          bodyDiv.className = 'body';
          const metaDiv = document.createElement('div');
          metaDiv.className = 'meta';
          metaDiv.textContent = (data.nickname || data.username) + ' · ' + data.time;

          const bubbleDiv = document.createElement('div');
          bubbleDiv.className = 'bubble';
          if (data.type === 'image') {
            const img = document.createElement('img');
            img.src = '/image/' + data.text;
            img.dataset.id = data.text;
            img.loading = 'lazy';
            img.onclick = () => openPreview(img.dataset.id);
            bubbleDiv.appendChild(img);
          } else {
            bubbleDiv.textContent = data.text;
          }

          bodyDiv.appendChild(metaDiv);
          bodyDiv.appendChild(bubbleDiv);
          div.appendChild(avatarDiv);
          div.appendChild(bodyDiv);

          chatBox.appendChild(div);
          chatBox.scrollTop = chatBox.scrollHeight;
        }

        // ---- 图片预览 ----
        function openPreview(id) {
          const mask = document.getElementById('image-preview');
          document.getElementById('preview-img').src = '/image/' + id;
          const dl = document.getElementById('preview-download');
          dl.href = '/image/' + id + '?download=1';
          dl.setAttribute('download', 'image_' + id + '.jpg');
          mask.classList.add('show');
        }

        function closePreview() {
          document.getElementById('image-preview').classList.remove('show');
          document.getElementById('preview-img').src = '';
        }

        function onMaskClick(e) {
          // 只有点到遮罩本身（背景）才关闭
          if (e.target.id === 'image-preview') closePreview();
        }

        document.addEventListener('keydown', (e) => {
          if (e.key === 'Escape') closePreview();
        });

        async function sendText() {
          const text = input.value.trim();
          if (!text || sending) return;
          sending = true;

          const time = formatTimeNow();
          input.value = '';
          input.focus();

          try {
            await fetch('/send?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              body: JSON.stringify({ text, time, type: 'text' })
            });
          } catch(e) {}

          setTimeout(() => { sending = false; }, 150);
        }

        async function sendImage(base64) {
          if (sending) return;
          sending = true;
          imgBtn.disabled = true;
          imgBtn.textContent = '...';

          try {
            const res = await fetch('/image/upload?token=' + encodeURIComponent(TOKEN), { method: 'POST', body: base64 });
            if (!res.ok) {
              alert('图片上传失败：' + res.status);
              return;
            }
            const imageId = (await res.text()).trim();

            const time = formatTimeNow();
            await fetch('/send?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              body: JSON.stringify({ text: imageId, time, type: 'image' })
            });
          } catch(e) {
            alert('发送失败：' + e.message);
          } finally {
            imgBtn.disabled = false;
            imgBtn.textContent = '图';
            sending = false;
          }
        }

        function compressImage(file, maxWidth, callback) {
          const reader = new FileReader();
          reader.onload = (e) => {
            const img = new Image();
            img.onload = () => {
              const canvas = document.createElement('canvas');
              let width = img.width;
              let height = img.height;
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
          fetch('/api/logout?token=' + encodeURIComponent(TOKEN), { method: 'POST' }).finally(() => {
            localStorage.clear();
            location.href = '/login';
          });
        }

        sendBtn.onclick = sendText;
        input.onkeydown = (e) => { if (e.key === 'Enter') sendText(); };

        imgBtn.onclick = () => imageInput.click();
        imageInput.onchange = (e) => {
          const file = e.target.files[0];
          if (!file) return;
          compressImage(file, IMAGE_MAX_WIDTH, (base64) => sendImage(base64));
          e.target.value = '';
        };
        </script></body></html>
        """.replace("__SITE_TITLE__", Config.SITE_TITLE)
            .replace("__IMAGE_MAX_WIDTH__", String.valueOf(Config.IMAGE_MAX_WIDTH))
            .replace("__IMAGE_QUALITY__", String.valueOf(Config.IMAGE_QUALITY));
}