// WebMCP: gives an AI agent in the visitor's browser a few typed tools for this page.
// Every answer is read from the page at call time, so the text lives only in the HTML.
// Makes no request. Silent when the browser has no document.modelContext.
// Spec (a draft): https://webmachinelearning.github.io/webmcp/
// Loaded as a module (deferred, and its names stay out of the page's global scope).
const text = (el) => (el ? el.textContent.replace(/\s+/g, ' ').trim() : '');
const NONE = { type: 'object', properties: {}, additionalProperties: false };
const kind = () => {
  if (document.getElementById('limits')) return 'home';
  if (document.querySelector('.prose')) return 'privacy';
  return 'not_found';
};

const describe = {
  name: 'describe',
  title: 'Describe this page',
  description:
    'Says what this page is: its kind (home, privacy or not_found), language, title, summary, headings and the tools it offers. Call it first.',
  inputSchema: NONE,
  annotations: { readOnlyHint: true },
  execute: async () => ({
    ok: true,
    page: kind(),
    language: document.documentElement.lang || null,
    title: document.title,
    url: location.href,
    summary: document.querySelector('meta[name="description"]')?.content || text(document.querySelector('.lede')),
    headings: Array.from(document.querySelectorAll('main h1, main h2')).map(text),
    tools: toolsFor(kind()).map((t) => t.name),
  }),
};

const getDownload = {
  name: 'get_download',
  title: 'Where to get the app',
  description:
    'Lists the ways to get the app shown on this page, as {name, url}; url is null when the entry has no link yet (F-Droid before the app is listed there).',
  inputSchema: NONE,
  annotations: { readOnlyHint: true },
  execute: async () => ({
    ok: true,
    items: Array.from(document.querySelectorAll('#install .install > li')).map((li) => ({
      name: text(li),
      url: li.querySelector('a')?.href || null,
    })),
  }),
};

const listLimits = {
  name: 'list_limits',
  title: 'What the app cannot do',
  description:
    "Lists what the app cannot do, as {title, text}, including how it works with Android's Private DNS setting.",
  inputSchema: NONE,
  annotations: { readOnlyHint: true },
  execute: async () => ({
    ok: true,
    items: Array.from(document.querySelectorAll('#limits .cards > li')).map((li) => ({
      title: text(li.querySelector('h3')),
      text: text(li.querySelector('p')),
    })),
  }),
};

const getPrivacySummary = {
  name: 'get_privacy_summary',
  title: 'Privacy policy in short',
  description:
    'Gives the short privacy statement of this policy as {summary, effective, sections}, where sections lists the headings of the full policy.',
  inputSchema: NONE,
  annotations: { readOnlyHint: true },
  execute: async () => ({
    ok: true,
    summary: text(document.querySelector('.prose h2 + p')),
    effective: text(document.querySelector('.page-hero .meta')),
    sections: Array.from(document.querySelectorAll('.prose h2')).map(text),
  }),
};

const toolsFor = (page) => {
  if (page === 'home') return [describe, getDownload, listLimits];
  if (page === 'privacy') return [describe, getPrivacySummary];
  return [describe];
};

const registry = document.modelContext;
if (registry && typeof registry.registerTool === 'function') {
  // One refused tool must not stop the others, and nothing here may reject.
  for (const tool of toolsFor(kind())) {
    Promise.resolve()
      .then(() => registry.registerTool(tool))
      .catch(() => {});
  }
}
