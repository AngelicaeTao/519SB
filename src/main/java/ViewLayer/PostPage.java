package ViewLayer;

/** 帖子页面：列表 + 详情 */
public class PostPage {

    // ---------------- 列表页 ----------------
    public static final String LIST_HTML = """
        <!doctype html>
        <html lang="zh-CN"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>帖子 - 519SB</title>
        <style>
          * { box-sizing: border-box; margin: 0; padding: 0; }
          body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f5f5f5; min-height: 100vh; }
          #header { background: #fff; padding: 10px 15px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); z-index: 10; display: flex; align-items: center; justify-content: space-between; position: sticky; top: 0; }
          #header .title { font-size: 15px; font-weight: bold; }
          #header .right { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #666; }
          #header .right a, #header .right button { font-size: 12px; padding: 5px 10px; border: 1px solid #ddd; background: #fff; border-radius: 6px; cursor: pointer; color: #666; text-decoration: none; font-family: inherit; }
          #header .right a:hover, #header .right button:hover { background: #f5f5f5; }
          #header .right a.primary { border-color: #07c160; color: #07c160; }
          #header .right button.primary { background: #07c160; color: #fff; border-color: #07c160; }
          #header .right button.primary:hover { background: #06ad56; }
          #header .right button.online-btn { border-color: #07c160; color: #07c160; }
          #header .right button.online-btn.active { background: #07c160; color: #fff; }
          .my-avatar { width: 28px; height: 28px; border-radius: 50%; background: #07c160; color: #fff; display: inline-flex; align-items: center; justify-content: center; font-weight: bold; font-size: 13px; overflow: hidden; cursor: pointer; text-decoration: none; }
          .my-avatar img { width: 100%; height: 100%; object-fit: cover; }

          .container { max-width: 720px; margin: 20px auto; padding: 0 15px; }
          .post-card {
            background: #fff; border-radius: 12px; padding: 18px 20px;
            margin-bottom: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.06);
            cursor: pointer; transition: transform 0.15s, box-shadow 0.15s;
          }
          .post-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
          .post-card h2 { font-size: 17px; color: #222; margin-bottom: 8px; }
          .post-card .excerpt { font-size: 14px; color: #666; line-height: 1.6; margin-bottom: 10px; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
          .post-card .meta { font-size: 12px; color: #999; display: flex; gap: 12px; }
          .empty { text-align: center; color: #999; padding: 40px; }

          /* 在线用户抽屉 */
          #sidebar {
            position: fixed;
            top: 0;
            right: 0;
            bottom: 0;
            width: 220px;
            background: #fff;
            border-left: 1px solid #e5e5e5;
            overflow-y: auto;
            padding: 12px 10px;
            font-size: 13px;
            box-shadow: -2px 0 8px rgba(0,0,0,0.08);
            transform: translateX(100%);
            transition: transform 0.25s ease;
            z-index: 200;
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

          #sidebar-mask {
            position: fixed;
            inset: 0;
            background: rgba(0,0,0,0.15);
            display: none;
            z-index: 150;
          }
          #sidebar-mask.show { display: block; }

          .mask { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: none; align-items: center; justify-content: center; z-index: 300; padding: 20px; }
          .mask.show { display: flex; }
          .modal { background: #fff; border-radius: 12px; padding: 24px; width: 100%; max-width: 560px; max-height: 90vh; overflow-y: auto; }
          .modal h3 { font-size: 16px; margin-bottom: 16px; color: #333; }
          .modal input, .modal textarea {
            width: 100%; padding: 10px 12px; font-size: 14px; font-family: inherit;
            border: 1px solid #ddd; border-radius: 8px; outline: none; margin-bottom: 12px;
          }
          .modal input:focus, .modal textarea:focus { border-color: #07c160; }
          .modal textarea { min-height: 200px; resize: vertical; line-height: 1.6; }
          .modal-actions { display: flex; gap: 8px; margin-top: 8px; }
          .modal-actions button { flex: 1; padding: 10px; font-size: 14px; border-radius: 8px; border: none; cursor: pointer; font-family: inherit; }
          .modal-actions .save { background: #07c160; color: #fff; }
          .modal-actions .cancel { background: #eee; color: #333; }
          .msg { padding: 8px 12px; border-radius: 6px; font-size: 13px; margin-bottom: 12px; display: none; }
          .msg.show { display: block; }
          .msg.error { background: #fee; color: #c33; }
        </style></head><body>
        <div id="header">
          <span class="title">519SB 帖子</span>
          <div class="right">
            <a href="/chat" class="primary">聊天室</a>
            <button id="new-btn" class="primary">发帖</button>
            <button id="online-btn" class="online-btn">在线 <span id="online-count">0</span></button>
            <a id="admin-link" href="/admin" style="display:none;">管理</a>
            <a href="/profile" title="个人中心"><span class="my-avatar" id="my-avatar"></span></a>
            <span id="my-nickname"></span>
            <button onclick="doLogout()">退出</button>
          </div>
        </div>
        <div class="container" id="list"></div>

        <div id="sidebar-mask" onclick="toggleSidebar(false)"></div>
        <div id="sidebar">
          <h3>
            <span>在线用户</span>
            <button class="close-btn" onclick="toggleSidebar(false)" title="关闭">×</button>
          </h3>
          <div id="online-list"></div>
        </div>

        <div class="mask" id="edit-mask">
          <div class="modal">
            <h3>发新帖</h3>
            <div id="edit-msg" class="msg"></div>
            <input type="text" id="edit-title" placeholder="标题" maxlength="100">
            <textarea id="edit-content" placeholder="正文内容..."></textarea>
            <div class="modal-actions">
              <button class="cancel" onclick="closeEdit()">取消</button>
              <button class="save" onclick="savePost()">发布</button>
            </div>
          </div>
        </div>

        <script>
        const TOKEN = localStorage.getItem('token');
        const MY_NICKNAME = localStorage.getItem('nickname') || localStorage.getItem('username') || '';
        const MY_AVATAR = localStorage.getItem('avatarId') || null;
        const IS_ADMIN = localStorage.getItem('isAdmin') === 'true';
        if (!TOKEN) { location.href = '/login'; }

        var ONLINE_REFRESH_INTERVAL = 10000;
        var lastOnlineSignature = '';
        var sidebar = document.getElementById('sidebar');
        var sidebarMask = document.getElementById('sidebar-mask');
        var onlineBtn = document.getElementById('online-btn');

        // 顶部信息
        document.getElementById('my-nickname').textContent = MY_NICKNAME;
        if (IS_ADMIN) {
          var adminLink = document.getElementById('admin-link');
          adminLink.style.display = 'inline-block';
          adminLink.href = '/admin?token=' + encodeURIComponent(TOKEN);
        }

        function renderMyAvatar() {
          const el = document.getElementById('my-avatar');
          el.innerHTML = '';
          if (MY_AVATAR && MY_AVATAR !== 'null') {
            const img = document.createElement('img');
            img.src = '/image/' + MY_AVATAR;
            el.appendChild(img);
          } else {
            el.textContent = MY_NICKNAME.charAt(0).toUpperCase();
          }
        }
        renderMyAvatar();

        function doLogout() {
          if (!confirm('确定退出登录？')) return;
          fetch('/api/logout?token=' + encodeURIComponent(TOKEN), { method: 'POST' }).finally(() => {
            localStorage.clear();
            location.href = '/login';
          });
        }

        // ==================== 侧边栏 ====================
        function toggleSidebar(show) {
          if (typeof show === 'undefined') show = !sidebar.classList.contains('show');
          if (show) {
            sidebar.classList.add('show');
            sidebarMask.classList.add('show');
            onlineBtn.classList.add('active');
            loadOnlineUsers();
          } else {
            sidebar.classList.remove('show');
            sidebarMask.classList.remove('show');
            onlineBtn.classList.remove('active');
          }
        }
        onlineBtn.onclick = function () { toggleSidebar(); };

        async function loadOnlineUsers() {
          try {
            var res = await fetch('/online?token=' + encodeURIComponent(TOKEN));
            var r = await res.json();
            if (!r.ok) return;
            renderOnlineUsers(r.users);
          } catch (e) {}
        }

        function renderOnlineUsers(users) {
          document.getElementById('online-count').textContent = users.length;

          var sig = users.map(function (u) { return u.username + ':' + (u.avatarId || ''); }).join('|');
          if (sig === lastOnlineSignature) return;
          lastOnlineSignature = sig;

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

        loadOnlineUsers();
        setInterval(loadOnlineUsers, ONLINE_REFRESH_INTERVAL);

        // ==================== 帖子列表 ====================
        function fmtTime(ms) {
          const d = new Date(ms);
          return d.getFullYear() + '-' +
                 String(d.getMonth() + 1).padStart(2, '0') + '-' +
                 String(d.getDate()).padStart(2, '0') + ' ' +
                 String(d.getHours()).padStart(2, '0') + ':' +
                 String(d.getMinutes()).padStart(2, '0');
        }

        async function loadList() {
          const box = document.getElementById('list');
          box.innerHTML = '<div class="empty">加载中...</div>';
          try {
            const res = await fetch('/api/posts');
            const r = await res.json();
            if (!r.ok) { box.innerHTML = '<div class="empty">加载失败</div>'; return; }
            renderList(r.posts);
          } catch (e) {
            box.innerHTML = '<div class="empty">网络错误：' + e.message + '</div>';
          }
        }

        function renderList(posts) {
          const box = document.getElementById('list');
          box.innerHTML = '';
          if (!posts || posts.length === 0) {
            box.innerHTML = '<div class="empty">还没有帖子，点右上角发一篇吧~</div>';
            return;
          }
          posts.forEach(p => {
            const card = document.createElement('div');
            card.className = 'post-card';

            const h2 = document.createElement('h2');
            h2.textContent = p.title;

            const ex = document.createElement('div');
            ex.className = 'excerpt';
            ex.textContent = p.content.length > 100 ? p.content.substring(0, 100) + '...' : p.content;

            const meta = document.createElement('div');
            meta.className = 'meta';
            const a = document.createElement('span');
            a.textContent = 'by ' + (p.authorNickname || p.author);
            const t = document.createElement('span'); t.textContent = fmtTime(p.createdAt);
            const v = document.createElement('span'); v.textContent = p.views + ' 阅读';
            meta.appendChild(a); meta.appendChild(t); meta.appendChild(v);

            card.appendChild(h2);
            card.appendChild(ex);
            card.appendChild(meta);
            card.onclick = () => location.href = '/posts/' + p.id;
            box.appendChild(card);
          });
        }

        function openEdit() {
          document.getElementById('edit-title').value = '';
          document.getElementById('edit-content').value = '';
          document.getElementById('edit-msg').className = 'msg';
          document.getElementById('edit-mask').classList.add('show');
          document.getElementById('edit-title').focus();
        }
        function closeEdit() {
          document.getElementById('edit-mask').classList.remove('show');
        }
        function showEditMsg(text) {
          const el = document.getElementById('edit-msg');
          el.textContent = text;
          el.className = 'msg show error';
        }

        async function savePost() {
          const title = document.getElementById('edit-title').value.trim();
          const content = document.getElementById('edit-content').value;
          if (!title) { showEditMsg('标题不能为空'); return; }
          if (!content.trim()) { showEditMsg('内容不能为空'); return; }
          try {
            const res = await fetch('/api/posts?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ title, content })
            });
            const r = await res.json();
            if (r.ok) {
              closeEdit();
              location.href = '/posts/' + r.id;
            } else {
              showEditMsg(r.msg || '发帖失败');
            }
          } catch (e) {
            showEditMsg('网络错误：' + e.message);
          }
        }

        document.addEventListener('keydown', function (e) {
          if (e.key === 'Escape') toggleSidebar(false);
        });

        document.getElementById('new-btn').onclick = openEdit;
        loadList();
        </script></body></html>
        """;

