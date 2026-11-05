# kazuar privacy policy

Effective date: 2026-11-05.

kazuar is maintained by [aroum](https://github.com/aroum). Questions and requests can be submitted through [the project issue tracker](https://github.com/aroum/kazuar/issues). No registration or account is required.

## Data processed on the device

The keyboard processes text and touch input to enter text and offer suggestions. If personalization is enabled, learned words and word combinations are stored locally in the app's private, credential-protected storage. These files are unavailable before the first device unlock after a restart. The settings provide controls for personalization and incognito mode.

When clipboard history is enabled, the keyboard reads clipboard text and retains recent entries in memory for the configured retention period. Pinned entries are persisted in private, credential-protected storage with authenticated encryption using an app-specific Android Keystore key. Clipboard persistence requires Android 6 or later. Entries marked as sensitive by their source application are excluded by default. The adjacent “Save sensitive clipboard entries” setting explicitly allows them to be included. A source application may fail to mark sensitive content; the keyboard cannot reliably identify every password or secret.

The app queries launchable applications to let you configure application-specific incognito exceptions. This list is processed locally and is not sent to the maintainer. The app does not request unrestricted access to all installed packages.

Personal dictionary entries are accessed through Android's user dictionary provider. They may be available to other applications with access to that provider. Removing kazuar does not necessarily remove entries from this system dictionary.

## Backups and transfers

Automatic cloud backup and system device transfer of app data are disabled. Backups are created only through the app's explicit export actions and saved to a location you choose using Android's document picker. Exports may contain settings, personal dictionary entries, and learned typing history. These exported files are not encrypted by kazuar; their confidentiality depends on the storage location you select, which may be a cloud document provider. Pinned clipboard entries and the Keystore encryption key are not included.

## Network access and third parties

kazuar does not transmit input, clipboard contents, typing history, or the application list to the maintainer. The app does not request the Internet permission and does not include advertising or analytics services. Opening external project links uses another application and is subject to that service's privacy policy. A document provider selected for backup may upload the exported file under its own settings and policy.

## Deletion and controls

You can disable clipboard history, clear unpinned history, and unpin entries to remove their persistent copies. Use the learning-history reset controls to delete learned words. Android's “Clear storage” action or uninstalling the app removes its private settings, history, pinned clips, and app-specific encryption key. Copies exported to document providers and entries in the system user dictionary must be deleted separately.

## Changes

Changes to this policy are published in this repository with an updated effective date.
