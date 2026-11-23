/*
 * 519SB 共享 UI 层：注入底部导航栏。
 *
 * 为什么用 JS 注入而不是改每页 HTML：
 * 8 个页面都要有同一套底栏，写死在模板里意味着以后改一处要改八处。
 * 这里统一生成，页面只需要在 </body> 前引一个 <script src="/app.js">。
 *
 * 兼容策略：所有动作都先探测页面是否提供了对应函数/按钮，
 * 探测不到就跳到能完成该动作的页面，绝不让点击变成"没反应"。
 */
(function () {
  if (document.getElementById('app-tabbar')) return;

  var ICONS = {
    home: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 10.5 12 3l9 7.5"/><path d="M5.5 9.5V21h13V9.5"/><path d="M9.5 21v-6h5v6"/></svg>',
    chat: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 9.6 9.6 0 0 1-3.9-.8L3 21l1.9-4.5a8.2 8.2 0 0 1-.9-3.9 8.4 8.4 0 0 1 8.4-8.4 8.4 8.4 0 0 1 8.6 7.3Z"/></svg>',
    plus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>',
    bell: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8a6 6 0 1 0-12 0c0 7-3 8-3 8h18s-3-1-3-8"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/></svg>',
    user: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-6.5 8-6.5S20 17 20 21"/></svg>'
  };

  var path = location.pathname;
  var isPosts = path === '/' || path.indexOf('/posts') === 0;
  var isChat = path.indexOf('/chat') === 0;
  var isProfile = path.indexOf('/profile') === 0 || path.indexOf('/user') === 0;
  var isAdmin = path.indexOf('/admin') === 0;

  var bar = document.createElement('nav');
  bar.id = 'app-tabbar';
  bar.className = 'tabbar';

  function link(href, icon, label, active) {
    return '<a href="' + href + '" class="' + (active ? 'active' : '') + '">'
      + '<span class="tb-icon">' + ICONS[icon] + '</span>'
      + '<span>' + label + '</span></a>';
  }

  bar.innerHTML =
      link('/posts', 'home', '首页', isPosts)
    + link('/chat', 'chat', '聊天室', isChat)
    + '<button type="button" class="tb-fab" id="app-fab" title="发帖">' + ICONS.plus + '</button>'
    + '<a href="javascript:void(0)" id="app-online" class="' + (isAdmin ? 'active' : '') + '">'
      + '<span class="tb-icon">' + ICONS.bell + '</span><span>在线</span></a>'
    + link('/profile', 'user', '我的', isProfile);

  document.body.appendChild(bar);

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
})();
