'use strict';
(() => {
  const $ = selector => document.querySelector(selector);
  const $$ = selector => [...document.querySelectorAll(selector)];
  document.documentElement.classList.add('js');
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  let toastTimer;
  function toast(message) {
    clearTimeout(toastTimer);
    $('#toast').textContent = message;
    $('#toast').classList.add('show');
    toastTimer = setTimeout(() => $('#toast').classList.remove('show'), 2800);
  }
  function theme(value) {
    document.documentElement.dataset.accent = value;
    $$('[data-theme]').forEach(button => {
      const selected = button.dataset.theme === value;
      button.setAttribute('aria-checked', selected);
      button.tabIndex = selected ? 0 : -1;
    });
    $('meta[name="theme-color"]').content = getComputedStyle(document.documentElement).getPropertyValue('--bg').trim();
    try { localStorage.setItem('minecraft-party-accent', value); } catch {}
  }
  theme(document.documentElement.dataset.accent);
  $$('[data-theme]').forEach(button => button.addEventListener('click', () => theme(button.dataset.theme)));
  $$('.themes').forEach(group => group.addEventListener('keydown', event => {
    const buttons = [...group.querySelectorAll('button')];
    const index = buttons.indexOf(document.activeElement);
    if (index < 0) return;
    let next;
    if (['ArrowRight', 'ArrowDown'].includes(event.key)) next = (index + 1) % buttons.length;
    if (['ArrowLeft', 'ArrowUp'].includes(event.key)) next = (index + buttons.length - 1) % buttons.length;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = buttons.length - 1;
    if (next !== undefined) { event.preventDefault(); theme(buttons[next].dataset.theme); buttons[next].focus(); }
  }));

  const menu = $('.mobile-menu');
  function closeMenu() { menu.setAttribute('aria-expanded', 'false'); menu.setAttribute('aria-label', 'Otwórz menu'); $('#mobile-navigation').hidden = true; }
  menu.addEventListener('click', () => {
    const open = menu.getAttribute('aria-expanded') !== 'true';
    menu.setAttribute('aria-expanded', open);
    menu.setAttribute('aria-label', open ? 'Zamknij menu' : 'Otwórz menu');
    $('#mobile-navigation').hidden = !open;
  });
  $$('#mobile-navigation a').forEach(link => link.addEventListener('click', closeMenu));
  document.addEventListener('keydown', event => { if (event.key === 'Escape' && !$('#mobile-navigation').hidden) { closeMenu(); menu.focus(); } });
  document.addEventListener('click', event => { if (!event.target.closest('.topbar')) closeMenu(); });
  window.matchMedia('(min-width: 651px)').addEventListener('change', closeMenu);

  async function copy(value, message) {
    try {
      if (navigator.clipboard && window.isSecureContext) await navigator.clipboard.writeText(value);
      else {
        const field = document.createElement('textarea');
        field.value = value; field.setAttribute('readonly', '');
        field.style.position = 'fixed'; field.style.opacity = '0';
        document.body.append(field); field.select();
        const success = document.execCommand('copy'); field.remove();
        if (!success) throw new Error('Clipboard unavailable');
      }
      toast(message);
    } catch { toast('Nie udało się skopiować. Zaznacz tekst i skopiuj go ręcznie.'); }
  }
  $$('[data-copy]').forEach(button => button.addEventListener('click', () => copy(button.dataset.copy, button.dataset.success)));
  $('#copy-path').addEventListener('click', () => copy($('#mods-path').textContent, 'Ścieżka folderu skopiowana'));
  $('#reveal-code').addEventListener('click', () => {
    const shown = $('#reveal-code').getAttribute('aria-pressed') !== 'true';
    $('#reveal-code').setAttribute('aria-pressed', shown);
    $('#reveal-code').setAttribute('aria-label', shown ? 'Ukryj przykładowy kod' : 'Pokaż przykładowy kod');
    $('#reveal-code use').setAttribute('href', shown ? '#i-eye-off' : '#i-eye');
    $('#demo-code').textContent = shown ? '123 − 456' : '••• − •••';
    $('#demo-code').setAttribute('aria-label', shown ? 'Przykładowy kod: 123456' : 'Kod przykładowy ukryty');
  });
  $$('[data-leader]').forEach(button => button.addEventListener('click', () => {
    $$('[data-leader]').forEach(candidate => {
      const selected = button === candidate;
      candidate.classList.toggle('selected', selected);
      candidate.setAttribute('aria-pressed', selected);
      candidate.setAttribute('aria-label', `${selected ? 'Lider w podglądzie' : 'Wybierz lidera w podglądzie'}: ${candidate.dataset.leader}`);
    });
    $('#demo-status').textContent = `Lider w tym przykładzie: ${button.dataset.leader}. Skład Party zostaje.`;
  }));
  function demoStep(step) {
    $$('.scene-step').forEach(element => element.classList.toggle('current', Number(element.dataset.step) === step));
    $$('.member-status').forEach(element => { element.textContent = ['W lobby', 'Łączenie…', 'W świecie'][step]; });
    $('#demo-status').textContent = ['Podgląd: ekipa spotyka się w lobby przed wyborem świata.', 'Podgląd: znajomi wpisują kod, a lider uruchamia świat.', 'Podgląd gotowy. W modzie połączenie rozpoczyna się po wyborze świata.'][step];
    $('.world-frame').classList.toggle('playing', step === 2);
  }
  $('#demo-play').addEventListener('click', async () => {
    const button = $('#demo-play'); button.disabled = true;
    for (let step = 0; step < 3; step++) {
      demoStep(step);
      if (!reducedMotion && step < 2) await new Promise(resolve => setTimeout(resolve, 1100));
    }
    button.querySelector('span').textContent = 'Odtwórz podgląd ponownie'; button.disabled = false;
  });

  function platform(value, focus = false) {
    $$('[data-platform]').forEach(button => {
      const selected = button.dataset.platform === value;
      button.setAttribute('aria-selected', selected); button.tabIndex = selected ? 0 : -1;
      if (selected && focus) button.focus();
    });
    $('#platform-instructions').setAttribute('aria-labelledby', `tab-${value}`);
    $('#mods-path').textContent = value === 'linux' ? '~/.minecraft/mods' : '%appdata%\\.minecraft\\mods';
  }
  $$('[data-platform]').forEach(button => button.addEventListener('click', () => platform(button.dataset.platform)));
  $('.platform-tabs').addEventListener('keydown', event => {
    if (['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) {
      event.preventDefault();
      platform(event.key === 'Home' ? 'windows' : event.key === 'End' ? 'linux' : $('#tab-windows').getAttribute('aria-selected') === 'true' ? 'linux' : 'windows', true);
    }
  });

  if ('IntersectionObserver' in window && !reducedMotion) {
    const observer = new IntersectionObserver(entries => entries.forEach(entry => {
      if (entry.isIntersecting) { entry.target.classList.add('visible'); observer.unobserve(entry.target); }
    }), { rootMargin: '0px 0px -35px 0px', threshold: 0.03 });
    $$('.reveal').forEach(element => observer.observe(element));
  } else $$('.reveal').forEach(element => element.classList.add('visible'));
  const railLinks = $$('[data-rail]');
  let scheduled = false;
  function updateRail() {
    let current = railLinks[0];
    railLinks.forEach(link => { if (document.getElementById(link.dataset.rail).getBoundingClientRect().top < window.innerHeight * .45) current = link; });
    railLinks.forEach(link => { link.classList.toggle('current', link === current); if (link === current) link.setAttribute('aria-current', 'location'); else link.removeAttribute('aria-current'); });
    const height = document.documentElement.scrollHeight - window.innerHeight;
    $('#rail-progress').style.height = `${height > 0 ? Math.min(100, window.scrollY / height * 100) : 0}%`;
    scheduled = false;
  }
  window.addEventListener('scroll', () => { if (!scheduled) { scheduled = true; requestAnimationFrame(updateRail); } }, { passive: true });
  window.addEventListener('resize', updateRail); updateRail();
})();
