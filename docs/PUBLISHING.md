# Publishing this site

The site is plain HTML/CSS. No package installation or site build is required.
Live site: <https://unknown27s.github.io/craftcontroller/>.
Pages uses GitHub Actions with `.github/workflows/pages.yml`, deploying only
`docs/`. Pushes affecting the site deploy automatically, or dispatch **Deploy
GitHub Pages** manually. This workflow does not build Android or run app tests.

1. Review the app and choose a project license before distributing it.
2. Build and test the APK. Copy the tested APK to
   `docs/downloads/craftcontroller-debug.apk` and update `docs/downloads/SHA256SUMS`.
   The page links to that relative path, so it works under a repository subpath.
3. After you approve publishing, create your GitHub repository and push the
   source and `docs/` folder. Do not add local SDK paths or signing keys.
4. In the GitHub repository, open **Settings > Pages** and select **GitHub
   Actions** as the source (already configured for this repository).
5. GitHub will display the site URL after deployment. Check the download link
   and install guide on the published site. A project site normally uses
    `https://OWNER.github.io/REPOSITORY/`.

Rollback: revert the unwanted site commit, push the revert to `main`, and run
the Pages workflow. To take the site offline immediately, use
`gh api --method DELETE repos/Unknown27s/craftcontroller/pages`.
Verify the home page, `assets/app-icon.svg` and APK download after deployment.

Preview locally from the project root:

```sh
python3 -m http.server 8080 --directory docs
```

Open <http://localhost:8080>. The page does not load third-party fonts, scripts,
trackers, or other external assets. External credit links open only when clicked.

The site labels the APK as a debug-signed prototype. Replace that description and
link only after you prepare a signed release and validate it. Do not imply that
the original DSU Controller author endorses this implementation.

Reference: <https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site>.
