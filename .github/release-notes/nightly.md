## Android 13 and newer

Kazuar now requires **Android 13+ (API 33)**. Android 12 and older are no longer supported. The keyboard and interface support English and Russian.

## Important: signing certificate and backup migration

**The signing certificate has changed compared with older builds, including CI builds that used temporary keys.** Android may report a conflict with the installed app instead of allowing an update.

Before uninstalling an older version:

1. Open Backup in the installed app and use **Full export (all data)**. Save the ZIP outside the app, for example in Downloads, and make sure it was created successfully.
2. Save important clipboard entries separately. **Clipboard history and pinned clips are not included in the backup.**
3. If Android rejects the update because of the signature, uninstall the old version and install this nightly.
4. Enable/select Kazuar again and **manually import the saved backup**. Settings, custom layouts, the personal dictionary and learning history must be transferred through the backup; they are not migrated automatically after uninstalling.

**Legacy backups:** current full/history imports require the portable archive containing `history.json`. Old binary learning-history archives are rejected. When migrating from a much older build, also export settings and the personal dictionary as separate JSON files and import those separately. Binary learning history cannot be restored by this version. Check the backup format and keep your original files before uninstalling.

Current installable CI builds and nightly builds use the same persistent signing key. Builds already using that key can normally be updated in place.

## Main changes

- **Per-app keyboard controls:** independently override learning, suggestions, autocorrection, learned-word dictionary use, next-word predictions and automatic capitalization. Enable a switch for each app and tap its row to customize it. Explicit overrides can take priority over app requests, including incognito/no-learning requests. Enabling learning in private or password fields can save their text. Gemini input can be hosted by the Google app; configure Google for those fields.
- **Settings cleanup:** animation, slide-preview, scale and timing controls are in Themes & Appearance. Keyboard resizing has a single home there. The last-crash report is in Debug, with local viewing and explicit copying only; nothing is sent automatically.
- **Privacy and backups:** improved sensitive-clipboard handling, protected pinned-clip storage, validated custom-layout imports and safer portable backups, including learning history.
- **Keyboard and native stability:** extensive dictionary, Unicode, JNI, persistence and bounds-checking fixes, plus support for native libraries on systems with 16 KB memory pages.
- **Build improvements:** updated Android/Kotlin/Gradle dependencies and resource shrinking with explicit protection for dynamically loaded layouts, dictionaries and text. Packaged APK smoke tests run on a 16 KB Android emulator.
