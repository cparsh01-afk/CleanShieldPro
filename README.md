# Parsh CleanShieldPro

Android privacy utility built with Kotlin and Android Gradle Plugin.

## Current implementation scope

- Local DNS-only `VpnService` design. The VPN route is limited to the virtual DNS resolver address; ordinary internet traffic is not routed through the TUN interface.
- IPv4/UDP DNS query parsing and response construction with bounds checks.
- Cloudflare and Quad9 DNS-over-HTTPS providers, plus a custom HTTPS endpoint.
- DoH sockets are protected with `VpnService.protect()` to prevent VPN recursion.
- TLS certificate verification remains enabled through OkHttp defaults.
- Local domain block rules and whitelist; whitelist rules take precedence.
- Aggregate DNS counters only. DNS names and query payloads are not persisted.
- Dashboard reports DNS counters, connectivity, RAM, storage and battery percentage using Android APIs.

## Important limitations

This initial engine processes **IPv4/UDP DNS only**. It does not yet implement DNS-over-TCP or IPv6 DNS packet processing. Applications using their own DNS-over-HTTPS/DNS-over-TLS clients, hard-coded resolvers, or encrypted DNS inside an app may bypass this local filter. Some apps may use TCP fallback for DNS; test compatibility on the target device before relying on the VPN.

DNS filtering cannot block every advertisement, including many in-app and video advertisements. DNS-over-HTTPS encrypts DNS lookups between this app and the selected resolver; it does not hide destination IP addresses, traffic timing, or all network metadata from the network operator.

No firewall bypass, traffic interception beyond the declared DNS route, CPU/GPU overclocking, or forced app killing is implemented.

## Build with GitHub Actions (no Android Studio required)

Push to the `master` branch or manually run **Build Parsh CleanShieldPro APK** from the Actions tab. The workflow uses JDK 17, Android SDK 34 and Gradle 8.2. Download the `Parsh-CleanShieldPro` artifact from the completed workflow run.

Application ID remains `com.cleanshield.pro` to preserve update compatibility with the existing app.

## Device testing checklist

1. Install the generated APK on the target device.
2. Grant the Android VPN permission when prompted.
3. Start protection and verify Android shows the VPN key icon and the app reports active.
4. Open ordinary websites and apps; verify connectivity remains available.
5. Test a known blocked test domain and confirm the blocked counter increments.
6. Stop protection and verify normal DNS/network operation resumes.
7. Repeat with Cloudflare and Quad9; custom providers must use a valid HTTPS DoH endpoint.

Do not treat a successful APK compilation as proof of correct packet behavior. Validate on-device before daily use.
