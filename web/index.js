(function () {
  "use strict";
  var App = window.NotesApp;
  var root = document.getElementById("catalog");

  function render(catalog) {
    var topics = catalog.topics || [];
    if (!topics.length) {
      root.innerHTML = '<div class="panel"><div class="empty">暂时还没有公开笔记。</div></div>';
      return;
    }
    var html = topics.map(function (t) {
      var items = (t.notes || []).map(function (n) {
        return '<li>' +
          '<a class="note-title" href="/note/' + encodeURIComponent(n.id) + '">' + App.esc(n.title) + '</a>' +
          '<div class="note-meta">' +
            (n.chapterCount ? n.chapterCount + ' 个章节 · ' : '') +
            (n.referenceCount ? n.referenceCount + ' 条引用 · ' : '') +
            '更新于 ' + App.esc(n.updatedAt) +
          '</div>' +
          (n.summary ? '<p class="note-summary">' + App.esc(n.summary) + '</p>' : '') +
          '</li>';
      }).join("");
      return '<section class="panel">' +
        '<h2 class="topic-title">' + App.esc(t.title) +
          '<span class="count">（' + (t.notes || []).length + ' 篇公开笔记）</span></h2>' +
        (t.description ? '<p class="topic-desc">' + App.esc(t.description) + '</p>' : '') +
        '<ul class="note-list">' + items + '</ul>' +
        '</section>';
    }).join("");
    html += '<p class="muted">目录生成时间：' + App.esc(catalog.generatedAt) + '</p>';
    root.innerHTML = html;
  }

  App.api.catalog().then(render).catch(function (e) {
    root.innerHTML = '<div class="panel"><div class="empty">目录加载失败：' + App.esc(e.message) + '</div></div>';
  });
})();
