/*
 * 个性化配色：客户端应用。
 *
 * 为什么需要它：服务端注入只在请求带 token 时生效，但 /profile、/posts、
 * /admin 这些页面是通过普通链接进入的，token 只存在 localStorage 里，
 * 服务器拿不到，于是只能渲染默认色。这里用 localStorage 兜住。
 *
 * 引入位置有讲究：必须放在模板自带 <style> 之后。同优先级的选择器后者生效，
 * 提前引入会让默认 :root 把它盖住，等于没生效。
 */
(function () {
  var KEY_NAME = 'theme';
  var KEY_VARS = 'themeVars';

  function readLocal(key) {
    try { return localStorage.getItem(key); } catch (e) { return null; }
  }
  function writeLocal(key, val) {
    try { localStorage.setItem(key, val); } catch (e) { /* 忽略 */ }
  }

  /** 注入覆盖样式；追加到 head 末尾以保证排在模板 :root 之后 */
  function apply(name, vars) {
    if (!name || name === 'green' || !vars || !vars.primary) return;
    if (document.getElementById('theme-override-client')) return;

    var css = ':root{--primary:' + vars.primary
      + ';--primary-dark:' + (vars.dark || vars.primary)
      + ';--primary-light:' + (vars.light || vars.primary)
      + ';--primary-fade:' + (vars.fade || vars.primary) + ';}';

    var el = document.createElement('style');
    el.id = 'theme-override-client';
    el.textContent = css;
    var head = document.head || document.documentElement;
    head.appendChild(el);
  }

  // 服务端已经按 token 注入过了，那就是权威结果，不重复处理
  var serverInjected = !!document.getElementById('theme-override');

  var name = readLocal(KEY_NAME);
  if (name) {
    if (!serverInjected) {
      var raw = readLocal(KEY_VARS);
      if (raw) {
        try { apply(name, JSON.parse(raw)); } catch (e) { /* 数据坏了就当默认 */ }
      }
    }
    return;
  }

  // 本地没有记录（换设备、清过缓存）：向服务端问一次并缓存下来。
  // 走这个接口不需要 URL 带 token——token 就在 localStorage 里。
  var token = readLocal('token');
  if (!token) return;

  fetch('/api/profile/theme?token=' + encodeURIComponent(token))
    .then(function (r) { return r.json(); })
    .then(function (r) {
      if (!r || !r.ok || !r.current) return;
      writeLocal(KEY_NAME, r.current);
      var list = r.themes || [];
      for (var i = 0; i < list.length; i++) {
        if (list[i].name === r.current && list[i].vars) {
          writeLocal(KEY_VARS, JSON.stringify(list[i].vars));
          if (!serverInjected) apply(r.current, list[i].vars);
          break;
        }
      }
    })
    .catch(function () { /* 网络失败就用默认配色 */ });
})();
