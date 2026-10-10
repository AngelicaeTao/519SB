package ViewLayer;

/** 管理员页面 */
public class AdminPage {

    public static final String HTML = """
        <!doctype html>
        <html lang="zh-CN"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>管理员 - 519SB 聊天室</title>
        <style>
          * { box-sizing: border-box; margin: 0; padding: 0; }
          body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #e5e5e5; min-height: 100vh; }
          #header { background: #fff; padding: 12px 15px; display: flex; align-items: center; justify-content: space-between; box-shadow: 0 1px 3px rgba(0,0,0,0.1); position: sticky; top: 0; z-index: 10; }
          #header h1 { font-size: 16px; color: #333; }
          #header a { font-size: 13px; color: #07c160; text-decoration: none; padding: 6px 12px; border: 1px solid #07c160; border-radius: 6px; margin-left: 8px; }
          #header a:hover { background: #07c160; color: #fff; }
          .container { max-width: 900px; margin: 20px auto; padding: 0 15px; }
          .card { background: #fff; border-radius: 12px; padding: 20px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
          .card h2 { font-size: 15px; color: #333; margin-bottom: 16px; padding-bottom: 10px; border-bottom: 1px solid #eee; }
          table { width: 100%; border-collapse: collapse; font-size: 14px; }
          th, td { padding: 10px 8px; text-align: left; border-bottom: 1px solid #eee; }
          th { color: #666; font-weight: 500; font-size: 13px; }
          .avatar { width: 36px; height: 36px; border-radius: 50%; background: #07c160; color: #fff; display: inline-flex; align-items: center; justify-content: center; font-weight: bold; overflow: hidden; vertical-align: middle; }
          .avatar img { width: 100%; height: 100%; object-fit: cover; }
          .badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 11px; background: #ffe; color: #c90; }
          .badge.admin { background: #e7f5ff; color: #1971c2; }
          .btn { padding: 5px 12px; font-size: 12px; border: none; border-radius: 5px; cursor: pointer; margin-right: 4px; }
          .btn-primary { background: #07c160; color: #fff; }
          .btn-primary:hover { background: #06ad56; }
          .btn-gray { background: #eee; color: #333; }
          .btn-gray:hover { background: #ddd; }
          .btn-red { background: #ff5757; color: #fff; }
          .btn-red:hover { background: #e74c4c; }
          .modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: none; align-items: center; justify-content: center; z-index: 100; padding: 20px; }
          .modal-mask.show { display: flex; }
          .modal { background: #fff; border-radius: 12px; padding: 24px; width: 100%; max-width: 440px; max-height: 90vh; overflow-y: auto; }
          .modal.wide { max-width: 640px; }
          .modal h3 { font-size: 16px; margin-bottom: 18px; color: #333; }
          .form-row { margin-bottom: 14px; }
          .form-row label { display: block; font-size: 13px; color: #666; margin-bottom: 6px; }
          .form-row input[type=text], .form-row input[type=password] { width: 100%; padding: 10px 12px; font-size: 14px; border: 1px solid #ddd; border-radius: 8px; outline: none; }
          .form-row input:focus { border-color: #07c160; }
          .form-row input:disabled { background: #f5f5f5; color: #999; }
          .modal-actions { display: flex; gap: 8px; margin-top: 20px; }
          .modal-actions button { flex: 1; padding: 10px; font-size: 14px; border-radius: 8px; border: none; cursor: pointer; }
          .modal-actions .save { background: #07c160; color: #fff; }
          .modal-actions .cancel { background: #eee; color: #333; }
          .msg { padding: 10px 14px; border-radius: 8px; font-size: 13px; margin-bottom: 14px; display: none; }
          .msg.show { display: block; }
          .msg.error { background: #fee; color: #c33; }
          .msg.success { background: #efe; color: #3a3; }

          /* 消息列表 */
          .msg-toolbar { display: flex; align-items: center; gap: 12px; padding: 8px 4px; border-bottom: 1px solid #eee; margin-bottom: 6px; font-size: 13px; color: #666; }
          .msg-list { max-height: 420px; overflow-y: auto; }
          .msg-item { padding: 8px 4px; border-bottom: 1px solid #f2f2f2; display: flex; align-items: center; gap: 10px; font-size: 13px; }
          .msg-item:hover { background: #fafafa; }
          .msg-item input[type=checkbox] { flex-shrink: 0; cursor: pointer; }
          .msg-item .time { color: #999; flex-shrink: 0; width: 46px; }
          .msg-item .content { flex: 1; word-break: break-all; min-width: 0; }
          .msg-item .thumb {
            max-width: 60px; max-height: 60px; border-radius: 4px;
            object-fit: cover; cursor: zoom-in; vertical-align: middle;
          }
          .msg-item .del-btn {
            flex-shrink: 0; padding: 3px 10px; font-size: 12px;
            background: #ff5757; color: #fff; border: none; border-radius: 5px; cursor: pointer;
          }
          .msg-item .del-btn:hover { background: #e74c4c; }
          .empty-tip { color: #999; text-align: center; padding: 30px; }

          /* 图片预览遮罩 */
          #image-preview {
            position: fixed; inset: 0; background: rgba(0,0,0,0.92);
            display: none; align-items: center; justify-content: center;
            z-index: 999; padding: 20px;
          }
          #image-preview.show { display: flex; }
          .preview-content { display: flex; flex-direction: column; align-items: center; gap: 16px; max-width: 95vw; max-height: 95vh; }
          .preview-content img { max-width: 92vw; max-height: 82vh; object-fit: contain; border-radius: 4px; }
          .preview-actions { display: flex; gap: 12px; }
          .preview-actions a, .preview-actions button {
            padding: 9px 24px; font-size: 14px; border-radius: 6px;
            border: none; cursor: pointer; text-decoration: none;
            font-family: inherit; line-height: 1.2;
          }
          .preview-actions a { background: #07c160; color: #fff; }
          .preview-actions button { background: #fff; color: #333; }
        </style></head><body>
        <div id="header">
          <h1>管理员控制台</h1>
          <div><a href="/chat">返回聊天</a></div>
        </div>
        <div class="container">
          <div class="card">
            <h2>用户列表</h2>
            <div id="list-msg" class="msg"></div>
            <table>
              <thead>
                <tr>
                  <th>头像</th>
                  <th>账号</th>
                  <th>昵称</th>
                  <th>身份</th>
                  <th>注册时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody id="user-list"></tbody>
            </table>
          </div>
        </div>

        <!-- 编辑用户弹窗 -->
        <div class="modal-mask" id="edit-mask">
          <div class="modal">
            <h3>编辑用户</h3>
            <div id="edit-msg" class="msg"></div>
            <div class="form-row">
              <label>账号（不可修改）</label>
              <input type="text" id="edit-username" disabled>
            </div>
            <div class="form-row">
              <label>注册时间</label>
              <input type="text" id="edit-created" disabled>
            </div>
            <div class="form-row">
              <label>昵称</label>
              <input type="text" id="edit-nickname" maxlength="20">
            </div>
            <div class="form-row">
              <label>新密码（留空不修改）</label>
              <input type="password" id="edit-password" placeholder="留空则不修改密码">
            </div>
            <div class="form-row">
              <label>头像</label>
              <div style="display:flex; align-items:center; gap:12px;">
                <div class="avatar" id="edit-avatar" style="width:48px;height:48px;font-size:20px;"></div>
                <button class="btn btn-gray" onclick="document.getElementById('edit-avatar-input').click()">更换头像</button>
              </div>
              <input type="file" id="edit-avatar-input" accept="image/*" style="display: none;">
            </div>
            <div class="form-row">
              <label><input type="checkbox" id="edit-isadmin" style="margin-right:6px;">设为管理员</label>
            </div>
            <div class="modal-actions">
              <button class="cancel" onclick="closeEdit()">取消</button>
              <button class="save" onclick="saveEdit()">保存</button>
            </div>
          </div>
        </div>

        <!-- 查看用户消息弹窗 -->
        <div class="modal-mask" id="msg-mask">
          <div class="modal wide">
            <h3>用户消息：<span id="msg-user"></span></h3>
            <div id="msg-list-msg" class="msg"></div>
            <div class="msg-toolbar">
              <label><input type="checkbox" id="select-all" onchange="toggleSelectAll(this.checked)"> 全选</label>
              <button class="btn btn-red" onclick="deleteSelected()">删除选中</button>
              <span id="selected-count" style="margin-left:auto;color:#999;"></span>
            </div>
            <div class="msg-list" id="msg-list"></div>
            <div class="modal-actions">
              <button class="cancel" onclick="closeMsg()">关闭</button>
            </div>
          </div>
        </div>

        <!-- 图片预览 -->
        <div id="image-preview" onclick="onPreviewMaskClick(event)">
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
        const IS_ADMIN = localStorage.getItem('isAdmin') === 'true';
        if (!TOKEN) { location.href = '/login'; }
        if (!IS_ADMIN) { location.href = '/chat'; }

        let editTarget = null;
        let editAvatarId = null;
        let currentMsgUser = null;

        function showMsg(id, text, type) {
          const el = document.getElementById(id);
          el.textContent = text;
          el.className = 'msg show ' + type;
          if (type === 'success') setTimeout(() => { el.className = 'msg'; }, 2500);
        }

        async function loadUsers() {
          try {
            const res = await fetch('/api/admin/users?token=' + encodeURIComponent(TOKEN));
            const r = await res.json();
            if (!r.ok) { showMsg('list-msg', r.msg || '加载失败', 'error'); return; }
            renderUsers(r.users);
          } catch (e) { showMsg('list-msg', '网络错误：' + e.message, 'error'); }
        }

        function renderUsers(users) {
          const tbody = document.getElementById('user-list');
          tbody.innerHTML = '';
          users.forEach(u => {
            const tr = document.createElement('tr');

            const tdAvatar = document.createElement('td');
            const avatarDiv = document.createElement('div');
            avatarDiv.className = 'avatar';
            if (u.avatarId) avatarDiv.innerHTML = `<img src="/image/${u.avatarId}">`;
            else avatarDiv.textContent = (u.nickname || u.username).charAt(0).toUpperCase();
            tdAvatar.appendChild(avatarDiv);

            const tdUsername = document.createElement('td');
            tdUsername.textContent = u.username;

            const tdNickname = document.createElement('td');
            tdNickname.textContent = u.nickname;

            const tdRole = document.createElement('td');
            tdRole.innerHTML = u.isAdmin
              ? '<span class="badge admin">管理员</span>'
              : '<span class="badge">普通用户</span>';

            const tdTime = document.createElement('td');
            tdTime.textContent = formatTime(u.createdAt);

            const tdActions = document.createElement('td');
            const btnEdit = document.createElement('button');
            btnEdit.className = 'btn btn-primary';
            btnEdit.textContent = '编辑';
            btnEdit.onclick = () => openEdit(u);

            const btnMsg = document.createElement('button');
            btnMsg.className = 'btn btn-gray';
            btnMsg.textContent = '消息';
            btnMsg.onclick = () => openMessages(u);

            const btnDel = document.createElement('button');
            btnDel.className = 'btn btn-red';
            btnDel.textContent = '删除';
            btnDel.onclick = () => deleteUser(u);

            tdActions.appendChild(btnEdit);
            tdActions.appendChild(btnMsg);
            tdActions.appendChild(btnDel);

            tr.appendChild(tdAvatar);
            tr.appendChild(tdUsername);
            tr.appendChild(tdNickname);
            tr.appendChild(tdRole);
            tr.appendChild(tdTime);
            tr.appendChild(tdActions);
            tbody.appendChild(tr);
          });
        }

        function formatTime(ms) {
          const d = new Date(ms);
          return d.getFullYear() + '-' +
                 String(d.getMonth() + 1).padStart(2, '0') + '-' +
                 String(d.getDate()).padStart(2, '0') + ' ' +
                 String(d.getHours()).padStart(2, '0') + ':' +
                 String(d.getMinutes()).padStart(2, '0');
        }

        // ---------- 编辑用户 ----------
        function openEdit(u) {
          editTarget = u;
          editAvatarId = u.avatarId;
          document.getElementById('edit-username').value = u.username;
          document.getElementById('edit-created').value = formatTime(u.createdAt);
          document.getElementById('edit-nickname').value = u.nickname;
          document.getElementById('edit-password').value = '';
          document.getElementById('edit-isadmin').checked = u.isAdmin;
          renderEditAvatar();
          document.getElementById('edit-msg').className = 'msg';
          document.getElementById('edit-mask').classList.add('show');
        }

        function renderEditAvatar() {
          const el = document.getElementById('edit-avatar');
          if (editAvatarId) el.innerHTML = `<img src="/image/${editAvatarId}">`;
          else {
            const nick = document.getElementById('edit-nickname').value || editTarget.username;
            el.textContent = nick.charAt(0).toUpperCase();
          }
        }

        function closeEdit() {
          document.getElementById('edit-mask').classList.remove('show');
          editTarget = null;
          editAvatarId = null;
        }

        document.getElementById('edit-avatar-input').onchange = (e) => {
          const file = e.target.files[0];
          if (!file) return;
          compressImage(file, 200, async (base64) => {
            try {
              const res = await fetch('/image/upload?token=' + encodeURIComponent(TOKEN), { method: 'POST', body: base64 });
              if (!res.ok) { showMsg('edit-msg', '头像上传失败：' + res.status, 'error'); return; }
              editAvatarId = (await res.text()).trim();
              renderEditAvatar();
            } catch (err) { showMsg('edit-msg', '上传失败：' + err.message, 'error'); }
          });
          e.target.value = '';
        };

        async function saveEdit() {
          if (!editTarget) return;
          const nickname = document.getElementById('edit-nickname').value.trim();
          const newPassword = document.getElementById('edit-password').value;
          const isAdmin = document.getElementById('edit-isadmin').checked;

          if (!nickname) { showMsg('edit-msg', '昵称不能为空', 'error'); return; }

          try {
            const res = await fetch('/api/admin/updateUser?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                username: editTarget.username,
                nickname, newPassword,
                avatarId: editAvatarId,
                isAdmin
              })
            });
            const r = await res.json();
            if (r.ok) { closeEdit(); loadUsers(); }
            else showMsg('edit-msg', r.msg || '保存失败', 'error');
          } catch (e) { showMsg('edit-msg', '网络错误：' + e.message, 'error'); }
        }

        // ---------- 删除用户 ----------
        async function deleteUser(u) {
          if (!confirm(`确定删除用户「${u.nickname}（${u.username}）」？\n该操作会踢出在线用户并删除其所有消息，不可恢复。`)) return;
          try {
            const res = await fetch('/api/admin/deleteUser?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ username: u.username })
            });
            const r = await res.json();
            if (r.ok) { showMsg('list-msg', '用户已删除', 'success'); loadUsers(); }
            else showMsg('list-msg', r.msg || '删除失败', 'error');
          } catch (e) { showMsg('list-msg', '网络错误：' + e.message, 'error'); }
        }

        // ---------- 查看消息 ----------
        async function openMessages(u) {
          currentMsgUser = u;
          document.getElementById('msg-user').textContent = u.nickname + '（' + u.username + '）';
          document.getElementById('select-all').checked = false;
          document.getElementById('msg-list').innerHTML = '<p class="empty-tip">加载中...</p>';
          document.getElementById('msg-list-msg').className = 'msg';
          updateSelectedCount();
          document.getElementById('msg-mask').classList.add('show');

          try {
            const res = await fetch('/api/admin/userMessages?token=' + encodeURIComponent(TOKEN) + '&username=' + encodeURIComponent(u.username));
            const r = await res.json();
            if (!r.ok) {
              document.getElementById('msg-list').innerHTML = `<p class="empty-tip" style="color:#c33;">${r.msg || '加载失败'}</p>`;
              return;
            }
            renderMessages(r.messages);
          } catch (e) {
            document.getElementById('msg-list').innerHTML = `<p class="empty-tip" style="color:#c33;">网络错误：${e.message}</p>`;
          }
        }

        function renderMessages(messages) {
          const list = document.getElementById('msg-list');
          if (!messages || messages.length === 0) {
            list.innerHTML = '<p class="empty-tip">暂无消息</p>';
            return;
          }
          list.innerHTML = '';
          messages.forEach(m => {
            const div = document.createElement('div');
            div.className = 'msg-item';

            const cb = document.createElement('input');
            cb.type = 'checkbox';
            cb.className = 'msg-check';
            cb.dataset.id = m.id;
            cb.onchange = updateSelectedCount;

            const time = document.createElement('span');
            time.className = 'time';
            time.textContent = m.time;

            const content = document.createElement('div');
            content.className = 'content';
            if (m.type === 'image') {
              const img = document.createElement('img');
              img.className = 'thumb';
              img.src = '/image/' + m.text;
              img.loading = 'lazy';
              img.onclick = () => openPreview(m.text);
              content.appendChild(img);
              const tag = document.createElement('span');
              tag.style.marginLeft = '8px';
              tag.style.color = '#999';
              tag.textContent = '[图片]';
              content.appendChild(tag);
            } else {
              content.textContent = m.text;
            }

            const del = document.createElement('button');
            del.className = 'del-btn';
            del.textContent = '删除';
            del.onclick = () => deleteOneMessage(m.id, div);

            div.appendChild(cb);
            div.appendChild(time);
            div.appendChild(content);
            div.appendChild(del);
            list.appendChild(div);
          });
          updateSelectedCount();
        }

        function toggleSelectAll(checked) {
          document.querySelectorAll('.msg-check').forEach(cb => cb.checked = checked);
          updateSelectedCount();
        }

        function updateSelectedCount() {
          const n = document.querySelectorAll('.msg-check:checked').length;
          document.getElementById('selected-count').textContent = n > 0 ? `已选 ${n} 条` : '';
        }

        async function deleteOneMessage(id, rowEl) {
          if (!confirm('确定删除这条消息？')) return;
          try {
            const res = await fetch('/api/admin/deleteMessage?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ id })
            });
            const r = await res.json();
            if (r.ok) {
              rowEl.remove();
              updateSelectedCount();
              const list = document.getElementById('msg-list');
              if (!list.querySelector('.msg-item')) {
                list.innerHTML = '<p class="empty-tip">暂无消息</p>';
              }
            } else {
              showMsg('msg-list-msg', r.msg || '删除失败', 'error');
            }
          } catch (e) { showMsg('msg-list-msg', '网络错误：' + e.message, 'error'); }
        }

        async function deleteSelected() {
          const ids = [...document.querySelectorAll('.msg-check:checked')].map(cb => parseInt(cb.dataset.id));
          if (ids.length === 0) { showMsg('msg-list-msg', '请先勾选要删除的消息', 'error'); return; }
          if (!confirm(`确定删除选中的 ${ids.length} 条消息？`)) return;

          try {
            const res = await fetch('/api/admin/deleteMessages?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ ids })
            });
            const r = await res.json();
            if (r.ok) {
              showMsg('msg-list-msg', r.msg || '已删除', 'success');
              ids.forEach(id => {
                const cb = document.querySelector(`.msg-check[data-id="${id}"]`);
                if (cb) cb.closest('.msg-item').remove();
              });
              document.getElementById('select-all').checked = false;
              updateSelectedCount();
              const list = document.getElementById('msg-list');
              if (!list.querySelector('.msg-item')) {
                list.innerHTML = '<p class="empty-tip">暂无消息</p>';
              }
            } else {
              showMsg('msg-list-msg', r.msg || '删除失败', 'error');
            }
          } catch (e) { showMsg('msg-list-msg', '网络错误：' + e.message, 'error'); }
        }

        function closeMsg() {
          document.getElementById('msg-mask').classList.remove('show');
          currentMsgUser = null;
        }

        // ---------- 图片预览 ----------
        function openPreview(id) {
          document.getElementById('preview-img').src = '/image/' + id;
          const dl = document.getElementById('preview-download');
          dl.href = '/image/' + id + '?download=1';
          dl.setAttribute('download', 'image_' + id + '.jpg');
          document.getElementById('image-preview').classList.add('show');
        }
        function closePreview() {
          document.getElementById('image-preview').classList.remove('show');
          document.getElementById('preview-img').src = '';
        }
        function onPreviewMaskClick(e) {
          if (e.target.id === 'image-preview') closePreview();
        }
        document.addEventListener('keydown', (e) => {
          if (e.key === 'Escape') { closePreview(); closeMsg(); closeEdit(); }
        });

        // ---------- 工具 ----------
        function compressImage(file, maxWidth, callback) {
          const reader = new FileReader();
          reader.onload = (e) => {
            const img = new Image();
            img.onload = () => {
              const canvas = document.createElement('canvas');
              let width = img.width, height = img.height;
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

        loadUsers();
        </script></body></html>
        """;
}