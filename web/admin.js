/* 研究员后台：课题与笔记的增删改、章节/引用编辑、公开开关 */
(function () {
  "use strict";
  var App = window.NotesApp;
  var esc = App.esc;

  var state = {
    topics: [],
    notes: [],
    currentNoteId: null,
    currentTopicId: null,
    // 编辑器中暂存的章节与引用
    chapters: [],
    references: []
  };

  var noteListEl = document.getElementById("note-list");
  var topicListEl = document.getElementById("topic-list");
  var noteEditorEl = document.getElementById("note-editor");
  var topicEditorEl = document.getElementById("topic-editor");

  // ---------------- 标签切换 ----------------

  var btnNotes = document.getElementById("tab-notes");
  var btnTopics = document.getElementById("tab-topics");
  var viewNotes = document.getElementById("view-notes");
  var viewTopics = document.getElementById("view-topics");

  function showTab(which) {
    var notesOn = which === "notes";
    viewNotes.hidden = !notesOn;
    viewTopics.hidden = notesOn;
    btnNotes.className = notesOn ? "" : "secondary";
    btnTopics.className = notesOn ? "secondary" : "";
  }
  btnNotes.addEventListener("click", function () { showTab("notes"); });
  btnTopics.addEventListener("click", function () { showTab("topics"); });

  // ================= 课题管理 =================

  function renderTopicList() {
    if (!state.topics.length) {
      topicListEl.innerHTML = '<div class="empty">还没有课题。</div>';
      return;
    }
    topicListEl.innerHTML = state.topics.map(function (t) {
      return '<div class="row' + (t.id === state.currentTopicId ? " active" : "") + '" data-id="' + esc(t.id) + '">' +
        '<div class="t">' + esc(t.title) + '</div>' +
        '<div class="m">' + (t.publicCount || 0) + ' 公开 / 共 ' + (t.noteCount || 0) + ' 篇</div>' +
        '</div>';
    }).join("");
    Array.prototype.forEach.call(topicListEl.querySelectorAll(".row"), function (row) {
      row.addEventListener("click", function () {
        state.currentTopicId = row.getAttribute("data-id");
        renderTopicList();
        renderTopicEditor();
      });
    });
  }

  function renderTopicEditor() {
    var t = state.topics.filter(function (x) { return x.id === state.currentTopicId; })[0];
    if (!t) {
      topicEditorEl.innerHTML = '<div class="empty">请选择左侧课题，或新建一个。</div>';
      return;
    }
    topicEditorEl.innerHTML =
      '<label>课题标题</label>' +
      '<input type="text" id="t-title" value="' + esc(t.title) + '">' +
      '<label>课题简介</label>' +
      '<textarea id="t-desc" rows="4">' + esc(t.description || "") + '</textarea>' +
      '<p class="muted">创建于 ' + esc(t.createdAt || "") + '，最近更新 ' + esc(t.updatedAt || "") +
      '；该课题共 ' + (t.noteCount || 0) + ' 篇笔记。</p>' +
      '<div class="toolbar" style="margin-top:16px">' +
        '<button id="t-save">保存课题</button>' +
        '<button id="t-delete" class="danger">删除课题</button>' +
      '</div>';

    document.getElementById("t-save").addEventListener("click", async function () {
      try {
        await App.api.updateTopic(t.id, {
          title: val("t-title"),
          description: val("t-desc")
        });
        App.toast("课题已保存");
        await reloadAll();
      } catch (e) {
        App.toast(e.message, true);
      }
    });
    document.getElementById("t-delete").addEventListener("click", async function () {
      if (!window.confirm("确认删除课题「" + t.title + "」？课题下仍有笔记时会被拒绝。")) {
        return;
      }
      try {
        await App.api.deleteTopic(t.id);
        state.currentTopicId = null;
        App.toast("课题已删除");
        await reloadAll();
      } catch (e) {
        App.toast(e.message, true);
      }
    });
  }

  function blankTopicEditor() {
    topicEditorEl.innerHTML =
      '<h3>新建课题</h3>' +
      '<label>课题标题</label><input type="text" id="t-title" placeholder="例如：计算生物学">' +
      '<label>课题简介</label><textarea id="t-desc" rows="4"></textarea>' +
      '<div class="toolbar" style="margin-top:16px"><button id="t-save">创建课题</button></div>';
    document.getElementById("t-save").addEventListener("click", async function () {
      try {
        var created = await App.api.createTopic({ title: val("t-title"), description: val("t-desc") });
        state.currentTopicId = created.id;
        App.toast("课题已创建");
        await reloadAll();
      } catch (e) {
        App.toast(e.message, true);
      }
    });
  }

  document.getElementById("new-topic").addEventListener("click", function () {
    state.currentTopicId = null;
    renderTopicList();
    blankTopicEditor();
  });

  // ================= 笔记列表 =================

  function renderNoteList() {
    if (!state.notes.length) {
      noteListEl.innerHTML = '<div class="empty">还没有笔记，点击上方按钮新建。</div>';
      return;
    }
    noteListEl.innerHTML = state.notes.map(function (n) {
      var badge = n.isPublic
        ? '<span class="badge public">公开</span>'
        : '<span class="badge private">私密</span>';
      return '<div class="row' + (n.id === state.currentNoteId ? " active" : "") + '" data-id="' + esc(n.id) + '">' +
        '<div class="t">' + badge + ' ' + esc(n.title) + '</div>' +
        '<div class="m">' + esc(n.topicTitle) + ' · 更新 ' + esc(n.updatedAt) + '</div>' +
        '</div>';
    }).join("");
    Array.prototype.forEach.call(noteListEl.querySelectorAll(".row"), function (row) {
      row.addEventListener("click", function () {
        selectNote(row.getAttribute("data-id"));
      });
    });
  }

  function selectNote(id) {
    state.currentNoteId = id;
    var n = state.notes.filter(function (x) { return x.id === id; })[0];
    state.chapters = (n && n.chapters ? n.chapters : []).map(clone);
    state.references = (n && n.references ? n.references : []).map(clone);
    renderNoteList();
    renderNoteEditor();
  }

  // ================= 笔记编辑器 =================

  function renderNoteEditor() {
    var n = state.notes.filter(function (x) { return x.id === state.currentNoteId; })[0];
    if (!n) {
      noteEditorEl.innerHTML = '<div class="empty">请选择左侧笔记，或新建一篇。</div>';
      return;
    }
    var options = state.topics.map(function (t) {
      return '<option value="' + esc(t.id) + '"' + (t.id === n.topicId ? " selected" : "") + '>' +
        esc(t.title) + '</option>';
    }).join("");

    noteEditorEl.innerHTML =
      '<div class="toolbar" style="justify-content:space-between">' +
        '<h3 style="margin:0">编辑笔记 ' + esc(n.id) + '</h3>' +
        '<div>' +
          (n.isPublic ? '<a class="btn" target="_blank" href="/note/' + encodeURIComponent(n.id) + '">查看公开页</a> ' : '') +
          '<button id="n-delete" class="danger tiny">删除</button>' +
        '</div>' +
      '</div>' +
      '<label>所属课题</label><select id="n-topic">' + options + '</select>' +
      '<label>标题</label><input type="text" id="n-title" value="' + esc(n.title) + '">' +
      '<label>一句话摘要</label><input type="text" id="n-summary" value="' + esc(n.summary || "") + '">' +
      '<label>正文（支持简单 Markdown：标题、列表、引用、加粗、链接、代码块、任务项）</label>' +
      '<textarea id="n-content" rows="10">' + esc(n.content || "") + '</textarea>' +
      '<h3 style="margin-top:18px">笔记章节</h3><div id="chapter-boxes"></div>' +
      '<button id="add-chapter" class="secondary tiny">＋ 添加章节</button>' +
      '<h3 style="margin-top:18px">引用说明</h3><div id="ref-boxes"></div>' +
      '<button id="add-ref" class="secondary tiny">＋ 添加引用</button>' +
      '<div class="switch-row" style="margin-top:20px">' +
        '<input type="checkbox" id="n-public"' + (n.isPublic ? " checked" : "") + '>' +
        '<label for="n-public">公开发布（取消勾选则立刻从公开目录与阅读页撤下）</label>' +
      '</div>' +
      '<div class="toolbar" style="margin-top:16px">' +
        '<button id="n-save">保存笔记</button>' +
        '<span class="muted">最近更新：' + esc(n.updatedAt || "") + '</span>' +
      '</div>';

    document.getElementById("add-chapter").addEventListener("click", function () {
      state.chapters.push({ heading: "", anchor: "", body: "" });
      renderChapterBoxes();
    });
    document.getElementById("add-ref").addEventListener("click", function () {
      state.references.push({ type: "期刊论文", title: "", authors: "", year: "", url: "", note: "" });
      renderRefBoxes();
    });
    document.getElementById("n-save").addEventListener("click", saveNote);
    document.getElementById("n-delete").addEventListener("click", deleteNote);

    renderChapterBoxes();
    renderRefBoxes();
  }

  function renderChapterBoxes() {
    var box = document.getElementById("chapter-boxes");
    box.innerHTML = state.chapters.map(function (c, i) {
      return '<div class="sub-box" data-i="' + i + '">' +
        '<div class="grid2">' +
          '<div><h4>章节 ' + (i + 1) + ' 标题</h4>' +
          '<input type="text" data-f="heading" value="' + esc(c.heading) + '" placeholder="例如：研究背景"></div>' +
          '<div><h4>锚点（可留空）</h4>' +
          '<input type="text" data-f="anchor" value="' + esc(c.anchor || "") + '" placeholder="chap-1"></div>' +
        '</div>' +
        '<h4 style="margin-top:8px">章节内容（Markdown）</h4>' +
        '<textarea data-f="body" rows="5">' + esc(c.body || "") + '</textarea>' +
        '<div class="toolbar" style="margin:6px 0 0"><button class="danger tiny" data-del="chapter">删除本章节</button></div>' +
        '</div>';
    }).join("");
    bindSubBoxes(box, state.chapters);
  }

  function renderRefBoxes() {
    var box = document.getElementById("ref-boxes");
    box.innerHTML = state.references.map(function (r, i) {
      return '<div class="sub-box" data-i="' + i + '">' +
        '<div class="grid2">' +
          '<div><h4>类型</h4>' +
            '<select data-f="type">' +
              ["期刊论文", "会议论文", "教材", "网页", "数据集", "其他"].map(function (tp) {
                return '<option' + (tp === r.type ? " selected" : "") + '>' + esc(tp) + '</option>';
              }).join("") +
            '</select></div>' +
          '<div><h4>年份</h4><input type="text" data-f="year" value="' + esc(r.year || "") + '"></div>' +
        '</div>' +
        '<h4 style="margin-top:8px">标题</h4><input type="text" data-f="title" value="' + esc(r.title || "") + '">' +
        '<h4>作者 / 机构</h4><input type="text" data-f="authors" value="' + esc(r.authors || "") + '">' +
        '<h4>链接</h4><input type="text" data-f="url" value="' + esc(r.url || "") + '" placeholder="https://...">' +
        '<h4>引用说明</h4><textarea data-f="note" rows="2">' + esc(r.note || "") + '</textarea>' +
        '<div class="toolbar" style="margin:6px 0 0"><button class="danger tiny" data-del="ref">删除本引用</button></div>' +
        '</div>';
    }).join("");
    bindSubBoxes(box, state.references);
  }

  function bindSubBoxes(box, store) {
    Array.prototype.forEach.call(box.querySelectorAll(".sub-box"), function (sub) {
      var i = Number(sub.getAttribute("data-i"));
      Array.prototype.forEach.call(sub.querySelectorAll("[data-f]"), function (input) {
        var field = input.getAttribute("data-f");
        var evt = input.tagName === "TEXTAREA" || input.tagName === "SELECT" ? "change" : "input";
        input.addEventListener(evt, function () {
          store[i][field] = input.value;
        });
        if (input.tagName === "TEXTAREA" || input.tagName === "SELECT") {
          input.addEventListener("input", function () { store[i][field] = input.value; });
        }
      });
      var del = sub.querySelector("[data-del]");
      if (del) {
        del.addEventListener("click", function () {
          var kind = del.getAttribute("data-del");
          store.splice(i, 1);
          if (kind === "chapter") {
            renderChapterBoxes();
          } else {
            renderRefBoxes();
          }
        });
      }
    });
  }

  async function saveNote() {
    var id = state.currentNoteId;
    try {
      var body = {
        topicId: val("n-topic"),
        title: val("n-title"),
        summary: val("n-summary"),
        content: val("n-content"),
        chapters: state.chapters,
        references: state.references,
        isPublic: document.getElementById("n-public").checked
      };
      await App.api.updateNote(id, body);
      App.toast(body.isPublic ? "已保存并公开发布" : "已保存（私密，不会出现在公开目录）");
      await reloadAll(id);
    } catch (e) {
      App.toast(e.message, true);
    }
  }

  async function deleteNote() {
    var n = state.notes.filter(function (x) { return x.id === state.currentNoteId; })[0];
    if (!window.confirm("确认删除笔记「" + n.title + "」？此操作不可恢复。")) {
      return;
    }
    try {
      await App.api.deleteNote(n.id);
      state.currentNoteId = null;
      App.toast("笔记已删除");
      await reloadAll();
    } catch (e) {
      App.toast(e.message, true);
    }
  }

  function blankNoteEditor() {
    noteEditorEl.innerHTML =
      '<h3>新建笔记</h3>' +
      '<label>所属课题</label><select id="n-topic">' +
        state.topics.map(function (t) {
          return '<option value="' + esc(t.id) + '">' + esc(t.title) + '</option>';
        }).join("") + '</select>' +
      '<label>标题</label><input type="text" id="n-title" placeholder="请输入笔记标题">' +
      '<label>一句话摘要</label><input type="text" id="n-summary">' +
      '<label>正文（Markdown）</label><textarea id="n-content" rows="10"></textarea>' +
      '<div class="switch-row" style="margin-top:14px">' +
        '<input type="checkbox" id="n-public" disabled><label for="n-public">新笔记默认私密，保存后再勾选公开</label>' +
      '</div>' +
      '<div class="toolbar" style="margin-top:16px"><button id="n-create">创建笔记</button></div>' +
      '<p class="muted">章节和引用可在创建后继续编辑。</p>';
    document.getElementById("n-create").addEventListener("click", async function () {
      if (!state.topics.length) {
        App.toast("请先在“课题管理”里创建一个课题", true);
        showTab("topics");
        return;
      }
      try {
        var created = await App.api.createNote({
          topicId: val("n-topic"),
          title: val("n-title"),
          summary: val("n-summary"),
          content: val("n-content"),
          chapters: [],
          references: []
        });
        App.toast("已创建（默认私密）");
        await reloadAll(created.id);
      } catch (e) {
        App.toast(e.message, true);
      }
    });
  }

  document.getElementById("new-note").addEventListener("click", function () {
    if (!state.topics.length) {
      App.toast("请先创建至少一个课题", true);
      showTab("topics");
      return;
    }
    state.currentNoteId = null;
    renderNoteList();
    blankNoteEditor();
  });

  // ---------------- 通用 ----------------

  function val(id) {
    var el = document.getElementById(id);
    return el ? el.value : "";
  }

  function clone(o) {
    return JSON.parse(JSON.stringify(o));
  }

  async function reloadAll(selectNoteId) {
    var ts = await App.api.listTopics();
    var ns = await App.api.listNotes();
    state.topics = ts.topics || [];
    state.notes = ns.notes || [];
    if (selectNoteId) {
      state.currentNoteId = selectNoteId;
    } else if (state.currentNoteId && !state.notes.some(function (x) { return x.id === state.currentNoteId; })) {
      state.currentNoteId = null;
    }
    if (state.currentTopicId && !state.topics.some(function (x) { return x.id === state.currentTopicId; })) {
      state.currentTopicId = null;
    }
    renderTopicList();
    renderNoteList();
    if (state.currentNoteId) {
      selectNote(state.currentNoteId);
    } else {
      noteEditorEl.innerHTML = '<div class="empty">请选择左侧笔记，或新建一篇。</div>';
    }
    if (state.currentTopicId) {
      renderTopicEditor();
    } else {
      topicEditorEl.innerHTML = '<div class="empty">请选择左侧课题，或新建一个。</div>';
    }
  }

  reloadAll().catch(function (e) {
    App.toast("初始化失败：" + e.message, true);
  });
})();
