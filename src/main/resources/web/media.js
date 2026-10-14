/*
 * 动态图上传助手（聊天 / 帖子 / 评论共用）。
 *
 * 为什么需要它：页面原来的做法是先用 canvas 压成 JPEG 再上传，
 * 而 canvas 只取第一帧——GIF 和动态 WebP 的动画会被压没，变成一张静态图。
 *
 * 这里按格式分流：
 *   - GIF / WebP  → 原样上传（type=anim），保留动画
 *   - 其它图片    → 交给调用方提供的压缩函数，再按普通图片上传
 *
 * 用法（各页面的 compressImage 实现不同，所以由调用方传进来）：
 *   MediaUpload.send(file, {
 *     token: TOKEN,
 *     compress: compressImage,      // function(file, maxWidth, cb)
 *     maxWidth: IMAGE_MAX_WIDTH,
 *     stillType: 'message',         // 非动图时的用途
 *     onSuccess: function (id, isAnimated) { ... },
 *     onError: function (msg) { ... }
 *   });
 */
(function () {
  /** 这两种格式才可能带多帧；JPEG/PNG 一定是静态的 */
  function isAnimatedFormat(file) {
    var t = (file && file.type ? file.type : '').toLowerCase();
    return t === 'image/gif' || t === 'image/webp';
  }

  function readAsDataUrl(file, cb) {
    var r = new FileReader();
    r.onload = function () { cb(r.result); };
    r.onerror = function () { cb(null); };
    r.readAsDataURL(file);
  }

  function post(token, type, dataUrl, onOk, onErr) {
    fetch('/image/upload?token=' + encodeURIComponent(token) + '&type=' + encodeURIComponent(type), {
      method: 'POST',
      body: dataUrl
    }).then(function (res) {
      if (!res.ok) {
        // 413 通常是超了体积上限，415 是格式没通过魔数校验
        onErr('上传失败（HTTP ' + res.status + '）'
          + (res.status === 413 ? '：文件太大' : '')
          + (res.status === 415 ? '：格式不受支持' : '')
          + (res.status === 429 ? '：超出配额' : ''));
        return null;
      }
      return res.text();
    }).then(function (id) {
      if (id != null) onOk(id.trim());
    }).catch(function (e) {
      onErr('上传失败：' + e.message);
    });
  }

  function send(file, opts) {
    if (!file) return;
    var token = opts.token;
    var animated = isAnimatedFormat(file);

    if (animated) {
      // 原样上传，不压缩——压了就变静态图了
      readAsDataUrl(file, function (dataUrl) {
        if (!dataUrl) { opts.onError('读取文件失败'); return; }
        post(token, 'anim', dataUrl, function (id) {
          opts.onSuccess(id, true);
        }, opts.onError);
      });
      return;
    }

    if (typeof opts.compress !== 'function') {
      // 没有压缩函数就原样按静态图传
      readAsDataUrl(file, function (dataUrl) {
        if (!dataUrl) { opts.onError('读取文件失败'); return; }
        post(token, opts.stillType || 'message', dataUrl, function (id) {
          opts.onSuccess(id, false);
        }, opts.onError);
      });
      return;
    }

    opts.compress(file, opts.maxWidth || 800, function (dataUrl) {
      post(token, opts.stillType || 'message', dataUrl, function (id) {
        opts.onSuccess(id, false);
      }, opts.onError);
    });
  }

  /**
   * 原始二进制上传（视频 / 文件）。
   *
   * 不走 base64：base64 会把体积撑大 1/3 还要额外解码，大文件既费带宽又费内存。
   * 直接把 File 作为请求体发出去，服务端按原始字节接收。
   *
   * 用 XMLHttpRequest 而不是 fetch，因为只有它能拿到**上传进度**。
   * 大文件没有进度反馈时，使用者会以为卡住了去刷新页面——
   * 那会让在途请求被浏览器中止，报出来的就是 "Failed to fetch"。
   *
   * @return 可用于取消的 xhr 对象
   */
  function uploadRaw(file, opts) {
    var url = '/image/upload?token=' + encodeURIComponent(opts.token)
            + '&type=' + encodeURIComponent(opts.type);
    if (opts.fileName) {
      url += '&filename=' + encodeURIComponent(opts.fileName);
    }

    var xhr = new XMLHttpRequest();
    if (opts.onStart) opts.onStart(xhr);

    xhr.open('POST', url, true);

    if (opts.onProgress) {
      xhr.upload.onprogress = function (e) {
        if (e.lengthComputable) opts.onProgress(e.loaded / e.total, e.loaded, e.total);
      };
    }

    xhr.onload = function () {
      if (xhr.status >= 200 && xhr.status < 300) {
        opts.onSuccess((xhr.responseText || '').trim());
        return;
      }
      opts.onError(httpErrorText(xhr.status));
    };

    // onerror 只在网络层失败时触发：连接被重置、离线、或请求被中止后浏览器
    // 把它归为网络错误。真正的"文件太大"如果被服务端提前拒绝，也会走这里，
    // 所以提示里要把两种可能都说到。
    xhr.onerror = function () {
      opts.onError('网络中断，或文件超过服务器上限被拒绝'
        + '\n\n如果上传过程中刷新/关闭过页面，请求会被浏览器中止，也会出现这个提示。'
        + '\n请确认文件大小后重试。');
    };

    xhr.onabort = function () {
      if (opts.onAbort) opts.onAbort();
    };

    xhr.send(file);
    return xhr;
  }

  /** 把 HTTP 状态码翻译成可读提示 */
  function httpErrorText(status) {
    var extra = '';
    if (status === 413) extra = '：文件太大';
    else if (status === 415) extra = '：格式不受支持';
    else if (status === 429) extra = '：超出配额或发送过于频繁';
    else if (status === 403) extra = '：没有权限（登录可能已过期）';
    else if (status === 401) extra = '：请重新登录';
    return '上传失败（HTTP ' + status + '）' + extra;
  }

  window.MediaUpload = {
    isAnimatedFormat: isAnimatedFormat,
    send: send,
    uploadRaw: uploadRaw,
    httpErrorText: httpErrorText
  };
})();
