/*
 * 519SB 共享 UI 层：登录态守卫 + 底部导航栏。
 *
 * 为什么要有「登录态守卫」这一段：
 * 页面是纯静态 HTML，服务端拿不到 localStorage 里的 token，所以不做重定向。
 * 结果就是 token 早已失效（在别的标签页退出、会话过期、服务器重启）时，
 * 页面照样能打开，界面还显示着登录状态 —— 用户以为自己登着，实际所有请求 401。
 *
 * 这里统一收口：以服务端 /api/session 为唯一权威，失效就清本地状态并踢回登录页。
 * 只写一处，8 个页面共享。
 *
 * 引入位置：必须在 <head> 里（同步执行），且排在页面自己的脚本之前。
 * 放在 body 末尾时，页面内联脚本早就发完请求了 —— 那些 401 没人接，
 * 页面就会一直停在一个「未登录但还在」的状态。
 *
 * 兼容策略：所有动作都先探测页面是否提供了对应函数/按钮，
 * 探测不到就跳到能完成该动作的页面，绝不让点击变成"没反应"。
 */
(function () {
  'use strict';

  var LOGIN_PAGE = /^\/(login|gate)\/?$/;
  var AUTH_KEYS = ['token', 'username', 'nickname', 'avatarId', 'isAdmin'];

  function readToken() {
    try { return localStorage.getItem('token'); } catch (e) { return null; }
  }

  function clearAuth() {
    for (var i = 0; i < AUTH_KEYS.length; i++) {
      try { localStorage.removeItem(AUTH_KEYS[i]); } catch (e) {}
    }
  }

  /* ==================== 一、登录态守卫 ==================== */

  var killing = false;

  /**
   * 统一登出：清本地状态 + 跳登录页。
   * reason 会带到 /login，用于在页面上说明"为什么退出来了"。
   */
  function forceLogout(reason) {
    if (killing) return;
    killing = true;
    clearAuth();
    if (LOGIN_PAGE.test(location.pathname)) return;
    location.replace('/login' + (reason ? '?reason=' + reason : ''));
  }
  window.appForceLogout = forceLogout;

  var rawFetch = window.fetch;

  /*
   * 有些入口是「带 token 的链接」（服务端要用它渲染配色，比如 /admin?token=…、
   * /user/xxx?token=…）。这类请求的 token 在 URL 上而不在 localStorage，
   * 如果直接按 localStorage 判断，会把刚点进来的管理员当成未登录踢走。
   * 所以先把它收进本地，再走统一守卫。
   */
  (function adoptTokenFromUrl() {
    try {
      var t = new URLSearchParams(location.search).get('token');
      // 只在本地没有身份时采纳：否则别人分享一条带 token 的链接，
      // 打开就会顶掉当前登录的账号。
      if (t && !localStorage.getItem('token')) localStorage.setItem('token', t);
    } catch (e) {}
  })();

  /* 没有 token 就别渲染了，直接回登录页 */
  if (!readToken() && !LOGIN_PAGE.test(location.pathname)) {
    location.replace('/login');
    return;
  }

  /**
   * 任何接口返回 401 都视为登录态失效。
   *
   * 包一层 fetch 而不是在十几处调用点各写一遍：漏掉任何一处，
   * 用户就会看到"页面还在、数据全空"的诡异状态。
   */
  if (typeof rawFetch === 'function') {
    window.fetch = function () {
      var p = rawFetch.apply(this, arguments);
      p.then(function (res) {
        if (res && res.status === 401) forceLogout('expired');
      }, function () {});
      return p;
    };
  }

  /* verifySession 可能比导航栏挂载更早完成，先存一份，挂载后补画 */
  var lastSession = null;

  /* 用服务端返回的真实身份覆盖本地缓存，防止本地被改花 */
  function verifySession() {
    var token = readToken();
    if (!token) { forceLogout('expired'); return; }
    (rawFetch || window.fetch)('/api/session?token=' + encodeURIComponent(token),
        { cache: 'no-store' })
      .then(function (res) {
        if (res.status === 401) { forceLogout('expired'); return null; }
        return res.ok ? res.json() : null;
      })
      .then(function (d) {
        if (!d || !d.ok) return;
        try {
          localStorage.setItem('username', d.username || '');
          localStorage.setItem('nickname', d.nickname || d.username || '');
          localStorage.setItem('avatarId', d.avatarId == null ? 'null' : d.avatarId);
          localStorage.setItem('isAdmin', d.isAdmin ? 'true' : 'false');
        } catch (e) {}
        lastSession = d;
        window.appSession = d;
        // 管理后台：服务端已经确认不是管理员，就别把人留在这页
        if (location.pathname.indexOf('/admin') === 0 && !d.isAdmin) {
          location.replace('/chat');
          return;
        }
        paintIdentity(d);
        // 页面若需要在身份确认后刷新自己的内容（比如资料页的头像/昵称），挂个钩子
        if (typeof window.onSessionReady === 'function') {
          try { window.onSessionReady(d); } catch (e) {}
        }
      })
      .catch(function () { /* 网络抖动不踢人，避免离线误判 */ });
  }

  /* 别的标签页退出登录 → 本页立刻跟上，不要停留在假登录态 */
  window.addEventListener('storage', function (e) {
    if (e.key === null || (e.key === 'token' && !e.newValue)) {
      forceLogout('logout');
    }
  });

  /* 浏览器后退/前进恢复页面（bfcache）时不会重新执行脚本，必须手动复核 */
  window.addEventListener('pageshow', function (e) {
    if (e.persisted) verifySession();
  });

  /* 挂机不动也要在会话过期后及时退出，而不是等下一次点击才 401 */
  setInterval(verifySession, 60000);

  /**
   * 主动退出：先通知服务端销毁会话，再清本地。
   * 页面的退出按钮统一调这个，避免每页各写一遍（也避免 localStorage.clear()
   * 顺手把主题偏好一起清掉）。
   */
  window.appLogout = function () {
    var token = readToken();
    if (!token) { forceLogout('logout'); return; }
    rawFetch('/api/logout?token=' + encodeURIComponent(token), { method: 'POST' })
      .then(function () { forceLogout('logout'); },
            function () { forceLogout('logout'); });
  };

  /* ==================== 二、导航（手机底栏 / PC 侧栏） ====================
     *
     * 两套导航同时挂载，用 CSS 媒体查询决定谁显示：
     *   < 900px  底部 tabbar（触屏习惯，拇指可达）
     *   ≥ 900px  左侧 sidenav（鼠标习惯，导航常驻不占内容高度）
     * 只挂载不判断宽度，是为了窗口拖拽缩放时不用重新渲染。
     */

  var ICONS = {
    home: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 10.5 12 3l9 7.5"/><path d="M5.5 9.5V21h13V9.5"/><path d="M9.5 21v-6h5v6"/></svg>',
    chat: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 9.6 9.6 0 0 1-3.9-.8L3 21l1.9-4.5a8.2 8.2 0 0 1-.9-3.9 8.4 8.4 0 0 1 8.4-8.4 8.4 8.4 0 0 1 8.6 7.3Z"/></svg>',
    plus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>',
    bell: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8a6 6 0 1 0-12 0c0 7-3 8-3 8h18s-3-1-3-8"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/></svg>',
    user: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-6.5 8-6.5S20 17 20 21"/></svg>',
    admin: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l7 3v6c0 4.2-2.9 7.6-7 9-4.1-1.4-7-4.8-7-9V6l7-3Z"/><path d="M9.2 12.2l2 2 3.6-3.8"/></svg>',
    logout: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3"/><path d="M10 8l-4 4 4 4"/><path d="M6 12h9"/></svg>'
  };

  var path = location.pathname;
  var isPosts = path === '/' || path.indexOf('/posts') === 0;
  var isChat = path.indexOf('/chat') === 0;
  var isAdmin = path.indexOf('/admin') === 0;
  // 手机底栏没有管理后台这一项，所以后台页归到「我的」下高亮；
  // PC 侧栏有独立的管理入口，就各亮各的。
  var isProfile = path.indexOf('/profile') === 0 || path.indexOf('/user') === 0;
  var isMeTab = isProfile || isAdmin;

  /**
   * 把当前登录身份画到导航栏上。
   *
   * 这是「登录状态不清楚」最直接的解法：底栏常驻，头像 + 昵称一直在，
   * 用户任何时刻都能确认自己是登录着的、以谁的身份在用。
   */
  /* 底栏和侧栏的「我」是两份 DOM，画的时候走同一段逻辑。 */
  function paintOne(iconBox, nameBox, d, maxLen) {
    if (!iconBox || !nameBox) return;

    var nick = (d && (d.nickname || d.username)) || '';
    if (!nick) {
      try {
        nick = localStorage.getItem('nickname') || localStorage.getItem('username') || '';
      } catch (e) {}
    }

    var avatarId = d ? d.avatarId : undefined;
    if (avatarId === undefined) {
      try {
        var raw = localStorage.getItem('avatarId');
        avatarId = (raw === 'null' || raw === 'undefined') ? null : raw;
      } catch (e) {}
    }

    if (avatarId && avatarId !== 'null' && avatarId !== 'undefined') {
      iconBox.innerHTML = '<img alt="" src="/image/' + encodeURIComponent(avatarId) + '">';
    } else if (nick) {
      iconBox.textContent = nick.charAt(0).toUpperCase();
    }

    var label = nick || '我的';
    if (maxLen && label.length > maxLen) label = label.slice(0, maxLen);
    nameBox.textContent = label;
  }

  function paintIdentity(d) {
    paintOne(document.getElementById('app-me-icon'),
             document.getElementById('app-me-name'), d, 5);
    paintOne(document.getElementById('sn-me-icon'),
             document.getElementById('sn-me-name'), d, 0);

    var me = document.getElementById('app-me');
    var nick = (d && (d.nickname || d.username)) || '';
    if (me) me.classList.toggle('signed', !!nick);

    /* 侧栏用户卡：账号名 + 管理员入口 */
    var handle = document.getElementById('sn-me-handle');
    if (handle) {
      var uname = (d && d.username) || '';
      if (!uname) { try { uname = localStorage.getItem('username') || ''; } catch (e) {} }
      handle.textContent = uname ? '@' + uname : '';
    }

    var adminLink = document.getElementById('sn-admin');
    if (adminLink) {
      var admin = false;
      try { admin = localStorage.getItem('isAdmin') === 'true'; } catch (e) {}
      if (d && d.isAdmin !== undefined) admin = !!d.isAdmin;
      adminLink.hidden = !admin;
      adminLink.style.display = admin ? '' : 'none';
    }
  }

  function link(href, icon, label, active) {
    return '<a href="' + href + '" class="' + (active ? 'active' : '') + '">'
      + '<span class="tb-icon">' + ICONS[icon] + '</span>'
      + '<span>' + label + '</span></a>';
  }

  function mountTabbar() {
    if (document.getElementById('app-tabbar')) return;
    if (!document.body) return;

    var bar = document.createElement('nav');
    bar.id = 'app-tabbar';
    bar.className = 'tabbar';

    bar.innerHTML =
        link('/posts', 'home', '首页', isPosts)
      + link('/chat', 'chat', '聊天室', isChat)
      + '<button type="button" class="tb-fab" id="app-fab" title="发帖">' + ICONS.plus + '</button>'
      + '<a href="javascript:void(0)" id="app-online">'
        + '<span class="tb-icon">' + ICONS.bell + '</span><span>在线</span></a>'
      + '<a href="/profile" id="app-me" class="' + (isProfile ? 'active' : '') + '">'
        + '<span class="tb-icon" id="app-me-icon">' + ICONS.user + '</span>'
        + '<span id="app-me-name">我的</span></a>';

    document.body.appendChild(bar);
    paintIdentity(lastSession);

    /* 中间的发布按钮：帖子页直接点页面自己的「发帖」；其它页跳回帖子页 */
    bar.querySelector('#app-fab').addEventListener('click', function () {
      var btn = document.getElementById('new-btn');
      if (btn) { btn.click(); return; }
      location.href = '/posts';
    });

    /* 在线：帖子页有侧边抽屉，直接打开；其它页跳回帖子页再看 */
    bar.querySelector('#app-online').addEventListener('click', function () {
      if (typeof window.toggleSidebar === 'function') { window.toggleSidebar(true); return; }
      if (typeof window.openOnline === 'function') { window.openOnline(); return; }
      location.href = '/posts';
    });
  }

  /* ---------- PC 端左侧导航 ---------- */

  function snLink(href, icon, label, active) {
    return '<a href="' + href + '" class="sn-item' + (active ? ' active' : '') + '">'
      + '<span class="sn-icon">' + ICONS[icon] + '</span>'
      + '<span class="sn-label">' + label + '</span></a>';
  }

  function mountSidenav() {
    if (document.getElementById('app-sidenav')) return;
    if (!document.body) return;

    var el = document.createElement('aside');
    el.id = 'app-sidenav';
    el.className = 'sidenav';
    el.innerHTML =
        '<div class="sn-brand">'
          + '<span class="sn-logo">5</span>'
          + '<span class="sn-brand-text"><b>519SB</b><small>帖子 · 聊天 · 分享</small></span>'
        + '</div>'
      + '<nav class="sn-nav">'
        + snLink('/posts', 'home', '首页', isPosts)
        + snLink('/chat', 'chat', '聊天室', isChat)
        + '<button type="button" class="sn-item sn-new" id="sn-fab">'
          + '<span class="sn-icon">' + ICONS.plus + '</span><span class="sn-label">发布新帖</span></button>'
        + '<a href="javascript:void(0)" class="sn-item" id="sn-online">'
          + '<span class="sn-icon">' + ICONS.bell + '</span><span class="sn-label">在线用户</span></a>'
        + snLink('/profile', 'user', '个人中心', isProfile)
        + '<a href="/admin" id="sn-admin" class="sn-item' + (isAdmin ? ' active' : '') + '" style="display:none;">'
          + '<span class="sn-icon">' + ICONS.admin + '</span><span class="sn-label">管理后台</span></a>'
      + '</nav>'
      + '<div class="sn-user">'
        + '<span class="sn-avatar" id="sn-me-icon">' + ICONS.user + '</span>'
        + '<span class="sn-user-meta"><b id="sn-me-name">我的</b><small id="sn-me-handle"></small></span>'
        + '<button type="button" class="sn-logout" id="sn-logout" title="退出登录" aria-label="退出登录">'
          + ICONS.logout + '</button>'
      + '</div>';

    document.body.appendChild(el);
    paintIdentity(lastSession);

    /* /admin 是服务端按 URL 上的 token 校验并渲染的，裸链接会 302 回登录页 */
    var adminLink = el.querySelector('#sn-admin');
    if (adminLink) {
      var tk = readToken();
      if (tk) adminLink.href = '/admin?token=' + encodeURIComponent(tk);
    }

    el.querySelector('#sn-fab').addEventListener('click', function () {
      var btn = document.getElementById('new-btn');
      if (btn) { btn.click(); return; }
      location.href = '/posts';
    });

    el.querySelector('#sn-online').addEventListener('click', function () {
      if (typeof window.toggleSidebar === 'function') { window.toggleSidebar(true); return; }
      if (typeof window.openOnline === 'function') { window.openOnline(); return; }
      location.href = '/posts';
    });

    el.querySelector('#sn-logout').addEventListener('click', function () {
      window.appLogout();
    });
  }

  function mountNav() {
    mountTabbar();
    mountSidenav();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', mountNav);
  } else {
    mountNav();
  }

  /* 启动时校验一次：本地有 token 不代表服务端还认它 */
  verifySession();
})();
