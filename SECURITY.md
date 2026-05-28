# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in Spire, please report it responsibly rather than opening a public GitHub issue.

Contact: hello@bylinnea.dev

Please include:
- A description of the vulnerability
- Steps to reproduce it
- Any potential impact you've identified

I'll aim to respond within a few days and will keep you updated on the fix.

## Scope

This is a personal open source project. The main security considerations are:

- **API keys** are stored in Android's `EncryptedSharedPreferences` and never hardcoded in the source code
- **Backup files** are created locally and only shared by the user's explicit action
- **No data is collected** — all plant data stays on the user's device
- **Network requests** are only made to Anthropic and PlantNet APIs, and only when the user has provided their own API keys and initiates an action
