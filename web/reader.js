(function () {
  "use strict";
  var App = window.NotesApp;
  var root = document.getElementById("reader");

  // 支持 /note/n1 形式的前端路由（StaticHandler 会映射到 reader.html）
  var match = window.location.pathname.match(/\/note\/([^/]+)$/);
  var id = match ? decodeURIComponent(match[1]) : new URLSearchParams(window.location.search).get("id");

  if (!id) {
    root.innerHTML = '<div class="panel"><div class="empty">缺少笔记 ID，请从<a href="/">公开目录</a>进入。</div></div>';
    return;
  }

  function render(note) {
    var toc = (note.chapters || []).map(function (c, i) {
      return '<li><a href="#' + App.esc(c.anchor || ("chap-" + (i + 1))) + '">' + App.esc(c.heading) + '</a></li>';
    }).join("");

    var chapters = (note.chapters || []).map(function (c, i) {
      var anchor = c.anchor || ("chap-" + (i + 1));
      return '<section class="chapter" id="' + App.esc(anchor) + '">' +
        '<h3>' + App.esc(c.heading) + '</h3>' +
        '<div class="markdown">' + App.renderMarkdown(c.body || "") + '</div>' +
        '</section>';
    }).join("");

    var refs = (note.references || []).map(function (r, i) {
      var link = r.url
        ? ' <a href="' + App.esc(r.url) + '" target="_blank" rel="noopener noreferrer">原文链接</a>'
        : "";
      return '<div class="ref-item">' +
        '<div><span class="ref-type">' + App.esc(r.type || "其他") + '</span>' +
        '<strong>[' + (i + 1) + '] ' + App.esc(r.title) + '</strong>' + link + '</div>' +
        (r.authors ? '<div class="ref-authors">' + App.esc(r.authors) + (r.year ? "（" + App.esc(r.year) + "）" : "") + '</div>' : '') +
        (r.note ? '<div class="ref-note">引用说明：' + App.esc(r.note) + '</div>' : '') +
        '</div>';
    }).join("");

    root.innerHTML =
      '<div class="panel reader-head">' +
        '<div class="breadcrumb"><a href="/">公开目录</a> / ' +
          '<a href="/#' + App.esc(note.topicId) + '">' + App.esc(note.topicTitle) + '</a></div>' +
        '<h2>' + App.esc(note.title) + '</h2>' +
        '<div class="note-meta">更新于 ' + App.esc(note.updatedAt) +
          (note.summary ? ' · ' + App.esc(note.summary) : '') + '</div>' +
      '</div>' +
      (toc ? '<nav class="toc"><strong>章节目录</strong><ol>' + toc + '</ol></nav>' : '') +
      (note.content ? '<section class="panel markdown"><h2>笔记正文</h2>' + App.renderMarkdown(note.content) + '</section>' : '') +
      (chapters ? '<section class="panel"><h2>分章笔记</h2>' + chapters + '</section>' : '') +
      (refs ? '<section class="panel refs"><h2>引用说明</h2>' + refs + '</section>' : '');
  }

  App.api.publicNote(id).then(render).catch(function (e) {
    if (e.status === 404) {
      root.innerHTML = '<div class="panel"><div class="empty">这篇笔记不存在或尚未公开。<br><a href="/">返回公开目录</a></div></div>';
    } else {
      root.innerHTML = '<div class="panel"><div class="empty">加载失败：' + App.esc(e.message) + '</div></div>';
    }
  });
})();
