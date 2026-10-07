// DM Only cage. Runs at document start on every instagram.com page.
// Bounces feed surfaces to the inbox, and freezes a reel opened from a DM
// so you can watch it but cannot swipe on to the next one.
(function () {
  if (window.__dmOnlyCage) return;
  window.__dmOnlyCage = true;

  var INBOX = '/direct/inbox/';

  // Feed surfaces: anything matching is sent to the inbox.
  var BLOCK = [
    /^\/$/,                                          // home feed
    /^\/reels\/?$/,                                  // Reels tab
    /^\/reel\/?$/,                                   // bare /reel
    /^\/explore(\/|$)/,                              // Explore + search grid
    /^\/[A-Za-z0-9._]+\/(reels|tagged|saved)(\/|$)/  // a profile's scrolling tabs
  ];

  // Single reel permalink (what a DM share opens). Allowed, but locked.
  var REEL = /^\/reels?\/([A-Za-z0-9_-]+)\/?/;

  var locked = null;          // id of the reel being watched
  var pinned = new WeakMap(); // scroller -> scrollTop it is held at
  var lastPath = null;

  function isBlocked(path) {
    for (var i = 0; i < BLOCK.length; i++) if (BLOCK[i].test(path)) return true;
    return false;
  }

  function reelId(path) {
    var m = REEL.exec(path);
    return m ? m[1] : null;
  }

  function pathOf(url) {
    try { return new URL(url, location.href).pathname; } catch (e) { return null; }
  }

  // Returns false when navigation to `path` must not happen.
  function allow(path) {
    if (path == null) return true;
    if (isBlocked(path)) {
      location.replace(INBOX);
      return false;
    }
    var id = reelId(path);
    if (id && locked && id !== locked) {
      // Swiped to the next reel: go back to the one that was shared.
      location.replace('/reel/' + locked + '/');
      return false;
    }
    if (id !== locked) pinned = new WeakMap();
    locked = id;
    return true;
  }

  // --- SPA navigation hooks -------------------------------------------------
  ['pushState', 'replaceState'].forEach(function (name) {
    var orig = history[name];
    history[name] = function (state, title, url) {
      if (url != null && !allow(pathOf(url))) return;
      return orig.apply(this, arguments);
    };
  });

  function check() {
    var path = location.pathname;
    if (path === lastPath) return;
    lastPath = path;
    allow(path);
  }
  window.addEventListener('popstate', check);
  setInterval(check, 400);
  check();

  // --- Links to feed surfaces do nothing -----------------------------------
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest && e.target.closest('a[href]');
    if (!a) return;
    var path = pathOf(a.getAttribute('href'));
    if (path && isBlocked(path)) {
      e.preventDefault();
      e.stopImmediatePropagation();
    }
  }, true);

  // --- Freeze the reel viewer ----------------------------------------------
  // The reel feed is a scroll container holding <video>s. Comments, captions
  // and text boxes scroll in containers with no video, so they still work.
  function scrollerOf(node) {
    for (var el = node; el && el !== document.documentElement; el = el.parentElement) {
      if (el.nodeType !== 1) continue;
      var oy = getComputedStyle(el).overflowY;
      if ((oy === 'auto' || oy === 'scroll') && el.scrollHeight > el.clientHeight) return el;
    }
    return document.scrollingElement;
  }

  function isReelFeed(el) {
    return !!(el && el.querySelector && el.querySelector('video'));
  }

  function reelScroller(target) {
    if (!locked) return null;
    var el = scrollerOf(target);
    return isReelFeed(el) ? el : null;
  }

  function pin(el) {
    if (el && !pinned.has(el)) pinned.set(el, el.scrollTop);
  }

  document.addEventListener('touchstart', function (e) {
    pin(reelScroller(e.target));
  }, { capture: true, passive: true });

  ['touchmove', 'wheel'].forEach(function (type) {
    document.addEventListener(type, function (e) {
      var el = reelScroller(e.target);
      if (!el) return;
      pin(el);
      e.preventDefault();
    }, { capture: true, passive: false });
  });

  document.addEventListener('keydown', function (e) {
    if (!locked) return;
    var t = e.target;
    if (/^(ArrowUp|ArrowDown|PageUp|PageDown| )$/.test(e.key) &&
        !/^(INPUT|TEXTAREA)$/.test(t.tagName) && !t.isContentEditable) {
      e.preventDefault();
      e.stopImmediatePropagation();
    }
  }, true);

  // Last line of defence: if the feed moves anyway (momentum, programmatic
  // scroll), snap it back to where it was pinned.
  document.addEventListener('scroll', function (e) {
    if (!locked) return;
    var el = e.target === document ? document.scrollingElement : e.target;
    if (!isReelFeed(el)) return;
    pin(el);
    var top = pinned.get(el);
    if (Math.abs(el.scrollTop - top) > 2) el.scrollTop = top;
  }, true);

  // --- Unread count to native notifications --------------------------------
  // Instagram prefixes the tab title with the unread count: "(3) Instagram".
  var lastUnread = -1;
  function reportUnread() {
    if (window.top !== window || !window.DMOnlyNative) return;
    var m = /^\((\d+)\)/.exec(document.title);
    var n = m ? +m[1] : 0;
    if (n === lastUnread) return;
    lastUnread = n;
    DMOnlyNative.postMessage(String(n));
  }
  setInterval(reportUnread, 2000);

  // --- Hide the doorways ----------------------------------------------------
  var css =
    'a[href="/"], a[href="/reels/"], a[href="/reels"], a[href^="/explore"],' +
    'a[href$="/reels/"][role="tab"], a[href$="/tagged/"], a[href$="/saved/"]' +
    '{ display: none !important; }';
  function addStyle() {
    var s = document.createElement('style');
    s.textContent = css;
    (document.head || document.documentElement).appendChild(s);
  }
  if (document.documentElement) addStyle();
  else document.addEventListener('DOMContentLoaded', addStyle);
})();
