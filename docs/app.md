# Android Solid Services App

The **Android Solid Services** app is the hub of the ecosystem. It manages your Solid accounts and runs three background bound services that third-party apps connect to over AIDL — giving the whole Android device a single, shared Solid identity layer.

## Features

- **Single sign-in** — one login in ASS; every third-party app on the device reuses that session via IPC.
- **Multi-account** — log in with multiple Solid pod accounts and switch the active one from Settings.
- **Access control** — third-party apps must request a grant; you see the app name and approve or deny from within ASS.
- **Resource management** — proxy CRUD operations on pod resources for connected apps.
- **Contacts data module** — manage address books, contacts, and groups stored on the pod.
- **Resource sharing** *(0.5.0)* — share resources and containers with other people at View / Add / Edit access (WAC or ACP), with share links and a public catalog of given/received shares.
- **Notifications inbox** *(0.5.0)* — a Linked Data Notifications inbox for share offers and access requests, with accept/reject handling.

## Screenshots

| Login | Account switcher | Resource browser | Settings |
|-------|-----------------|-----------------|----------|
| ![Login](https://github.com/user-attachments/assets/9afe2f9d-f4a3-4e05-ae77-ab6e13febf84) | ![Accounts](https://github.com/user-attachments/assets/543b2d9e-2f51-481f-b50d-934ece61172f) | ![Resources](https://github.com/user-attachments/assets/3deabd3a-d907-407a-9fbb-b27e26882206) | ![Settings](https://github.com/user-attachments/assets/b6df9725-321d-4572-b9fa-07cf28de3e9a) |

## How the App Works

When a third-party app wants to use Solid, it binds to one of ASS's AIDL services:

```
Third-party app
      │
      │  (Android IPC / AIDL)
      ▼
Android Solid Services
      ├── ASSAuthenticatorService      ← login, access grants, account switching
      ├── ASSResourceService           ← read / create / update / delete pod resources
      ├── SolidDataModulesService      ← contacts data module (address books, contacts, groups)
      ├── ASSSharingService            ← share / revoke resources, access grants (0.5.0)
      └── ASSNotificationsService      ← Linked Data Notifications inbox (0.5.0)
```

The third-party app never handles tokens or pod credentials directly. ASS performs all authenticated requests on its behalf.

## Installation

Download the latest APK from the [GitHub Releases page](https://github.com/erfangholami/Android-Solid-Services/releases) or [build it from source](getting-started.md).

## Technology Stack

| Area                 | Technology                                                  |
|----------------------|-------------------------------------------------------------|
| UI                   | Jetpack Compose, Navigation Compose                         |
| State / architecture | MVVM, ViewModel, StateFlow                                  |
| Dependency injection | Hilt                                                        |
| IPC                  | Android AIDL bound services                                 |
| Authentication       | OpenID Connect + DPoP or Bearer tokens (`net.openid:appauth`) |
| Persistence          | DataStore + Protocol Buffers (access grants, user profiles); token store encrypted at rest (AES-256-GCM, Android Keystore) |
| Solid protocol       | Custom `SolidHttpClient` (OkHttp-based)                     |
| Min SDK              | 26                                                          |
| Target SDK           | 33                                                          |
| Compile SDK          | 36                                                          |
