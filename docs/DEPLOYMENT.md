# Website and GitHub deployment

Serve the root containing index.html with its assets and downloads directories. No frontend build is needed. Fonts and screenshot assets are local.

The 255.5 MiB EXE must be uploaded to GitHub Releases or large-file storage, not committed using ordinary Git. Replace both Windows download href values in index.html and main.html with your actual release URL. The Android APK can also be a release asset. Never substitute a source ZIP for a download button promising an EXE.

For a source repository, exclude downloads/*.exe and downloads/*.apk via .gitignore, then upload the actual binaries separately to Releases. The complete delivery ZIP includes them for direct local use. Confirm your hosting provider’s maximum per-file size before deployment.
