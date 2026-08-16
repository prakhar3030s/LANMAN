# LocalLink

LocalLink is a hyper-local, decentralized messaging application designed with a strict "Zero Cloud Footprint" philosophy. It allows devices on the same Local Area Network (LAN) or Wi-Fi network to discover each other, pair securely via QR codes, and exchange end-to-end encrypted messages and files directly — without any intermediate servers, cloud databases, or internet requirements.

## Architecture

LocalLink is built using a fully reactive, offline-first architecture on Android:

1. **Discovery Layer**: Uses UDP Multicast (Port 4444) to continuously broadcast and discover peers on the local subnet.
2. **Network/Transport Layer**: Establishes direct TCP socket connections (Port 4445) between devices for reliable message transmission.
3. **Protocol Layer**: Defines a binary framing protocol (Magic Header + Size + Payload) capable of handling Text, Ping/Pong, and chunked File Transfers.
4. **Security Layer**: Integrates `libsignal-client` for true forward-secure, end-to-end encryption. Each contact pairing performs an initial key exchange via QR code (using an out-of-band Ed25519 fingerprint verification) and subsequently uses the Double Ratchet algorithm for all messages.
5. **Data Layer**: A Room database acts as the single source of truth. The application uses a "Store-and-Forward" pattern: outbound messages are written to the database first, then asynchronously picked up by a background dispatcher and pushed over the network when the peer becomes available.

## Key Features

- **Serverless**: No accounts, no phone numbers, no cloud dependencies.
- **End-to-End Encryption**: Powered by the Signal Protocol.
- **Store-and-Forward**: Messages are queued locally and delivered automatically when the recipient connects to the network.
- **File Transfers**: Direct P2P file transfers over encrypted TCP streams, bypassing Android memory limits via chunking.
- **Dynamic UI**: Built completely in Jetpack Compose, featuring a custom "Translucent Monolith" glassmorphic design system powered by the Haze library.

## Recent Updates

### 17 August
- **Progress Ring**: Resolved rendering and state synchronization issues for the transfer progress ring.
- **Voice Notes**: Added the voice note message button (*Known issue*: Triggers an application crash; investigation and fix in progress).

## Building the Project

### Prerequisites
- Android Studio Ladybug or later
- Android SDK 35 (Android 15)
- JDK 17

### Debug Build
Simply open the project in Android Studio and press "Run" (`Shift+F10`), or run the following via command line:
```bash
./gradlew assembleDebug
```

### Release Build (Signed)
LocalLink is configured to use ProGuard/R8 for minification. You must generate your own signing keystore to build a release APK.

1. Generate a keystore:
```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my_alias
```
2. Copy `keystore.properties.template` to `keystore.properties` in the root directory.
3. Fill in the values in `keystore.properties` with the credentials used in Step 1.
4. Build the release APK:
```bash
./gradlew assembleRelease
```

The APK will be available in `app/build/outputs/apk/release/`.

## License

MIT License. See `LICENSE` for details.
