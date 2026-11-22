# Minecraft Party website

Polish landing page for the mod, served at the root of the existing Cloudflare Worker:
https://minecraft-party.wojciechowskaola57.workers.dev/

`dist/` is the complete deployment artifact: plain HTML, CSS, JavaScript and local assets.
No npm dependencies, compilation, analytics, external scripts or browser requests to the
private party-code API are needed. The existing Cloudflare Git integration can keep its
root directory `cloudflare`, build `npm test` and deployment `npm run deploy`.
`cloudflare/wrangler.jsonc` publishes `../website/dist`; `/health` and `/v1/*` always run
through the API Worker first. Existing clients keep the same service URL.

The lobby interaction on the page is explicitly a **demonstration**, not a live lobby.
Its public sample code is 123456. It creates no invitations or network sessions. The
isometric scene and logo are original SVG illustrations, not in-game screenshots.
The site takes its typography, rounded shapes and editorial pacing as inspiration from
[Vice Website](https://splitshotz.github.io/Vice-Website/); it uses original content and artwork.
Figtree is distributed under its [SIL Open Font License](dist/assets/OFL.txt).

## Preview and validation

From the repository root:

```sh
python3 -m http.server 8080 --directory website/dist
```

Open http://localhost:8080. Clipboard actions work on localhost or HTTPS. Six-digit codes
are hidden initially; sample code copying does not require revealing it. All three accent
themes persist locally, installation tabs support keyboard navigation, animations respect
reduced motion and the page remains readable with JavaScript disabled.

GitHub Actions installs Playwright 1.65.2 and Chromium, exercises the rendered page at
desktop/tablet/phone sizes, checks the real interactions and attaches screenshots. The
separate Cloudflare runtime test verifies that static assets, API routing and SQLite
directory operations work together. These tests do not verify Minecraft multiplayer.
On pushes to `main`, `test/live.py` also checks the public HTML, JavaScript and directory
health after allowing time for Cloudflare's independent Git deployment to finish.

When releasing the mod, update the version, requirements, release and JAR links in
`dist/index.html`, and refresh `dist/assets/social.png`. Do not publish real invitations,
test credentials or player addresses in website content or preview screenshots.
