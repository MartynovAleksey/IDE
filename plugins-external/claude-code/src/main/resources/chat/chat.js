'use strict';

// ---- markdown: everything is escaped first, only the tags below are produced ----

function esc(s) {
  return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

function inline(s) {
  return s.split(/(`[^`\n]+`)/).map((part, i) => i % 2
    ? '<code>' + esc(part.slice(1, -1)) + '</code>'
    : esc(part)
      .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
      .replace(/(^|[\s(])\*([^*\s][^*]*?)\*(?=[\s).,:;!?]|$)/g, '$1<i>$2</i>')
      .replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a data-url="$2">$1</a>')
  ).join('');
}

const cells = l => l.trim().replace(/^\||\|$/g, '').split('|').map(c => c.trim());

// shortcut: no nested lists or blockquotes; add them when answers need them
function md(src) {
  const out = [], lines = String(src).split('\n');
  let para = [], list = null;
  const flush = () => {
    if (para.length) out.push('<p>' + para.map(inline).join('<br>') + '</p>');
    if (list) out.push(`<${list.tag}>` + list.items.map(x => '<li>' + inline(x) + '</li>').join('') + `</${list.tag}>`);
    para = []; list = null;
  };
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i];
    let m;
    if (/^\s*```/.test(l)) {
      flush();
      const code = [];
      while (++i < lines.length && !/^\s*```/.test(lines[i])) code.push(lines[i]);
      out.push('<pre><button class="copy">Copy</button><code>' + esc(code.join('\n')) + '</code></pre>');
    }
    else if (/^\s*\|/.test(l) && /^\s*\|?[\s:|-]+\|?\s*$/.test(lines[i + 1] || '') && lines[i + 1].includes('-')) {
      flush();
      const head = cells(l), rows = [];
      for (i += 2; i < lines.length && /^\s*\|/.test(lines[i]); i++) rows.push(cells(lines[i]));
      i--;
      out.push('<table><tr>' + head.map(c => '<th>' + inline(c) + '</th>').join('') + '</tr>'
        + rows.map(r => '<tr>' + r.map(c => '<td>' + inline(c) + '</td>').join('') + '</tr>').join('') + '</table>');
    }
    else if ((m = l.match(/^(#{1,6})\s+(.*)/))) { flush(); out.push(`<h${m[1].length}>${inline(m[2])}</h${m[1].length}>`); }
    else if ((m = l.match(/^\s*([-*]|\d+\.)\s+(.*)/))) {
      const tag = /\d/.test(m[1]) ? 'ol' : 'ul';
      if (para.length || (list && list.tag !== tag)) flush();
      (list = list || { tag, items: [] }).items.push(m[2]);
    }
    else if (!l.trim()) flush();
    else { if (list) flush(); para.push(l); }
  }
  flush();
  return out.join('');
}

/** The answer to an AskUserQuestion: the tool's input with `answers` {question: chosen labels or typed text}. */
function askAnswer(input, chosen) {
  const answers = {};
  (input.questions || []).forEach((q, i) => { answers[q.question] = (chosen[i] || []).filter(Boolean).join(', '); });
  return Object.assign({}, input, { answers });
}

if (typeof module !== 'undefined') module.exports = { md, esc, askAnswer };

// ---- chat ----

if (typeof document !== 'undefined') {
  const $ = id => document.getElementById(id);
  const log = $('log'), input = $('input'), sendButton = $('send'), menu = $('menu'), attachments = $('attachments');
  const modeButton = $('mode'), modelButton = $('model'), popup = $('popup'), ctx = $('ctx'), where = $('where'), todos = $('todos');
  const MODES = [['default', 'Ask before edits'], ['acceptEdits', 'Accept edits'], ['plan', 'Plan mode'], ['auto', 'Auto'], ['bypassPermissions', 'Bypass permissions']];
  let mode = 'default', model = 'default', models = [], popupItems = [], popupIndex = 0, popupPick = null;
  const cards = {}, pending = {}, tasks = new Map(), creating = {};
  let thinking = null, workStart = 0, workTimer = null, diagnostics = [], live = null, busy = false, cwd = '', commands = [], files = null, images = [], titled = false, requestId = 0;
  let menuItems = [], menuIndex = 0, menuToken = null;

  const send = o => ide('stdin:' + JSON.stringify(o));
  const control = request => {
    const id = 'ide-' + (++requestId);
    send({ type: 'control_request', request_id: id, request });
    return new Promise(resolve => { pending[id] = resolve; });
  };
  const atBottom = () => log.scrollHeight - log.scrollTop - log.clientHeight < 60;
  const rel = s => cwd && String(s).startsWith(cwd + '/') ? String(s).slice(cwd.length + 1) : String(s);

  /** The element new output of [o] goes into: a subagent's output nests in its Task card. */
  function target(o) {
    const parent = o && o.parent_tool_use_id && cards[o.parent_tool_use_id];
    if (!parent) return log;
    const row = parent.querySelector(':scope > details.row') || parent;
    let sub = row.querySelector(':scope > .sub');
    if (!sub) {
      sub = document.createElement('div');
      sub.className = 'sub';
      row.insertBefore(sub, row.querySelector(':scope > .res'));
    }
    return sub;
  }
  function add(cls, html, into = log) {
    const empty = $('empty');
    if (empty) empty.remove();
    const stick = atBottom(), el = document.createElement('div');
    el.className = cls; el.innerHTML = html; into.appendChild(el);
    if (stick) log.scrollTop = log.scrollHeight;
    return el;
  }
  /** The line above the input while claude works: what it does and for how long, like the CLI's spinner. */
  function working(label) {
    $('work-label').textContent = label + '…';
    if (!workTimer) { workStart = Date.now(); workTimer = setInterval(tick, 1000); }
    tick(); $('working').hidden = false;
  }
  function tick() { $('work-time').textContent = Math.floor((Date.now() - workStart) / 1000) + 's'; }
  function idle() { clearInterval(workTimer); workTimer = null; $('working').hidden = true; }
  function setBusy(b) {
    if (b !== busy) { if (b) working('Thinking'); else idle(); }
    busy = b; sendButton.textContent = b && !input.value.trim() ? 'Stop' : 'Send'; sendButton.classList.toggle('primary', !b || !!input.value.trim()); }

  // ---- tools ----

  /** mcp__server__tool as "server · tool". */
  const toolName = n => String(n).startsWith('mcp__') ? n.slice(5).split('__').join(' · ') : String(n);
  const summary = i => rel((i && (i.command || i.file_path || i.notebook_path || i.pattern || i.url || i.query || i.description || i.prompt)) || '');
  const diff = (a = '', b = '') => '<pre class="diff">' + a.split('\n').map(l => '<del>- ' + esc(l) + '</del>').join('')
    + b.split('\n').map(l => '<ins>+ ' + esc(l) + '</ins>').join('') + '</pre>';
  function detail(name, i) {
    if (name === 'Edit') return diff(i.old_string, i.new_string) + '<button class="ide-diff">Open diff</button>';
    if (name === 'MultiEdit' && i.edits) return i.edits.map(e => diff(e.old_string, e.new_string)).join('');
    if (name === 'Write') return '<details><summary>Content</summary><pre>' + esc(i.content || '') + '</pre></details>';
    return '';
  }
  function bindDetail(el, name, i) {
    const button = el.querySelector('.ide-diff');
    if (button) button.onclick = () => ide('diff:' + JSON.stringify({ path: i.file_path, old: i.old_string || '', new: i.new_string || '' }));
  }
  function toolCard(b, into) {
    const i = b.input || {};
    // task lists: TodoWrite sends the whole list; TaskCreate/TaskUpdate change one task, whose id comes in TaskCreate's result
    if (b.name === 'TodoWrite') { showTodos(i.todos || []); return add('note', 'Updated tasks<div class="res"></div>', into); }
    if (b.name === 'TaskCreate') { creating[b.id] = i; return add('note', 'New task: ' + esc(i.subject || '') + '<div class="res"></div>', into); }
    if (b.name === 'TaskUpdate') {
      const id = String(i.taskId), t = tasks.get(id);
      if (t) { Object.assign(t, i.status ? { status: i.status } : {}, i.subject ? { content: i.subject } : {}); if (i.status === 'deleted') tasks.delete(id); showTodos([...tasks.values()]); }
      return add('note', 'Task ' + esc(id) + (i.status ? ': ' + esc(i.status.replace('_', ' ')) : '') + '<div class="res"></div>', into);
    }
    // one line that opens to the result (and a subagent's work); edits show their diff below it
    const el = add('tool', `<details class="row"><summary><span class="name">${esc(toolName(b.name))}</span> <span class="sum">${esc(summary(i))}</span></summary><div class="res"></div></details>`
      + detail(b.name, i), into);
    if (i.file_path) {
      const s = el.querySelector('.sum');
      s.classList.add('link');
      s.onclick = e => { e.preventDefault(); e.stopPropagation(); ide('open:' + i.file_path); };
    }
    bindDetail(el, b.name, i);
    return el;
  }
  function toolResult(b) {
    const el = cards[b.tool_use_id];
    if (!el) return;
    const text = typeof b.content === 'string' ? b.content : (b.content || []).map(c => c.text || '').join('\n');
    const created = creating[b.tool_use_id], id = !b.is_error && created && text.match(/#(\d+)/);
    if (id) { tasks.set(id[1], { content: created.subject, activeForm: created.activeForm, status: 'pending' }); showTodos([...tasks.values()]); }
    el.classList.add(b.is_error ? 'failed' : 'done');
    if (el.classList.contains('note')) return;
    const res = el.querySelector('.row > .res');
    if (res) res.innerHTML = text ? `<pre>${esc(text)}</pre>` : '<div class="empty-res">No output</div>';
  }
  function showTodos(list) {
    todos.hidden = !list.length;
    const done = list.filter(t => t.status === 'completed').length;
    todos.innerHTML = `<details open><summary>Tasks ${done}/${list.length}</summary><ul>`
      + list.map(t => `<li class="${esc(t.status)}">${esc(t.status === 'in_progress' && t.activeForm ? t.activeForm : t.content)}</li>`).join('') + '</ul></details>';
  }

  // ---- permission prompts, questions, plans ----

  function answer(el, o, response, text) {
    send({ type: 'control_response', response: { subtype: 'success', request_id: o.request_id, response } });
    if (busy) working('Thinking');
    el.querySelectorAll('button, input').forEach(b => { b.disabled = true; });
    el.querySelector('.btns').textContent = text;
    input.focus();
  }
  const deny = message => ({ behavior: 'deny', message });

  function permission(o) {
    const r = o.request, i = r.input || {};
    if (r.tool_name === 'AskUserQuestion' && i.questions) return question(o);
    if (r.tool_name === 'ExitPlanMode') {
      const el = add('perm', '<b>Plan</b><div class="msg">' + md(i.plan || '') + '</div>'
        + '<div class="btns"><button class="primary" data-a="accept">Approve and accept edits</button><button data-a="approve">Approve</button><button data-a="keep">Keep planning</button></div>');
      el.querySelector('[data-a=accept]').onclick = () => { answer(el, o, { behavior: 'allow', updatedInput: i }, 'Approved'); setMode('acceptEdits'); };
      el.querySelector('[data-a=approve]').onclick = () => answer(el, o, { behavior: 'allow', updatedInput: i }, 'Approved');
      el.querySelector('[data-a=keep]').onclick = () => answer(el, o, deny('The user wants to keep planning. Ask what to change.'), 'Keep planning');
      el.querySelector('button').focus();
      return;
    }
    const always = r.permission_suggestions && r.permission_suggestions.length;
    const el = add('perm', `<div>Allow <b>${esc(toolName(r.display_name || r.tool_name))}</b>: <span class="sum">${esc(rel(r.description || summary(i)))}</span>?</div>`
      + (r.tool_use_id && cards[r.tool_use_id] ? '' : detail(r.tool_name, i))  // the tool's card above already shows it
      + `<div class="btns"><button class="primary" data-a="allow">Allow</button>${always ? '<button data-a="always">Allow for session</button>' : ''}<button data-a="deny">Deny</button></div>`, target(o));
    bindDetail(el, r.tool_name, i);
    el.querySelectorAll('.btns button').forEach(button => button.onclick = () => {
      const a = button.dataset.a;
      answer(el, o, a === 'deny' ? deny('The user denied this tool use.')
        : Object.assign({ behavior: 'allow', updatedInput: i }, a === 'always' ? { updatedPermissions: r.permission_suggestions } : {}),
        a === 'deny' ? 'Denied' : 'Allowed');
    });
    el.querySelector('.btns button').focus();
  }

  function question(o) {
    const i = o.request.input, chosen = i.questions.map(() => []);
    const el = add('perm', i.questions.map((q, n) => {
      const type = q.multiSelect ? 'checkbox' : 'radio';
      return (q.header ? `<div class="hdr">${esc(q.header)}</div>` : '') + `<div class="q">${esc(q.question)}</div>`
        + (q.options || []).map((opt, k) => `<label class="opt"><input type="${type}" name="q${n}" data-q="${n}" value="${k}"> ${esc(opt.label)}`
          + (opt.description ? ` <small>${esc(opt.description)}</small>` : '') + '</label>').join('')
        + `<input type="text" data-other="${n}" placeholder="Other…">`;
    }).join('') + '<div class="btns"><button class="primary" data-a="submit">Submit</button><button data-a="skip">Skip</button></div>');
    el.querySelector('[data-a=submit]').onclick = () => {
      i.questions.forEach((q, n) => {
        chosen[n] = [...el.querySelectorAll(`input[data-q="${n}"]:checked`)].map(box => q.options[+box.value].label);
        const other = el.querySelector(`[data-other="${n}"]`).value.trim();
        if (other) chosen[n].push(other);
      });
      answer(el, o, { behavior: 'allow', updatedInput: askAnswer(i, chosen) }, 'Answered');
    };
    el.querySelector('[data-a=skip]').onclick = () => answer(el, o, deny('The user skipped the question.'), 'Skipped');
  }

  // ---- protocol ----

  function userText(content) {
    if (typeof content === 'string') return content;
    return (content || []).filter(b => b.type === 'text').map(b => b.text).join('\n');
  }

  function handle(o, history) {
    switch (o.type) {
      case 'stream_event': {
        if (o.parent_tool_use_id) break;  // subagents show their complete messages in their Task
        const e = o.event, block = e.content_block || {}, d = e.delta || {};
        if (e.type === 'content_block_start') {
          if (block.type === 'text') { live = { el: add('msg', ''), text: '' }; working('Writing'); }
          else if (block.type === 'thinking') { thinking = { el: add('note thinking', '<details open><summary>Thinking…</summary><div></div></details>'), text: '' }; working('Thinking'); }
          else if (block.type === 'tool_use') working('Running ' + toolName(block.name));
        }
        else if (e.type === 'content_block_delta') {
          const stick = atBottom();
          if (d.type === 'text_delta' && live) { live.text += d.text; live.el.innerHTML = md(live.text); }
          else if (d.type === 'thinking_delta' && thinking) { thinking.text += d.thinking; thinking.el.querySelector('details > div').innerHTML = md(thinking.text); }
          if (stick) log.scrollTop = log.scrollHeight;
        }
        break;
      }
      case 'assistant':
        for (const b of (o.message && o.message.content) || []) {
          if (b.type === 'text') { if (live && !history) { live.el.innerHTML = md(b.text); live = null; } else add('msg', md(b.text), target(o)); }
          else if (b.type === 'thinking' && thinking && !history && !o.parent_tool_use_id) {
            // the streamed block is done: fold it like the CLI does
            const details = thinking.el.querySelector('details');
            details.open = false; details.querySelector('summary').textContent = 'Thinking';
            if (b.thinking) details.querySelector('div').innerHTML = md(b.thinking);
            thinking = null;
          }
          else if (b.type === 'thinking' && b.thinking) add('note thinking', '<details><summary>Thinking</summary><div>' + md(b.thinking) + '</div></details>', target(o));
          else if (b.type === 'tool_use') cards[b.id] = toolCard(b, target(o));
        }
        break;
      case 'user': {
        const content = o.message && o.message.content;
        if (Array.isArray(content)) content.filter(b => b.type === 'tool_result').forEach(toolResult);
        if (busy && !history && !o.parent_tool_use_id) working('Thinking');
        const text = history && !o.isMeta && userText(content).trim();
        if (text && !text.startsWith('<')) add('msg user', esc(text));
        break;
      }
      case 'control_request':
        if (o.request.subtype === 'can_use_tool') { permission(o); working('Waiting for you'); }
        break;
      case 'control_response': {
        const r = o.response, resolve = pending[r.request_id];
        if (resolve) { delete pending[r.request_id]; resolve(r.response || {}); }
        break;
      }
      case 'system':
        if (o.subtype === 'init') { cwd = o.cwd || ''; where.textContent = cwd.split('/').pop(); }
        if (o.subtype === 'compact_boundary') add('divider', '— conversation compacted —');
        if (o.permissionMode) { mode = o.permissionMode; renderPickers(); }
        break;
      case 'result':
        setBusy(false); live = null; thinking = null;
        if (String(o.terminal_reason).startsWith('aborted')) add('note', 'Stopped');
        else if (o.is_error) add('err', esc(o.result || (o.errors || []).join('\n') || o.subtype));
        refreshContext();
        break;
      case 'exit':
        setBusy(false);
        add('err', 'claude exited with code ' + o.code + '. Open a new chat with +.' + (diagnostics.length ? '\n\n' + esc(diagnostics.join('\n')) : ''));
        break;
    }
  }

  window.onClaude = (line, history) => {
    let o;
    // not JSON: claude's log (stderr) such as warnings about unknown model names; shown only if claude exits
    try { o = JSON.parse(line); } catch (e) { if (line.trim()) diagnostics = diagnostics.slice(-29).concat(line); return; }
    handle(o, history);
  };

  window.onStarted = async () => {
    const init = await control({ subtype: 'initialize' });
    commands = (init.commands || []).map(c => ({ label: '/' + c.name, hint: c.description || c.argumentHint || '' }));
    models = init.models || [];
    mode = init.current_permission_mode || mode;
    renderPickers();
    modeButton.hidden = modelButton.hidden = false;
    const settings = await control({ subtype: 'get_settings' });
    const current = settings.applied && settings.applied.model;
    const match = models.find(m => m.value === current) || models.find(m => m.resolvedModel === current);
    // a model claude does not list (a custom one behind ANTHROPIC_BASE_URL) is still the current one
    if (!match && current) models.push({ value: current, displayName: current });
    model = match ? match.value : current || model;
    renderPickers();
    refreshContext();
  };

  async function refreshContext() {
    const usage = await control({ subtype: 'get_context_usage' });
    if (usage && usage.maxTokens) ctx.textContent = Math.round(usage.percentage || 0) + '% context';
  }
  // ---- mode and model pickers: menus in the page, since JCEF cuts native <select> lists at the page's edge ----

  function renderPickers() {
    modeButton.textContent = (MODES.find(m => m[0] === mode) || [mode, mode])[1];
    const m = models.find(x => x.value === model);
    modelButton.textContent = m ? m.displayName || m.value : model;
  }
  function openPopup(button, items, current, onPick) {
    popupItems = items; popupPick = onPick;
    popupIndex = Math.max(0, items.findIndex(it => it[0] === current));
    popup.innerHTML = items.map(([v, label, hint], k) => `<div role="option" data-k="${k}" aria-selected="${v === current}">`
      + `<span class="check">${v === current ? '✓' : ''}</span>${esc(label)}${hint ? '<small>' + esc(hint) + '</small>' : ''}</div>`).join('');
    popup.style.left = button.offsetLeft + 'px';
    popup.hidden = false;
    highlightPopup();
  }
  function highlightPopup() { [...popup.children].forEach((d, k) => d.classList.toggle('sel', k === popupIndex)); }
  function pickPopup(k) { const it = popupItems[k]; popup.hidden = true; if (it) popupPick(it[0]); input.focus(); }
  popup.onmousedown = e => { const d = e.target.closest('[data-k]'); if (d) { e.preventDefault(); pickPopup(+d.dataset.k); } };
  function setMode(m) { mode = m; renderPickers(); control({ subtype: 'set_permission_mode', mode: m }); }
  modeButton.onclick = () => popup.hidden ? openPopup(modeButton, MODES, mode, setMode) : (popup.hidden = true);
  modelButton.onclick = () => popup.hidden
    ? openPopup(modelButton, models.map(m => [m.value, m.displayName || m.value, m.description]), model,
                v => { model = v; renderPickers(); control({ subtype: 'set_model', model: v }); })
    : (popup.hidden = true);
  document.addEventListener('mousedown', e => { if (!popup.contains(e.target) && !e.target.classList.contains('pick')) popup.hidden = true; });
  document.addEventListener('keydown', e => {
    if (popup.hidden) return;
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); popupIndex = (popupIndex + (e.key === 'ArrowDown' ? 1 : popupItems.length - 1)) % popupItems.length; highlightPopup(); }
    if (e.key === 'Enter') { e.preventDefault(); pickPopup(popupIndex); }
  });
  renderPickers();

  // ---- input: send, stop, / and @ completion, images ----

  function submit() {
    const text = input.value.trim();
    if (!text && !images.length) return;
    const content = images.length
      ? images.map(i => ({ type: 'image', source: { type: 'base64', media_type: i.type, data: i.data } })).concat(text ? [{ type: 'text', text }] : [])
      : text;
    add('msg user', images.map(i => `<img src="data:${esc(i.type)};base64,${i.data}" alt="">`).join('') + esc(text));
    if (!titled && text) { titled = true; ide('title:' + text.split('\n')[0]); }
    send({ type: 'user', message: { role: 'user', content } });
    input.value = ''; input.style.height = ''; images = []; attachments.innerHTML = '';
    closeMenu(); setBusy(true);
  }
  const stop = () => control({ subtype: 'interrupt' });

  function openMenu(items, token) {
    menuItems = items.slice(0, 50); menuIndex = 0; menuToken = token;
    if (!menuItems.length) return closeMenu();
    menu.innerHTML = menuItems.map((it, k) => `<div role="option" data-k="${k}">${esc(it.label)}${it.hint ? '<small>' + esc(it.hint) + '</small>' : ''}</div>`).join('');
    menu.hidden = false; highlight();
  }
  function closeMenu() { menu.hidden = true; menuItems = []; menuToken = null; }
  function highlight() {
    [...menu.children].forEach((d, k) => d.classList.toggle('sel', k === menuIndex));
    const sel = menu.children[menuIndex];
    if (sel) sel.scrollIntoView({ block: 'nearest' });
  }
  function pick(k) {
    const it = menuItems[k], t = menuToken;
    if (!it || !t) return;
    const insert = (t.kind === '@' ? '@' + it.value : it.label) + ' ';
    input.value = input.value.slice(0, t.start) + insert + input.value.slice(t.end);
    input.selectionStart = input.selectionEnd = t.start + insert.length;
    closeMenu(); input.focus();
  }
  menu.onmousedown = e => { const d = e.target.closest('[data-k]'); if (d) { e.preventDefault(); pick(+d.dataset.k); } };

  function complete() {
    const caret = input.selectionStart, before = input.value.slice(0, caret);
    let m = before.match(/^\/(\S*)$/);
    if (m) {
      const q = m[1].toLowerCase();
      const hits = commands.filter(c => !c.label.startsWith('/__') && c.label.toLowerCase().includes(q))
        .sort((a, b) => (a.label.toLowerCase().startsWith('/' + q) ? 0 : 1) - (b.label.toLowerCase().startsWith('/' + q) ? 0 : 1) || a.label.localeCompare(b.label));
      return openMenu(hits, { kind: '/', start: 0, end: caret });
    }
    m = before.match(/(^|\s)@(\S*)$/);
    if (m) {
      const start = caret - m[2].length - 1, q = m[2].toLowerCase();
      if (!files) { files = []; ide('files:'); }
      const hits = files.filter(f => f.toLowerCase().includes(q))
        .sort((a, b) => (a.toLowerCase().split('/').pop().startsWith(q) ? 0 : 1) - (b.toLowerCase().split('/').pop().startsWith(q) ? 0 : 1) || a.length - b.length);
      return openMenu(hits.map(f => ({ label: f.split('/').pop(), hint: f.includes('/') ? f.slice(0, f.lastIndexOf('/')) : '', value: f })), { kind: '@', start, end: caret });
    }
    closeMenu();
  }
  window.onFiles = list => { files = list; if (!menu.hidden || /(^|\s)@\S*$/.test(input.value.slice(0, input.selectionStart))) complete(); };

  function addImage(file) {
    if (!file || !/^image\/(png|jpeg|gif|webp)$/.test(file.type)) return;
    const reader = new FileReader();
    reader.onload = () => {
      const image = { type: file.type, data: String(reader.result).split(',')[1] };
      images.push(image);
      const thumb = document.createElement('div');
      thumb.className = 'thumb';
      thumb.innerHTML = `<img src="${esc(reader.result)}" alt="Attached image"><button aria-label="Remove image">×</button>`;
      thumb.querySelector('button').onclick = () => { images = images.filter(x => x !== image); thumb.remove(); };
      attachments.appendChild(thumb);
    };
    reader.readAsDataURL(file);
  }

  sendButton.onclick = () => busy && !input.value.trim() && !images.length ? stop() : submit();
  input.onkeydown = e => {
    if (!menu.hidden) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); menuIndex = (menuIndex + (e.key === 'ArrowDown' ? 1 : menuItems.length - 1)) % menuItems.length; highlight(); return; }
      if (e.key === 'Enter' || e.key === 'Tab') { e.preventDefault(); pick(menuIndex); return; }
    }
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); submit(); }
  };
  input.oninput = () => { input.style.height = ''; input.style.height = input.scrollHeight + 'px'; setBusy(busy); complete(); };
  input.onclick = complete;
  input.onpaste = e => [...(e.clipboardData && e.clipboardData.files) || []].forEach(addImage);
  document.addEventListener('dragover', e => e.preventDefault());
  document.addEventListener('drop', e => { e.preventDefault(); [...(e.dataTransfer && e.dataTransfer.files) || []].forEach(addImage); });
  /** Escape: the IDE sends it here (see keepEscapeInChat); a browser without the IDE gets it as a key. */
  window.onEscape = () => {
    if (!popup.hidden) popup.hidden = true;
    else if (!menu.hidden) closeMenu();
    else if (busy) stop();
  };
  document.addEventListener('keydown', e => { if (e.key === 'Escape' && typeof escapeFromIde === 'undefined') { e.preventDefault(); onEscape(); } });
  log.addEventListener('click', e => {
    const url = e.target.dataset && e.target.dataset.url;
    if (url) ide('link:' + url);
    if (e.target.classList.contains('copy')) {
      ide('copy:' + e.target.parentElement.querySelector('code').textContent);
      e.target.textContent = 'Copied';
      setTimeout(() => { e.target.textContent = 'Copy'; }, 1200);
    }
  });
  input.focus();
}
