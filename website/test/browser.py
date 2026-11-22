#!/usr/bin/env python3
"""Actual rendered-page checks, run in GitHub Actions (Chromium required)."""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import json
import threading

from playwright.sync_api import sync_playwright, expect

root = Path(__file__).resolve().parents[1]
artifacts = root / 'artifacts'
artifacts.mkdir(exist_ok=True)

class QuietHandler(SimpleHTTPRequestHandler):
    def do_GET(self):
        # A project Pages site serves assets under /minecraft-party/, not /.
        if not self.path.startswith('/minecraft-party/'):
            self.send_error(404)
            return
        self.path = self.path[len('/minecraft-party'):]
        super().do_GET()

    def log_message(self, *args):
        pass

server = ThreadingHTTPServer(('127.0.0.1', 0), partial(QuietHandler, directory=str(root / 'dist')))
threading.Thread(target=server.serve_forever, daemon=True).start()
url = f'http://127.0.0.1:{server.server_port}/minecraft-party/'
try:
    with sync_playwright() as p:
        browser = p.chromium.launch()
        context = browser.new_context(permissions=['clipboard-read', 'clipboard-write'], reduced_motion='reduce')
        page = context.new_page()
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        overflow = []
        for width, height in [(1440, 1000), (1024, 900), (768, 1024), (390, 844), (320, 740)]:
            page.set_viewport_size({'width': width, 'height': height})
            response = page.goto(url)
            assert response.status == 200
            page.evaluate('document.fonts.ready')
            if page.evaluate('document.documentElement.scrollWidth > innerWidth'):
                offenders = page.evaluate('''[...document.querySelectorAll('body *')].filter(e => {
                    const r=e.getBoundingClientRect(); return r.width && (r.right > innerWidth+1 || r.left < -1);
                }).map(e => ({tag:e.tagName, cls:e.className.baseVal ?? e.className, right:Math.round(e.getBoundingClientRect().right), left:Math.round(e.getBoundingClientRect().left)})).slice(0,35)''')
                overflow.append(width)
                print('OVERFLOW',width,json.dumps(offenders),flush=True)
            expect(page.locator('h1')).to_contain_text('Twój świat.')
            assert page.locator('.world-art').evaluate('(img) => img.complete && img.naturalWidth > 0')
            # Reduced motion also keeps every section visible for full-page review.
            page.screenshot(path=str(artifacts / f'page-{width}.png'), full_page=True)
            print(f'Render: {width}x{height}, assets loaded',flush=True)
        assert not overflow, f'Horizontal overflow at widths {overflow}'

        for width, selector, name in [(1440, '#mozliwosci', 'features'), (1440, '.leadership-block', 'leader'), (1440, '#instalacja', 'install'), (1440, '#pytania', 'faq'), (390, '#mozliwosci', 'mobile-feature')]:
            page.set_viewport_size({'width': width, 'height': 1000})
            page.locator(selector).evaluate('(element) => window.scrollTo(0, element.offsetTop - 110)')
            page.screenshot(path=str(artifacts / f'section-{name}.png'))

        page.set_viewport_size({'width': 1440, 'height': 1000})
        page.goto(url)
        expect(page.locator('#demo-code')).to_have_text('••• − •••')
        page.locator('[data-copy]').click()
        assert page.evaluate('navigator.clipboard.readText()') == '123456'
        expect(page.locator('#demo-code')).to_have_text('••• − •••')
        page.locator('#reveal-code').click()
        expect(page.locator('#demo-code')).to_have_text('123 − 456')
        page.locator('#reveal-code').click()
        expect(page.locator('#demo-code')).to_have_text('••• − •••')
        page.locator('[data-leader="Mila"]').click()
        expect(page.locator('[data-leader="Mila"]')).to_have_attribute('aria-pressed', 'true')
        expect(page.locator('[data-leader="Alex"]')).to_have_attribute('aria-pressed', 'false')
        page.locator('#demo-play').click()
        expect(page.locator('.member-status').first).to_have_text('W świecie')
        expect(page.locator('#demo-play')).to_be_enabled()
        print('Lobby demonstration: hidden code, copying, reveal, leader selection, replay passed')

        page.locator('.nav-end [data-theme="purple"]').click()
        expect(page.locator('html')).to_have_attribute('data-accent', 'purple')
        page.reload()
        expect(page.locator('html')).to_have_attribute('data-accent', 'purple')
        page.locator('.nav-end [data-theme="purple"]').focus()
        page.keyboard.press('ArrowLeft')
        expect(page.locator('html')).to_have_attribute('data-accent', 'green')
        page.locator('.nav-end [data-theme="blue"]').click()
        page.locator('#tab-linux').click()
        expect(page.locator('#mods-path')).to_have_text('~/.minecraft/mods')
        page.locator('#copy-path').click()
        assert page.evaluate('navigator.clipboard.readText()') == '~/.minecraft/mods'
        page.locator('#tab-linux').focus()
        page.keyboard.press('ArrowLeft')
        expect(page.locator('#tab-windows')).to_have_attribute('aria-selected', 'true')
        expect(page.locator('#mods-path')).to_have_text('%appdata%\\.minecraft\\mods')
        print('Accent persistence, radio keyboard control, installation tabs and folder copying passed')

        page.set_viewport_size({'width': 390, 'height': 844})
        page.locator('.mobile-menu').click()
        expect(page.locator('#mobile-navigation')).to_be_visible()
        page.keyboard.press('Escape')
        expect(page.locator('#mobile-navigation')).to_be_hidden()
        page.locator('.mobile-menu').click()
        page.locator('#mobile-navigation a[href="#pytania"]').click()
        expect(page.locator('#mobile-navigation')).to_be_hidden()
        page.locator('details').first.locator('summary').click()
        expect(page.locator('details').first).to_have_attribute('open', '')
        expect(page.locator('.download-main')).to_have_attribute('href', 'https://github.com/SigmaMiaek/minecraft-party/releases/download/v0.7.1%2B26.2/minecraft-party-0.7.1%2B26.2.jar')
        assert not errors, errors
        print('Mobile navigation, FAQ, published release target, no JavaScript errors passed')
        context.close()
        # The essential content, downloads, installation and FAQ also work without JS.
        context = browser.new_context(java_script_enabled=False, viewport={'width': 390, 'height': 844})
        page = context.new_page()
        page.goto(url)
        expect(page.locator('#install-title')).to_be_visible()
        expect(page.locator('.download-main')).to_be_visible()
        assert page.locator('h1').inner_text().startswith('Twój świat.')
        print('Content and download are available without JavaScript')
        browser.close()
finally:
    server.shutdown()
