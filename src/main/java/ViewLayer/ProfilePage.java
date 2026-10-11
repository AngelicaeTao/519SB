package ViewLayer;

/** 个人中心页面 */
public class ProfilePage {

    public static final String HTML = """
        <!doctype html>
        <html lang="zh-CN"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>个人中心 - 519SB 聊天室</title>
        <style>
          * { box-sizing: border-box; margin: 0; padding: 0; }
          body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #e5e5e5; height: 100vh; overflow-y: auto; }
          #header { background: #fff; padding: 12px 15px; display: flex; align-items: center; justify-content: space-between; box-shadow: 0 1px 3px rgba(0,0,0,0.1); position: sticky; top: 0; z-index: 10; }
          #header h1 { font-size: 16px; color: #333; }
          #header a { font-size: 13px; color: #07c160; text-decoration: none; padding: 6px 12px; border: 1px solid #07c160; border-radius: 6px; }
          #header a:hover { background: #07c160; color: #fff; }
          .container { max-width: 480px; margin: 20px auto; padding: 0 15px; }
          .card { background: #fff; border-radius: 12px; padding: 24px 20px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
          .card h2 { font-size: 15px; color: #333; margin-bottom: 16px; padding-bottom: 10px; border-bottom: 1px solid #eee; }
          .avatar-section { text-align: center; margin-bottom: 20px; }
          .avatar-preview {
            width: 100px; height: 100px; border-radius: 50%; background: #07c160;
            color: #fff; font-size: 40px; font-weight: bold; display: inline-flex;
            align-items: center; justify-content: center; overflow: hidden;
            cursor: pointer; transition: opacity 0.2s;
          }
          .avatar-preview:hover { opacity: 0.85; }
          .avatar-preview img { width: 100%; height: 100%; object-fit: cover; }
          .avatar-tip { font-size: 12px; color: #999; margin-top: 8px; }
          .form-row { margin-bottom: 14px; }
          .form-row label { display: block; font-size: 13px; color: #666; margin-bottom: 6px; }
          .form-row input {
            width: 100%; padding: 10px 12px; font-size: 15px;
            border: 1px solid #ddd; border-radius: 8px; outline: none;
          }
          .form-row input:focus { border-color: #07c160; }
          .form-row input:disabled { background: #f5f5f5; color: #999; cursor: not-allowed; }
          button {
            width: 100%; padding: 11px; font-size: 15px; font-weight: 500;
            background: #07c160; color: #fff; border: none; border-radius: 8px;
            cursor: pointer;
          }
          button:hover { background: #06ad56; }
          button:disabled { background: #ccc; cursor: not-allowed; }
          .msg { padding: 10px 14px; border-radius: 8px; font-size: 13px; margin-bottom: 14px; display: none; }
          .msg.show { display: block; }
          .msg.error { background: #fee; color: #c33; }
          .msg.success { background: #efe; color: #3a3; }
          .msg.warn { background: #fff7e6; color: #d48806; border: 1px solid #ffe58f; }
        </style></head><body>
        <div id="header">
          <h1>个人中心</h1>
          <a href="/chat">返回聊天</a>
        </div>
        <div class="container">

          <div class="card">
            <h2>头像和昵称</h2>
            <div class="avatar-section">
              <div class="avatar-preview" id="avatar-preview" onclick="document.getElementById('avatar-input').click()"></div>
              <p class="avatar-tip">点击头像上传新头像</p>
              <input type="file" id="avatar-input" accept="image/*" style="display: none;">
            </div>

            <div id="profile-msg" class="msg"></div>

            <div class="form-row">
              <label>账号（不可修改）</label>
              <input type="text" id="username" disabled>
            </div>
            <div class="form-row">
              <label>昵称</label>
              <input type="text" id="nickname" placeholder="请输入昵称" maxlength="20">
            </div>
            <button id="save-btn" onclick="saveProfile()">保存修改</button>
          </div>

          <div class="card">
            <h2>修改密码</h2>
            <div id="pwd-msg" class="msg"></div>
            <div class="form-row">
              <label>旧密码</label>
              <input type="password" id="old-password" placeholder="请输入旧密码" autocomplete="current-password">
            </div>
            <div class="form-row">
              <label>新密码</label>
              <input type="password" id="new-password" placeholder="6~50 位，含字母和数字" autocomplete="new-password">
            </div>
            <div class="form-row">
              <label>确认新密码</label>
              <input type="password" id="new-password2" placeholder="再次输入新密码" autocomplete="new-password">
            </div>
            <button onclick="changePassword()">修改密码</button>
          </div>

        </div>
        <script>
        const TOKEN = localStorage.getItem('token');
        const USERNAME = localStorage.getItem('username') || '';
        if (!TOKEN) { location.href = '/login'; }

        let currentAvatarId = localStorage.getItem('avatarId') || null;
        if (currentAvatarId === 'null') currentAvatarId = null;
        let currentNickname = localStorage.getItem('nickname') || USERNAME;

        let isSaved = false;

        document.getElementById('username').value = USERNAME;
        document.getElementById('nickname').value = currentNickname;

        function renderAvatar() {
          const el = document.getElementById('avatar-preview');
          el.innerHTML = '';
          if (currentAvatarId) {
            const img = document.createElement('img');
            img.src = '/image/' + currentAvatarId;
            el.appendChild(img);
          } else {
            el.textContent = (currentNickname || USERNAME).charAt(0).toUpperCase();
          }
        }
        renderAvatar();

        function showMsg(id, text, type) {
          const el = document.getElementById(id);
          el.textContent = text;
          el.className = 'msg show ' + type;
        }
        function hideMsg(id) {
          document.getElementById(id).className = 'msg';
        }

        function markDirty() {
          if (isSaved) {
            isSaved = false;
            const btn = document.getElementById('save-btn');
            btn.textContent = '保存修改';
            btn.disabled = false;
          }
        }
        function markSaved() {
          isSaved = true;
          const btn = document.getElementById('save-btn');
          btn.textContent = '已保存';
          btn.disabled = true;
        }

        document.getElementById('nickname').addEventListener('input', () => {
          markDirty();
          hideMsg('profile-msg');
        });

        document.getElementById('avatar-input').onchange = async (e) => {
          const file = e.target.files[0];
          if (!file) return;
          compressImage(file, 200, async (base64) => {
            try {
              const res = await fetch('/image/upload?token=' + encodeURIComponent(TOKEN) + '&type=avatar', {
                method: 'POST',
                body: base64
              });
              if (!res.ok) {
                showMsg('profile-msg', '头像上传失败：' + res.status, 'error');
                return;
              }
              currentAvatarId = (await res.text()).trim();
              renderAvatar();
              markDirty();
              showMsg('profile-msg', '头像已上传，记得点下方"保存修改"', 'warn');
            } catch (err) {
              showMsg('profile-msg', '上传失败：' + err.message, 'error');
            }
          });
          e.target.value = '';
        };

        async function saveProfile() {
          if (isSaved) return;
          const nickname = document.getElementById('nickname').value.trim();
          if (!nickname) { showMsg('profile-msg', '昵称不能为空', 'error'); return; }
          try {
            const res = await fetch('/api/profile/update?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ nickname, avatarId: currentAvatarId })
            });
            const r = await res.json();
            if (r.ok) {
              currentNickname = r.nickname;
              currentAvatarId = r.avatarId;
              localStorage.setItem('nickname', r.nickname);
              localStorage.setItem('avatarId', r.avatarId == null ? 'null' : r.avatarId);
              renderAvatar();
              showMsg('profile-msg', '资料已保存', 'success');
              markSaved();
            } else {
              showMsg('profile-msg', r.msg || '保存失败', 'error');
            }
          } catch (e) {
            showMsg('profile-msg', '网络错误：' + e.message, 'error');
          }
        }

        async function changePassword() {
          const oldPassword = document.getElementById('old-password').value;
          const p1 = document.getElementById('new-password').value;
          const p2 = document.getElementById('new-password2').value;
          if (!oldPassword) { showMsg('pwd-msg', '请输入旧密码', 'error'); return; }
          if (!p1) { showMsg('pwd-msg', '请输入新密码', 'error'); return; }
          if (p1 !== p2) { showMsg('pwd-msg', '两次输入的密码不一致', 'error'); return; }
          try {
            const res = await fetch('/api/profile/changePassword?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ oldPassword, newPassword: p1 })
            });
            const r = await res.json();
            if (r.ok) {
              document.getElementById('old-password').value = '';
              document.getElementById('new-password').value = '';
              document.getElementById('new-password2').value = '';
              if (r.token) {
                // 服务端已吊销全部旧会话并签发了新 token，存下后重载页面。
                // 重载会带着新 token 重建 SSE 长连接，避免还挂着已失效的旧连接。
                localStorage.setItem('token', r.token);
                showMsg('pwd-msg', r.msg || '密码修改成功', 'success');
                setTimeout(() => location.reload(), 800);
              } else {
                // 服务端没能签发新 token，只能要求重新登录
                showMsg('pwd-msg', r.msg || '密码修改成功，请重新登录', 'success');
                setTimeout(() => { localStorage.clear(); location.href = '/login'; }, 1200);
              }
            } else {
              showMsg('pwd-msg', r.msg || '修改失败', 'error');
            }
          } catch (e) {
            showMsg('pwd-msg', '网络错误：' + e.message, 'error');
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
              callback(canvas.toDataURL('image/jpeg', 0.85));
            };
            img.src = e.target.result;
          };
          reader.readAsDataURL(file);
        }
        </script></body></html>
        """;
}