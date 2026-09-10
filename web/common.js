/* 公共工具：接口请求、HTML 转义、轻量 Markdown 渲染（先转义再渲染，防止 XSS） */
(function (global) {
  "use strict";

  async function request(method, url, body) {
    var opt = { method: method, headers: {} };
    if (body !== undefined) {
      opt.headers["Content-Type"] = "application/json; charset=utf-8";
      opt.body = JSON.stringify(body);
    }
    var resp = await fetch(url, opt);
    var text = await resp.text();
    var data = null;
    if (text) {
      try {
        data = JSON.parse(text);
      } catch (e) {
        throw new Error("服务器返回了无法解析的内容");
      }
    }
    if (!resp.ok) {
      var msg = (data && data.error) || ("请求失败 HTTP " + resp.status);
      var err = new Error(msg);
      err.status = resp.status;
      throw err;
    }
    return data;
  }

  var api = {
    catalog: function () {
      return request("GET", "/api/catalog");
    },
    publicNote: function (id) {
      return request("GET", "/api/notes/" + encodeURIComponent(id));
    },
    listTopics: function () {
      return request("GET", "/api/admin/topics");
    },
    createTopic: function (b) {
      return request("POST", "/api/admin/topics", b);
    },
    updateTopic: function (id, b) {
      return request("PUT", "/api/admin/topics/" + encodeURIComponent(id), b);
    },
    deleteTopic: function (id) {
      return request("DELETE", "/api/admin/topics/" + encodeURIComponent(id));
    },
    listNotes: function () {
      return request("GET", "/api/admin/notes");
    },
    createNote: function (b) {
      return request("POST", "/api/admin/notes", b);
    },
    updateNote: function (id, b) {
      return request("PUT", "/api/admin/notes/" + encodeURIComponent(id), b);
    },
    deleteNote: function (id) {
      return request("DELETE", "/api/admin/notes/" + encodeURIComponent(id));
    }
  };

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function safeUrl(url) {
    var u = String(url).trim();
    if (/^(https?:\/\/|mailto:|\/|#)/i.test(u)) {
      return u.replace(/"/g, "%22");
    }
    return "#";
  }

  /* 行内：代码、加粗、斜体、链接；输入已被整体转义 */
  function inline(text) {
    var codes = [];
    text = text.replace(/`([^`]+)`/g, function (_, c) {
      codes.push(c);
      return "@@CODE" + (codes.length - 1) + "@@";
    });
    text = text.replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g, function (_, alt, url) {
      return '<img alt="' + alt + '" src="' + safeUrl(url) + '" style="max-width:100%">';
    });
    text = text.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, function (_, label, url) {
      return '<a href="' + safeUrl(url) + '" target="_blank" rel="noopener noreferrer">' + label + "</a>";
    });
    text = text.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
    text = text.replace(/(^|[\s(])\*([^*\n]+)\*/g, "$1<em>$2</em>");
    text = text.replace(/@@CODE(\d+)@@/g, function (_, i) {
      return "<code>" + codes[Number(i)] + "</code>";
    });
    return text;
  }

  /* 支持：标题、无序/有序列表、引用、围栏代码块、段落、任务项 */
  function renderMarkdown(src) {
    if (!src) {
      return "";
    }
    var escaped = esc(src);
    var lines = escaped.replace(/\r\n/g, "\n").split("\n");
    var html = [];
    var i = 0;

    function flushList(items, ordered) {
      html.push(ordered ? "<ol>" : "<ul>");
      items.forEach(function (it) {
        var task = it.match(/^\s*-\s*\[( |x)\]\s+(.*)$/i);
        if (task) {
          var checked = task[1].toLowerCase() === "x" ? " checked" : "";
          html.push("<li><input type='checkbox' disabled" + checked + "> " + inline(task[2]) + "</li>");
        } else {
          html.push("<li>" + inline(it.replace(/^\s*[-*]\s+/, "").replace(/^\s*\d+\.\s+/, "")) + "</li>");
        }
      });
      html.push(ordered ? "</ol>" : "</ul>");
    }

    while (i < lines.length) {
      var line = lines[i];

      if (/^```/.test(line)) {
        var buf = [];
        i++;
        while (i < lines.length && !/^```/.test(lines[i])) {
          buf.push(lines[i]);
          i++;
        }
        i++;
        html.push("<pre><code>" + buf.join("\n") + "</code></pre>");
        continue;
      }

      var h = line.match(/^(#{1,4})\s+(.*)$/);
      if (h) {
        var level = h[1].length + 1;
        html.push("<h" + level + ">" + inline(h[2]) + "</h" + level + ">");
        i++;
        continue;
      }

      if (/^\s*[-*]\s+/.test(line) || /^\s*\d+\.\s+/.test(line)) {
        var items = [];
        var ordered = /^\s*\d+\.\s+/.test(line);
        while (i < lines.length && (/^\s*[-*]\s+/.test(lines[i]) || /^\s*\d+\.\s+/.test(lines[i]))) {
          items.push(lines[i]);
          i++;
        }
        flushList(items, ordered);
        continue;
      }

      if (/^>\s?/.test(line)) {
        var q = [];
        while (i < lines.length && /^>\s?/.test(lines[i])) {
          q.push(lines[i].replace(/^>\s?/, ""));
          i++;
        }
        html.push("<blockquote>" + inline(q.join("<br>")) + "</blockquote>");
        continue;
      }

      if (line.trim() === "") {
        i++;
        continue;
      }

      var para = [];
      while (i < lines.length && lines[i].trim() !== "" &&
             !/^(#{1,4})\s/.test(lines[i]) && !/^\s*[-*]\s/.test(lines[i]) &&
             !/^\s*\d+\.\s/.test(lines[i]) && !/^>/.test(lines[i]) && !/^```/.test(lines[i])) {
        para.push(lines[i]);
        i++;
      }
      html.push("<p>" + inline(para.join("<br>")) + "</p>");
    }
    return html.join("\n");
  }

  function toast(message, isError) {
    var el = document.getElementById("toast");
    if (!el) {
      window.alert(message);
      return;
    }
    el.textContent = message;
    el.className = "toast show" + (isError ? " error" : "");
    clearTimeout(toast._t);
    toast._t = setTimeout(function () {
      el.className = "toast";
    }, 2600);
  }

  global.NotesApp = {
    api: api,
    esc: esc,
    renderMarkdown: renderMarkdown,
    toast: toast
  };
})(window);
