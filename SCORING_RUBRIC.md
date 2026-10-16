# App Risk Auditor — Scoring Rubric

## Permission Category Weights (0–30 max each)

| Category | Weight | Description |
|---|---|---|
| **Special Access** | 30 | Accessibility, overlay, device admin, notification listener, install packages, usage stats |
| **SMS** | 15 | Read, receive, send SMS — OTP theft, premium SMS fraud |
| **Contacts & Call Logs** | 12 | Harvesting social graph |
| **Location** | 12 | Fine + background location tracking |
| **Camera & Microphone** | 10 | Covert recording |
| **Background & Persistence** | 10 | Boot receiver, foreground service, ignore battery optimizations |
| **Phone & Identity** | 8 | Phone state, phone numbers, call phone |
| **Storage & Media** | 8 | External storage, media images, manage all files |
| **Network** | 5 | Internet, network state — exfiltration channel |

## Special Access Breakdown

| Access | Points | Reason |
|---|---|---|
| Accessibility Service active | 25 | Screen reading, tap interception |
| Device Admin active | 20 | Uninstall blocking, data wipe |
| Notification Listener active | 15 | OTP capture from notifications |
| Screen Overlay (SYSTEM_ALERT_WINDOW) | 15 | Fake login overlays |
| Request Install Packages | 10 | Install further APKs |
| VPN Service | 8 | Intercept all network traffic |
| Package Usage Stats | 5 | App usage profiling |
| Notification Policy Access | 5 | Suppress security alerts |

## Combination Rules

| Rule | Bonus | Trigger |
|---|---|---|
| SMS + Internet + Boot + Sideloaded | +20 | Classic banking trojan pattern |
| Accessibility + Overlay | +25 | Overlay attack (fake login screens) |
| Device Admin on non-security app | +20 | May block uninstallation |
| Notification Listener + SMS | +15 | Dual OTP capture channels |
| Camera + Mic + Boot + Internet | +18 | Covert surveillance |
| Accessibility on non-accessibility app | +15 | Unjustified screen access |
| Location + Contacts + Internet | +12 | Stalking/doxxing combo |

## Context Mismatch

| Mismatch | Bonus | Example |
|---|---|---|
| Torch/Calculator/Wallpaper with SMS | +8 | Flashlight app reading messages |
| Simple utility with contacts/call log | +8 | Booster app reading call history |
| Simple utility with accessibility | +8 | Wallpaper app with screen reader access |

## Sideload Penalty

Sideloaded app (not from Play Store) with score > 30: **+15 points**

## Risk Bands

| Band | Score | Color |
|---|---|---|
| **Low** | 0–24 | Green |
| **Medium** | 25–49 | Orange |
| **High** | 50–74 | Red |
| **Critical** | 75–100 | Dark Red |

Every High/Critical app displays **at least 3 plain-language reasons**.

## Explanation Requirements

Each reason answers: **What access does it have? → What can the attacker do? → Why should you care?**

Example:
> "Accessibility service is ACTIVE — can read all screen content, intercept taps, capture text from any app including banking and OTP screens"
