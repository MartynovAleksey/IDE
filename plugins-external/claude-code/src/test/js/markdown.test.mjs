import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const { md, askAnswer } = createRequire(import.meta.url)('../../main/resources/chat/chat.js');

test('escapes HTML everywhere, so model output cannot run script in the page', () => {
  const out = md('<img src=x onerror=alert(1)>\n`<b>`\n```\n<script>\n```\n[x](https://a.b/"onmouseover="x)\n| <i> | b |\n|---|---|\n| <svg> | c |');
  assert.ok(!/<img|<script|<b>`|"onmouseover|<i>|<svg/.test(out), out);
});

test('renders code, lists, headings, links, emphasis and tables', () => {
  assert.equal(md('# T\n- a\n- **b** and *c*\n\n```js\nx < 1\n```\nsee [doc](https://e.com/a?b=1&c=2)\n\n| k | v |\n|:--|--:|\n| a | `1` |'),
    '<h1>T</h1><ul><li>a</li><li><b>b</b> and <i>c</i></li></ul><pre><button class="copy">Copy</button><code>x &lt; 1</code></pre>' +
    '<p>see <a data-url="https://e.com/a?b=1&amp;c=2">doc</a></p>' +
    '<table><tr><th>k</th><th>v</th></tr><tr><td>a</td><td><code>1</code></td></tr></table>');
});

test('leaves a*b*c and list markers alone', () => {
  assert.equal(md('a*b*c'), '<p>a*b*c</p>');
});

test('answers AskUserQuestion with the chosen labels per question', () => {
  const input = { questions: [{ question: 'Which DB?', options: [{ label: 'PG' }] }, { question: 'Extras?', multiSelect: true }] };
  assert.deepEqual(askAnswer(input, [['PG'], ['cache', 'my own']]).answers, { 'Which DB?': 'PG', 'Extras?': 'cache, my own' });
  assert.deepEqual(input.answers, undefined);
});
