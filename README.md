# CleanShield Pro – Phase 3

Advanced Android ad & tracker blocking system.

## Features (Phase 3)
- System-wide protection using local VPN
- Domain-based ad & tracker blocking
- Battery Saver mode
- Per-setting controls (Ads / Trackers / Aggressive)
- Statistics (blocked count + data saved)
- Start on boot option
- Clean modern dark UI
- Foundation for element hiding, cloud sync, and full filter list downloads

## Important Notes
This is a **complete foundation** for a Phase 3 app.  
Full production-grade packet parsing and remote EasyList downloading can be extended from this base.

### How to build the APK online (No Android Studio needed)

1. Create a free GitHub account (if you don’t have one).
2. Create a **new repository** (name it e.g. `CleanShieldPro`).
3. Upload **all files and folders** from this project into the repository (you can drag & drop in the GitHub website).
4. Go to the **Actions** tab of your repository.
5. Run the workflow **“Build CleanShield Pro APK”** (or it will run automatically on push).
6. When the build finishes, download the APK from the **Artifacts** section.

### Permissions the app will request
- VPN permission (required for system-wide blocking)
- Internet
- Notification (to show protection status)

### Disclaimer
This project is for educational and personal use.  
Some websites or apps may detect blocking and stop working. Use the whitelist feature when needed.
