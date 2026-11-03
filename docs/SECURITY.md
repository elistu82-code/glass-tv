# Security and recovery

- No root, package disabling/removal, accessibility service, Home interception or sensitive permission grants.
- Package visibility uses scoped MAIN + LAUNCHER/LEANBACK_LAUNCHER queries. QUERY_ALL_PACKAGES is not requested. Hidden apps remain accessible in settings.
- App details and uninstall use official Android intents. System apps cannot be uninstalled through the launcher. Deletion always requires the system confirmation.
- ACCESS_NETWORK_STATE permits local status only. No INTERNET, location, microphone, storage-wide or audio-capture permissions. Optional Spotify NotificationListenerService is protected by the system BIND_NOTIFICATION_LISTENER_SERVICE permission; no access is enabled automatically. The UI explains that Notification Access is broad; callbacks ignore notification content and the adapter processes only Spotify sessions. No privileged MEDIA_CONTENT_CONTROL grant. No telemetry.
- Local image/video import uses system document picker and byte/type/dimension bounds. Files are copied into private storage and decoded off the UI thread. JSON backup is bounded, versioned, validated and contains no credentials; restored wallpaper paths are ignored.
- Existing Home on Fire was configured by the user and must remain unchanged. Back closes overlays; settings provides an explicit stock Home action. No role/component/helper/permission changes during V0.5. Do not assume normal Home is a stock-launcher recovery path while the helper is active.
- Debug APK is development-signed and debuggable. No signing keys or local configuration belong in Git. CI creates its own debug key, so CI APK updates may require uninstalling a locally signed APK first (export settings first).

Home redirection is active externally, not implemented by Glass. Future reconfiguration still requires explicit approval. App-specific Android/media exchange adds no broad storage permission; import data remains untrusted and is validated before copying/restoring.
