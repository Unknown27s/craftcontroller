# Publishing this site

The site is plain HTML/CSS. No package installation or site build is required.
Nothing has been published automatically.

1. Review the app and choose a project license before distributing it.
2. Build and test the APK. Copy the tested APK to
   `docs/downloads/craftcontroller-debug.apk` and update `docs/downloads/SHA256SUMS`.
   The page links to that relative path, so it works under a repository subpath.
3. After you approve publishing, create your GitHub repository and push the
   source and `docs/` folder. Do not add local SDK paths or signing keys.
4. In the GitHub repository, open **Settings > Pages**. Select **Deploy from a
   branch**, choose your default branch and **/docs**, then save.
5. GitHub will display the site URL after deployment. Check the download link
   and install guide on the published site. A project site normally uses
   `https://OWNER.github.io/REPOSITORY/`.

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
