# Anomess

Anomess is a secure, anonymous messaging application built for Android using Kotlin and Jetpack Compose. It prioritizes user privacy and security by leveraging Tor for network anonymity and robust encryption standards for data storage and transmission.

## Features

- **Anonymous Communication**: Integrated with **Tor** (The Onion Router) to ensure network traffic analysis protection and anonymity.
- **Secure Encryption**: Utilizes industry-standard encryption protocols (Google Tink) to secure messages.
- **Encrypted Local Storage**: All local data is encrypted using **SQLCipher**, ensuring that your messages are safe even if your device is compromised.
- **Modern UI**: Built with **Jetpack Compose** (Material 3) for a fluid, responsive, and beautiful user interface.
- **Media Sharing**: Support for securely sending images and media.
- **QR Code Pairing**: Securely share contact information via QR codes using ZXing.

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Network Anonymity**: Tor (via `tor-android` and `jtorctl`)
- **Database**: Room with SQLCipher
- **Image Loading**: Coil
- **Security**: AndroidX Security Crypto, Google Tink
- **Architecture**: MVVM

## Getting Started

### Prerequisites

- Android Studio Iguana or newer.
- JDK 17+.
- Android SDK API Level 34.

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/0wver/Anomes.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle projects to download dependencies.
4. Run the application on an emulator or physical device.

> **Note**: Due to the usage of Tor, the initial network connection might take a few moments to bootstrap.

## Security

Anomess takes security seriously:
- **At Rest**: Data is stored using SQLCipher 256-bit AES encryption.
- **In Transit**: Traffic is routed through the Tor network for anonymity.

## Contributing

Contributions are welcome! Please fork the repository and submit a pull request for any improvements or bug fixes.
