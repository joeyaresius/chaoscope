# Privacy Policy — Chaoscope for Android

_Last updated: May 2026_

## Summary

**The Chaoscope app does not collect, store, or share any personal user data. All processing is performed locally on the user's device. No third-party SDKs are used that track user behaviour.**

---

## Data collection

Chaoscope collects **no data** of any kind. Specifically:

- No personal information (name, email, phone number, etc.)
- No device identifiers (Android ID, IMEI, advertising ID, etc.)
- No usage analytics or telemetry
- No crash reports sent to any server
- No location data
- No camera or microphone access

## Network access

The app declares **no `INTERNET` permission**. Android's OS-level sandbox therefore blocks all outbound network traffic regardless of any code path. The app is incapable of sending data over a network.

## Local storage

The only write operation performed by the app is **PNG export**: when the user explicitly taps the Export button, the rendered image is saved to the `Pictures/Chaoscope/` folder on the user's own device. No other files are written, and no data leaves the device.

## Third-party SDKs

No third-party analytics, advertising, crash-reporting, or behavioural-tracking SDKs are included. The full dependency list consists exclusively of Android Jetpack / AndroidX libraries published by Google under the Apache 2.0 licence.

## External links

The splash screen contains two optional links that open outside the app:

| Button | Destination | Data sent by the app |
|---|---|---|
| Buy me a coffee | buymeacoffee.com/balancin | None — opens system browser |
| Suggest or Criticize | mailto:chaoscope@duck.com | None — opens user's email client |

In both cases the app passes no data. Standard browser/email-client HTTP headers (IP address, User-Agent) may be exchanged — that is the responsibility of the respective system app, not Chaoscope.

## Children's privacy

Because no data is collected at all, there are no special risks for users of any age.

## Changes to this policy

If a future version of the app introduces any form of data handling, this policy will be updated before that version is published, and the change will be clearly noted in the release notes.

## Contact

Questions about this privacy policy can be sent to **chaoscope@duck.com**.
