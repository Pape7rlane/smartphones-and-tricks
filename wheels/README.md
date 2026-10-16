Native Android wheels cross-compiled by CI are downloaded here before the APK
build (see .github/workflows/android.yml). Keep this directory so that
`options '--find-links', 'wheels'` in app/build.gradle always resolves.
