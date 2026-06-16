# Android Solid Services

## What is Solid?

[Solid](https://solidproject.org/) is an open standard led by Sir Tim Berners-Lee that gives users full control over their own data. Instead of your data living inside apps, it lives in your personal **Solid pod** — a secure online storage space you own. Apps are granted permission to read or write to your pod; you can revoke that permission at any time.

The result is a web where data and applications are decoupled: switch apps without losing your data, share data between apps without copy-pasting, and keep full ownership of everything you create.

## The Android Gap

Despite Solid's growing adoption, the Android ecosystem had no native way to integrate with Solid pods. Each app had to implement its own authentication flow, manage tokens independently, and ask users to log in separately per app — making it painful to build Solid-powered Android apps and frustrating to use them.

## How Android Solid Services Solves It

**Android Solid Services (ASS)** is a host application that acts as the Solid identity layer for Android. It manages your Solid accounts in one place and exposes secure inter-process communication (IPC) services that any third-party app can connect to.

- **Single sign-in** — log in once in the ASS app; every other app on your device reuses that session.
- **Multi-account** — manage multiple Solid pod accounts and switch between them from Settings.
- **Permission control** — each app must request access; you approve or deny from within ASS.
- **Resource sharing** — share pod resources and containers with other people at View / Add / Edit access (Web Access Control or Access Control Policy), with share links and a Linked Data Notifications inbox for offers and access requests.
- **SDK** — two libraries (`client`, `api`) let developers integrate Solid into their Android apps with just a few lines of Kotlin.

## See It in Action

| Login                                                                                     | Pod Selector                                                                                 | Account Mangment                                                                              | Granted Apps                                                                                 |
|-------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| ![Login](https://github.com/user-attachments/assets/9afe2f9d-f4a3-4e05-ae77-ab6e13febf84) | ![Accounts](https://github.com/user-attachments/assets/543b2d9e-2f51-481f-b50d-934ece61172f) | ![Resources](https://github.com/user-attachments/assets/3deabd3a-d907-407a-9fbb-b27e26882206) | ![Settings](https://github.com/user-attachments/assets/b6df9725-321d-4572-b9fa-07cf28de3e9a) |

## Project Components

| Component                                         | Role                                              | Published                                                                                                     |
|---------------------------------------------------|---------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| [Android Solid Services App](app.md)              | Host app — manages accounts, exposes IPC services | [GitHub Releases](https://github.com/erfangholami/Android-Solid-Services/releases)                            |
| [Android Solid Services API Library](api-library.md)       | Direct Solid server communication                 | [Maven Central](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/api)       |
| [Android Solid Services Client Library](client-library.md) | IPC client for third-party apps                   | [Maven Central](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/client) |

## Found a Bug? Have a Question?

We welcome issues and contributions. If something isn't working or you have a suggestion, please [open an issue on GitHub](https://github.com/erfangholami/Android-Solid-Services/issues) — it helps us improve the project for everyone.

## Acknowledgments

Thanks to funding from [NLnet](https://nlnet.nl/).
