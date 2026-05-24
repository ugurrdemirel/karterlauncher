# Karter website (static)

One-page landing site and privacy policy for Google Play Console and public links.

## Files

| File | Use in Play Console |
|------|---------------------|
| `index.html` | **Website** (optional) |
| `privacy.html` | **Privacy policy** (required) |

## Before publishing

1. Replace `privacy@example.com` in `privacy.html` with your real contact email.
2. Confirm the Play Store link (`id=com.karterlauncher`) matches your published app.
3. Deploy the `website/` folder to any static host (see below).

These files are **not** bundled into the Android APK unless you copy them into `app/src/main/assets/`.

## Deploy with GitHub Pages

From the repo root, with GitHub Pages source set to **GitHub Actions** or **Deploy from branch**:

**Option A — branch `/website` root**

In repo **Settings → Pages**, set publish directory to `/website` on `main`.

**Option B — `gh-pages` branch (only `website/` contents)**

```bash
cd website
git init -b gh-pages
git add .
git commit -m "Publish Karter website"
git remote add origin <your-repo-url>
git push -u origin gh-pages
```

Then enable Pages from the `gh-pages` branch.

Your URLs will look like:

- `https://<user>.github.io/<repo>/` → home
- `https://<user>.github.io/<repo>/privacy.html` → privacy policy

## Local preview

```bash
cd website
python3 -m http.server 8080
```

Open http://localhost:8080

## Play Console

- **Privacy policy URL:** `https://your-domain/privacy.html`
- **Website:** `https://your-domain/` (optional)

Keep the Play **Data safety** form consistent with `privacy.html` and in-app **Settings → Privacy & data**.