    // ---------------- 详情页 ----------------
    public static String detailHtml(long id) {
        return DETAIL_HTML.replace("__POST_ID__", String.valueOf(id));
    }

    private static final String DETAIL_HTML = """
        <!doctype html>
        <html lang="zh-CN"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>帖子 - 519SB</title>
        <style>
          * { box-sizing: border-box; margin: 0; padding: 0; }
          body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f5f5f5; min-height: 100vh; }
          #header { background: #fff; padding: 10px 15px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); z-index: 10; display: flex; align-items: center; justify-content: space-between; position: sticky; top: 0; }
          #header .title { font-size: 15px; font-weight: bold; }
          #header .right { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #666; }
          #header .right a, #header .right button { font-size: 12px; padding: 5px 10px; border: 1px solid #ddd; background: #fff; border-radius: 6px; cursor: pointer; color: #666; text-decoration: none; font-family: inherit; }
          #header .right a:hover, #header .right button:hover { background: #f5f5f5; }
          #header .right a.primary { border-color: #07c160; color: #07c160; }
          #header .right button.online-btn { border-color: #07c160; color: #07c160; }
          #header .right button.online-btn.active { background: #07c160; color: #fff; }
          .my-avatar { width: 28px; height: 28px; border-radius: 50%; background: #07c160; color: #fff; display: inline-flex; align-items: center; justify-content: center; font-weight: bold; font-size: 13px; overflow: hidden; cursor: pointer; text-decoration: none; }
          .my-avatar img { width: 100%; height: 100%; object-fit: cover; }

          .container { max-width: 720px; margin: 20px auto; padding: 0 15px; }
          .post { background: #fff; border-radius: 12px; padding: 28px 32px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
          .post h1 { font-size: 24px; color: #222; line-height: 1.4; margin-bottom: 12px; }
          .post .meta { font-size: 13px; color: #999; margin-bottom: 24px; padding-bottom: 16px; border-bottom: 1px solid #f0f0f0; }
          .post .content { font-size: 15px; line-height: 1.8; color: #333; white-space: pre-wrap; word-wrap: break-word; }
          .actions { display: flex; gap: 8px; margin-top: 28px; padding-top: 20px; border-top: 1px solid #f0f0f0; }
          .actions button { padding: 7px 16px; font-size: 13px; border-radius: 6px; border: none; cursor: pointer; font-family: inherit; }
          .actions .btn-gray { background: #f0f0f0; color: #333; }
          .actions .btn-gray:hover { background: #e0e0e0; }
          .actions .btn-red { background: #ff5757; color: #fff; }
          .actions .btn-red:hover { background: #e74c4c; }

          .like-bar { text-align: center; margin-top: 20px; }
          .like-btn {
            display: inline-flex; align-items: center; gap: 8px;
            padding: 10px 28px; font-size: 15px; font-family: inherit;
            background: #fff; color: #666;
            border: 1px solid #e0e0e0; border-radius: 24px;
            cursor: pointer; transition: all 0.2s;
            box-shadow: 0 1px 3px rgba(0,0,0,0.04);
          }
          .like-btn:hover { border-color: #ff5a5f; color: #ff5a5f; transform: translateY(-1px); }
          .like-btn.liked { background: #fff0f0; color: #ff5a5f; border-color: #ff8a8a; }
          .like-btn .heart { font-size: 18px; line-height: 1; }
          .like-btn.liked .heart { animation: pop 0.35s ease; }
          @keyframes pop {
            0%   { transform: scale(1); }
            50%  { transform: scale(1.5); }
            100% { transform: scale(1); }
          }

          .comments { background: #fff; border-radius: 12px; padding: 24px 32px; margin-top: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
          .comments-title { font-size: 16px; color: #333; margin-bottom: 18px; }
          .comments-title span { color: #999; font-weight: normal; font-size: 14px; }
          .comment-form textarea {
            width: 100%; padding: 12px 14px; font-size: 14px; font-family: inherit;
            border: 1px solid #e0e0e0; border-radius: 10px; outline: none;
            resize: vertical; min-height: 80px; line-height: 1.6;
            transition: border 0.2s;
          }
          .comment-form textarea:focus { border-color: #07c160; }
          .comment-form-actions { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; }
          .comment-tip { font-size: 12px; color: #bbb; }
          .btn-comment {
            padding: 8px 20px; font-size: 14px; font-family: inherit;
            background: #07c160; color: #fff; border: none; border-radius: 8px;
            cursor: pointer; transition: background 0.2s;
          }
          .btn-comment:hover { background: #06ad56; }
          .btn-comment:disabled { background: #ccc; cursor: not-allowed; }

          .comment-list { margin-top: 24px; }

          .comment-item { display: flex; gap: 12px; padding: 16px 0; border-top: 1px solid #f5f5f5; }
          .comment-item:first-child { border-top: none; }
          .comment-avatar {
            width: 40px; height: 40px; border-radius: 50%; background: #07c160;
            color: #fff; display: flex; align-items: center; justify-content: center;
            font-weight: bold; font-size: 16px; flex-shrink: 0; overflow: hidden;
          }
          .comment-avatar img { width: 100%; height: 100%; object-fit: cover; }
          .comment-body { flex: 1; min-width: 0; }
          .comment-head { display: flex; align-items: center; gap: 10px; margin-bottom: 4px; }
          .comment-nick { font-size: 14px; font-weight: 500; color: #333; }
          .comment-time { font-size: 12px; color: #bbb; }
          .comment-op {
            margin-left: auto; font-size: 12px; color: #999;
            background: none; border: none; cursor: pointer; padding: 2px 6px;
            border-radius: 4px; font-family: inherit;
          }
          .comment-op:hover { color: #07c160; background: #f0fff5; }
          .comment-op.del:hover { color: #ff5757; background: #fff0f0; }
          .comment-text { font-size: 14px; color: #444; line-height: 1.7; word-wrap: break-word; white-space: pre-wrap; }

          .reply-list { margin-top: 12px; padding-left: 16px; border-left: 2px solid #f0f0f0; }
          .reply-item { display: flex; gap: 10px; padding: 10px 0; }
          .reply-avatar {
            width: 32px; height: 32px; border-radius: 50%; background: #07c160;
            color: #fff; display: flex; align-items: center; justify-content: center;
            font-weight: bold; font-size: 13px; flex-shrink: 0; overflow: hidden;
          }
          .reply-avatar img { width: 100%; height: 100%; object-fit: cover; }
          .reply-body { flex: 1; min-width: 0; }
          .reply-head { display: flex; align-items: center; gap: 8px; margin-bottom: 3px; font-size: 13px; }
          .reply-nick { color: #333; font-weight: 500; }
          .reply-time { color: #bbb; font-size: 12px; }
          .reply-to { color: #999; font-size: 12px; }
          .reply-to b { color: #07c160; font-weight: 500; }
          .reply-text { font-size: 13px; color: #555; line-height: 1.6; word-wrap: break-word; white-space: pre-wrap; }
          .reply-op {
            margin-left: auto; font-size: 12px; color: #999;
            background: none; border: none; cursor: pointer; padding: 2px 6px;
            border-radius: 4px; font-family: inherit;
          }
          .reply-op:hover { color: #07c160; background: #f0fff5; }
          .reply-op.del:hover { color: #ff5757; background: #fff0f0; }

          .reply-form {
            margin-top: 10px; padding: 12px;
            background: #fafafa; border-radius: 10px;
          }
          .reply-form textarea {
            width: 100%; padding: 8px 10px; font-size: 13px; font-family: inherit;
            border: 1px solid #e0e0e0; border-radius: 8px; outline: none;
            resize: vertical; min-height: 60px; line-height: 1.5;
          }
          .reply-form textarea:focus { border-color: #07c160; }
          .reply-form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
          .reply-form-actions button {
            padding: 6px 14px; font-size: 13px; border-radius: 6px; border: none;
            cursor: pointer; font-family: inherit;
          }
          .btn-reply-cancel { background: #eee; color: #333; }
          .btn-reply-submit { background: #07c160; color: #fff; }
          .btn-reply-submit:hover { background: #06ad56; }
          .btn-reply-submit:disabled { background: #ccc; cursor: not-allowed; }

          .comment-empty { text-align: center; color: #bbb; padding: 30px; font-size: 14px; }

          /* 在线用户抽屉 */
          #sidebar {
            position: fixed;
            top: 0;
            right: 0;
            bottom: 0;
            width: 220px;
            background: #fff;
            border-left: 1px solid #e5e5e5;
            overflow-y: auto;
            padding: 12px 10px;
            font-size: 13px;
            box-shadow: -2px 0 8px rgba(0,0,0,0.08);
            transform: translateX(100%);
            transition: transform 0.25s ease;
            z-index: 200;
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

          #sidebar-mask {
            position: fixed;
            inset: 0;
            background: rgba(0,0,0,0.15);
            display: none;
            z-index: 150;
          }
          #sidebar-mask.show { display: block; }

          .mask { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: none; align-items: center; justify-content: center; z-index: 300; padding: 20px; }
          .mask.show { display: flex; }
          .modal { background: #fff; border-radius: 12px; padding: 24px; width: 100%; max-width: 560px; max-height: 90vh; overflow-y: auto; }
          .modal h3 { font-size: 16px; margin-bottom: 16px; color: #333; }
          .modal input, .modal textarea {
            width: 100%; padding: 10px 12px; font-size: 14px; font-family: inherit;
            border: 1px solid #ddd; border-radius: 8px; outline: none; margin-bottom: 12px;
          }
          .modal input:focus, .modal textarea:focus { border-color: #07c160; }
          .modal textarea { min-height: 200px; resize: vertical; line-height: 1.6; }
          .modal-actions { display: flex; gap: 8px; margin-top: 8px; }
          .modal-actions button { flex: 1; padding: 10px; font-size: 14px; border-radius: 8px; border: none; cursor: pointer; font-family: inherit; }
          .modal-actions .save { background: #07c160; color: #fff; }
          .modal-actions .cancel { background: #eee; color: #333; }
          .msg { padding: 8px 12px; border-radius: 6px; font-size: 13px; margin-bottom: 12px; display: none; }
          .msg.show { display: block; }
          .msg.error { background: #fee; color: #c33; }
        </style></head><body>
        <div id="header">
          <span class="title">519SB 帖子</span>
          <div class="right">
            <a href="/posts" class="primary">帖子列表</a>
            <a href="/chat" class="primary">聊天室</a>
            <button id="online-btn" class="online-btn">在线 <span id="online-count">0</span></button>
            <a id="admin-link" href="/admin" style="display:none;">管理</a>
            <a href="/profile" title="个人中心"><span class="my-avatar" id="my-avatar"></span></a>
            <span id="my-nickname"></span>
            <button onclick="doLogout()">退出</button>
          </div>
        </div>
        <div class="container">
          <article class="post">
            <h1 id="post-title">加载中...</h1>
            <div class="meta" id="post-meta"></div>
            <div class="content" id="post-content"></div>
            <div class="actions" id="post-actions" style="display:none;">
              <button class="btn-gray" id="edit-btn">编辑</button>
              <button class="btn-red" id="del-btn">删除</button>
            </div>
          </article>

          <div class="like-bar">
            <button class="like-btn" id="like-btn">
              <span class="heart" id="like-heart">♡</span>
              <span id="like-text">点赞</span>
              <span id="like-count">0</span>
            </button>
          </div>

          <div class="comments">
            <h2 class="comments-title">评论 <span id="comment-count">0</span></h2>
            <div class="comment-form">
              <textarea id="comment-input" placeholder="说点什么..." maxlength="500"></textarea>
              <div class="comment-form-actions">
                <span class="comment-tip">最多 500 字</span>
                <button class="btn-comment" id="comment-submit">发表评论</button>
              </div>
            </div>
            <div class="comment-list" id="comment-list"></div>
          </div>
        </div>

        <div id="sidebar-mask" onclick="toggleSidebar(false)"></div>
        <div id="sidebar">
          <h3>
            <span>在线用户</span>
            <button class="close-btn" onclick="toggleSidebar(false)" title="关闭">×</button>
          </h3>
          <div id="online-list"></div>
        </div>

        <div class="mask" id="edit-mask">
          <div class="modal">
            <h3>编辑帖子</h3>
            <div id="edit-msg" class="msg"></div>
            <input type="text" id="edit-title" maxlength="100">
            <textarea id="edit-content"></textarea>
            <div class="modal-actions">
              <button class="cancel" onclick="closeEdit()">取消</button>
              <button class="save" onclick="saveEdit()">保存</button>
            </div>
          </div>
        </div>

        <script>
        const TOKEN = localStorage.getItem('token');
        const MY_USERNAME = localStorage.getItem('username') || '';
        const MY_NICKNAME = localStorage.getItem('nickname') || MY_USERNAME;
        const MY_AVATAR = localStorage.getItem('avatarId') || null;
        const IS_ADMIN = localStorage.getItem('isAdmin') === 'true';
        if (!TOKEN) { location.href = '/login'; }
        const POST_ID = __POST_ID__;
        let currentPost = null;
        let allComments = [];

        var ONLINE_REFRESH_INTERVAL = 10000;
        var lastOnlineSignature = '';
        var sidebar = document.getElementById('sidebar');
        var sidebarMask = document.getElementById('sidebar-mask');
        var onlineBtn = document.getElementById('online-btn');

        // 顶部信息
        document.getElementById('my-nickname').textContent = MY_NICKNAME;
        if (IS_ADMIN) document.getElementById('admin-link').style.display = 'inline-block';

        function renderMyAvatar() {
          const el = document.getElementById('my-avatar');
          el.innerHTML = '';
          if (MY_AVATAR && MY_AVATAR !== 'null') {
            const img = document.createElement('img');
            img.src = '/image/' + MY_AVATAR;
            el.appendChild(img);
          } else {
            el.textContent = MY_NICKNAME.charAt(0).toUpperCase();
          }
        }
        renderMyAvatar();

        function doLogout() {
          if (!confirm('确定退出登录？')) return;
          fetch('/api/logout?token=' + encodeURIComponent(TOKEN), { method: 'POST' }).finally(() => {
            localStorage.clear();
            location.href = '/login';
          });
        }

        // ==================== 侧边栏 ====================
        function toggleSidebar(show) {
          if (typeof show === 'undefined') show = !sidebar.classList.contains('show');
          if (show) {
            sidebar.classList.add('show');
            sidebarMask.classList.add('show');
            onlineBtn.classList.add('active');
            loadOnlineUsers();
          } else {
            sidebar.classList.remove('show');
            sidebarMask.classList.remove('show');
            onlineBtn.classList.remove('active');
          }
        }
        onlineBtn.onclick = function () { toggleSidebar(); };

        async function loadOnlineUsers() {
          try {
            var res = await fetch('/online?token=' + encodeURIComponent(TOKEN));
            var r = await res.json();
            if (!r.ok) return;
            renderOnlineUsers(r.users);
          } catch (e) {}
        }

        function renderOnlineUsers(users) {
          document.getElementById('online-count').textContent = users.length;

          var sig = users.map(function (u) { return u.username + ':' + (u.avatarId || ''); }).join('|');
          if (sig === lastOnlineSignature) return;
          lastOnlineSignature = sig;

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

        loadOnlineUsers();
        setInterval(loadOnlineUsers, ONLINE_REFRESH_INTERVAL);

        // ==================== 帖子详情 ====================
        function fmtTime(ms) {
          const d = new Date(ms);
          return d.getFullYear() + '-' +
                 String(d.getMonth() + 1).padStart(2, '0') + '-' +
                 String(d.getDate()).padStart(2, '0') + ' ' +
                 String(d.getHours()).padStart(2, '0') + ':' +
                 String(d.getMinutes()).padStart(2, '0');
        }

        async function loadPost() {
          try {
            const res = await fetch('/api/posts?id=' + POST_ID + '&token=' + encodeURIComponent(TOKEN));
            const r = await res.json();
            if (!r.ok) {
              document.getElementById('post-title').textContent = '加载失败：' + (r.msg || '');
              return;
            }
            renderPost(r.post, r.liked, r.likeCount, r.commentCount);
          } catch (e) {
            document.getElementById('post-title').textContent = '网络错误：' + e.message;
          }
        }

        function renderPost(p, liked, likeCount, commentCount) {
          currentPost = p;
          document.title = p.title + ' - 519SB';
          document.getElementById('post-title').textContent = p.title;
          document.getElementById('post-meta').textContent =
            'by ' + (p.authorNickname || p.author) + ' · ' + fmtTime(p.createdAt) + ' · ' + p.views + ' 阅读';
          document.getElementById('post-content').textContent = p.content;

          if (MY_USERNAME === p.author || IS_ADMIN) {
            document.getElementById('post-actions').style.display = 'flex';
          }

          const btn = document.getElementById('like-btn');
          const heart = document.getElementById('like-heart');
          const text = document.getElementById('like-text');
          const count = document.getElementById('like-count');
          if (liked) {
            btn.classList.add('liked'); heart.textContent = '❤'; text.textContent = '已赞';
          } else {
            btn.classList.remove('liked'); heart.textContent = '♡'; text.textContent = '点赞';
          }
          count.textContent = likeCount;

          document.getElementById('comment-count').textContent = commentCount;
        }

        document.getElementById('like-btn').onclick = async () => {
          try {
            const res = await fetch('/api/posts/like?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ postId: POST_ID })
            });
            const r = await res.json();
            if (r.ok) {
              const btn = document.getElementById('like-btn');
              const heart = document.getElementById('like-heart');
              const text = document.getElementById('like-text');
              if (r.liked) { btn.classList.add('liked'); heart.textContent = '❤'; text.textContent = '已赞'; }
              else { btn.classList.remove('liked'); heart.textContent = '♡'; text.textContent = '点赞'; }
              document.getElementById('like-count').textContent = r.likeCount;
            }
          } catch (e) { alert('网络错误：' + e.message); }
        };

        async function loadComments() {
          const list = document.getElementById('comment-list');
          try {
            const res = await fetch('/api/posts/comments?postId=' + POST_ID);
            const r = await res.json();
            if (!r.ok) { list.innerHTML = '<div class="comment-empty">加载失败</div>'; return; }
            allComments = r.comments || [];
            renderComments();
            document.getElementById('comment-count').textContent = allComments.length;
          } catch (e) {
            list.innerHTML = '<div class="comment-empty">网络错误：' + e.message + '</div>';
          }
        }

        function renderComments() {
          const list = document.getElementById('comment-list');
          list.innerHTML = '';
          if (allComments.length === 0) {
            list.innerHTML = '<div class="comment-empty">还没有评论，抢个沙发~</div>';
            return;
          }

          const topLevel = allComments.filter(c => c.parentId === null);
          const repliesByParent = {};
          allComments.forEach(c => {
            if (c.parentId !== null) {
              (repliesByParent[c.parentId] = repliesByParent[c.parentId] || []).push(c);
            }
          });

          topLevel.forEach(c => {
            list.appendChild(buildCommentItem(c, repliesByParent[c.id] || []));
          });
        }

        function buildCommentItem(c, replies) {
          const item = document.createElement('div');
          item.className = 'comment-item';
          item.dataset.commentId = c.id;

          const av = document.createElement('div');
          av.className = 'comment-avatar';
          if (c.avatarId) {
            const img = document.createElement('img');
            img.src = '/image/' + encodeURIComponent(c.avatarId);
            av.appendChild(img);
          } else {
            av.textContent = (c.nickname || c.username).charAt(0).toUpperCase();
          }

          const body = document.createElement('div');
          body.className = 'comment-body';

          const head = document.createElement('div');
          head.className = 'comment-head';
          const nick = document.createElement('span');
          nick.className = 'comment-nick';
          nick.textContent = c.nickname || c.username;
          const time = document.createElement('span');
          time.className = 'comment-time';
          time.textContent = fmtTime(c.createdAt);
          head.appendChild(nick);
          head.appendChild(time);

          const replyBtn = document.createElement('button');
          replyBtn.className = 'comment-op';
          replyBtn.textContent = '回复';
          replyBtn.onclick = () => openReplyForm(item, c.id, c.nickname || c.username);
          head.appendChild(replyBtn);

          if (MY_USERNAME === c.username || IS_ADMIN) {
            const del = document.createElement('button');
            del.className = 'comment-op del';
            del.textContent = '删除';
            del.onclick = () => deleteComment(c.id, item);
            head.appendChild(del);
          }

          const text = document.createElement('div');
          text.className = 'comment-text';
          text.textContent = c.content;

          body.appendChild(head);
          body.appendChild(text);

          if (replies.length > 0) {
            const rl = document.createElement('div');
            rl.className = 'reply-list';
            replies.forEach(r => rl.appendChild(buildReplyItem(r, c.id, c.nickname || c.username)));
            body.appendChild(rl);
          }

          const formBox = document.createElement('div');
          formBox.className = 'reply-form-box';
          formBox.style.display = 'none';
          body.appendChild(formBox);

          item.appendChild(av);
          item.appendChild(body);
          return item;
        }

        function buildReplyItem(r, topId, topNick) {
          const item = document.createElement('div');
          item.className = 'reply-item';
          item.dataset.commentId = r.id;

          const av = document.createElement('div');
          av.className = 'reply-avatar';
          if (r.avatarId) {
            const img = document.createElement('img');
            img.src = '/image/' + encodeURIComponent(r.avatarId);
            av.appendChild(img);
          } else {
            av.textContent = (r.nickname || r.username).charAt(0).toUpperCase();
          }

          const body = document.createElement('div');
          body.className = 'reply-body';

          const head = document.createElement('div');
          head.className = 'reply-head';
          const nick = document.createElement('span');
          nick.className = 'reply-nick';
          nick.textContent = r.nickname || r.username;

          const to = document.createElement('span');
          to.className = 'reply-to';
          const replyToNick = r.replyToNick || '';
          if (replyToNick && replyToNick !== (r.nickname || r.username)) {
            to.appendChild(document.createTextNode('回复 '));
            const b = document.createElement('b');
            b.textContent = replyToNick;
            to.appendChild(b);
          }

          const time = document.createElement('span');
          time.className = 'reply-time';
          time.textContent = fmtTime(r.createdAt);

          head.appendChild(nick);
          if (to.textContent) head.appendChild(to);
          head.appendChild(time);

          const replyBtn = document.createElement('button');
          replyBtn.className = 'reply-op';
          replyBtn.textContent = '回复';
          replyBtn.onclick = () => {
            const topItem = item.closest('.comment-item');
            openReplyForm(topItem, topId, r.nickname || r.username);
          };
          head.appendChild(replyBtn);

          if (MY_USERNAME === r.username || IS_ADMIN) {
            const del = document.createElement('button');
            del.className = 'reply-op del';
            del.textContent = '删除';
            del.onclick = () => deleteComment(r.id, item);
            head.appendChild(del);
          }

          const text = document.createElement('div');
          text.className = 'reply-text';
          text.textContent = r.content;

          body.appendChild(head);
          body.appendChild(text);
          item.appendChild(av);
          item.appendChild(body);
          return item;
        }

        function openReplyForm(topCommentItem, parentId, targetNick) {
          document.querySelectorAll('.reply-form-box').forEach(b => {
            b.style.display = 'none';
            b.innerHTML = '';
          });

          const box = topCommentItem.querySelector('.reply-form-box');
          box.style.display = 'block';
          box.innerHTML = '';

          const form = document.createElement('div');
          form.className = 'reply-form';

          const ta = document.createElement('textarea');
          ta.placeholder = '回复 @' + targetNick + '：';
          ta.maxLength = 500;

          const actions = document.createElement('div');
          actions.className = 'reply-form-actions';

          const cancel = document.createElement('button');
          cancel.className = 'btn-reply-cancel';
          cancel.textContent = '取消';
          cancel.onclick = () => { box.style.display = 'none'; box.innerHTML = ''; };

          const submit = document.createElement('button');
          submit.className = 'btn-reply-submit';
          submit.textContent = '回复';
          submit.onclick = async () => {
            const content = ta.value.trim();
            if (!content) { alert('回复不能为空'); return; }
            submit.disabled = true;
            submit.textContent = '发送中...';
            try {
              const res = await fetch('/api/posts/comment?token=' + encodeURIComponent(TOKEN), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ postId: POST_ID, content, parentId })
              });
              const r = await res.json();
              if (r.ok) {
                box.style.display = 'none';
                box.innerHTML = '';
                await loadComments();
              } else {
                alert(r.msg || '回复失败');
                submit.disabled = false;
                submit.textContent = '回复';
              }
            } catch (e) {
              alert('网络错误：' + e.message);
              submit.disabled = false;
              submit.textContent = '回复';
            }
          };

          actions.appendChild(cancel);
          actions.appendChild(submit);
          form.appendChild(ta);
          form.appendChild(actions);
          box.appendChild(form);
          ta.focus();
        }

        document.getElementById('comment-submit').onclick = async () => {
          const input = document.getElementById('comment-input');
          const content = input.value.trim();
          if (!content) { alert('评论不能为空'); return; }

          const btn = document.getElementById('comment-submit');
          btn.disabled = true;
          btn.textContent = '发送中...';
          try {
            const res = await fetch('/api/posts/comment?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ postId: POST_ID, content })
            });
            const r = await res.json();
            if (r.ok) {
              input.value = '';
              await loadComments();
            } else {
              alert(r.msg || '评论失败');
            }
          } catch (e) {
            alert('网络错误：' + e.message);
          } finally {
            btn.disabled = false;
            btn.textContent = '发表评论';
          }
        };

        async function deleteComment(id, itemEl) {
          if (!confirm('确定删除这条评论？')) return;
          try {
            const res = await fetch('/api/posts/deleteComment?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ id })
            });
            const r = await res.json();
            if (r.ok) {
              await loadComments();
            } else {
              alert(r.msg || '删除失败');
            }
          } catch (e) { alert('网络错误：' + e.message); }
        }

        function openEdit() {
          if (!currentPost) return;
          document.getElementById('edit-title').value = currentPost.title;
          document.getElementById('edit-content').value = currentPost.content;
          document.getElementById('edit-msg').className = 'msg';
          document.getElementById('edit-mask').classList.add('show');
        }
        function closeEdit() {
          document.getElementById('edit-mask').classList.remove('show');
        }
        function showEditMsg(text) {
          const el = document.getElementById('edit-msg');
          el.textContent = text;
          el.className = 'msg show error';
        }

        async function saveEdit() {
          const title = document.getElementById('edit-title').value.trim();
          const content = document.getElementById('edit-content').value;
          if (!title) { showEditMsg('标题不能为空'); return; }
          if (!content.trim()) { showEditMsg('内容不能为空'); return; }
          try {
            const res = await fetch('/api/posts/update?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ id: POST_ID, title, content })
            });
            const r = await res.json();
            if (r.ok) { closeEdit(); loadPost(); }
            else showEditMsg(r.msg || '保存失败');
          } catch (e) { showEditMsg('网络错误：' + e.message); }
        }

        async function deletePost() {
          if (!confirm('确定删除这篇帖子？不可恢复。')) return;
          try {
            const res = await fetch('/api/posts/delete?token=' + encodeURIComponent(TOKEN), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ id: POST_ID })
            });
            const r = await res.json();
            if (r.ok) location.href = '/posts';
            else alert(r.msg || '删除失败');
          } catch (e) { alert('网络错误：' + e.message); }
        }

        document.addEventListener('keydown', function (e) {
          if (e.key === 'Escape') toggleSidebar(false);
        });

        document.getElementById('edit-btn').onclick = openEdit;
        document.getElementById('del-btn').onclick = deletePost;
        loadPost();
        loadComments();
        </script></body></html>
        """;
}