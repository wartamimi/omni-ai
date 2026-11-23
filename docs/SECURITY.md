# Security and privacy

## What OmniAI does with your data
- **No OmniAI server, no analytics, no crash reporting, no ads.** The app only connects to the provider
  endpoints you configure.
- **API keys** are encrypted with AES-256-GCM. The encryption key lives in the Android Keystore
  (hardware-backed where the device supports it) and can't be exported. Encrypted keys are stored in
  app-private, no-backup storage. The database only holds a label and a masked hint (e.g. `sk-…3f9a`).
- Keys are never written to logs, exports, backups, notifications or error messages. Error text from
  providers is passed through a redactor that removes anything that looks like a key or bearer token.
- **Conversations, prompts, personas and usage** stay in the app's private database. They're excluded from
  Android backup and device transfer. Export files (JSON/Markdown/CSV) are created only when you ask, and
  never contain API keys; custom headers that look like secrets are blanked.
- **Attachments** are copied into private storage. Images are re-encoded (removing EXIF data such as GPS).
  Documents are converted to text on the device; only that text is sent.

## Network protections
- TLS certificates are validated against the system trust store. User-installed CAs aren't trusted.
- `http://` is refused for public hosts. For local servers it's allowed only for private/loopback addresses
  and only when you enable it in Settings › Network.
- Requests to private, loopback, link-local and cloud-metadata addresses are blocked at DNS resolution time
  unless you allow local-network endpoints. Redirects are not followed.
- Requests are retried only on rate limits or connection failures before the provider starts answering,
  so a request is never billed twice by a retry.
- The optional API request log (Debug mode) records method, host/path, status and timing only, with
  credentials redacted, and is kept in memory.

## Tools
Tool calling is off by default. Built-in tools are local and side-effect free (calculator, date/time,
JSON formatter, text statistics, unit converter). OmniAI never runs code or shell commands from model
output. Each tool can be set to allow, ask or deny, and every execution is logged.

## Limits you should know about
Storing a secret on a phone is never perfect:
- On a rooted or compromised device, an attacker with code execution in the app's process can use the
  Keystore key to decrypt your API keys. Hardware-backed keys prevent copying the key off the device, not
  using it on the device.
- Anything you paste is briefly on the clipboard. OmniAI marks copied text as sensitive (Android 13+ hides it
  from previews), but other apps with clipboard access may still read it.
- A key is sent to the provider with every request. If you configure a wrong or malicious base URL, that
  server receives your key. Check URLs carefully, especially for custom providers.
- Use provider-side protections: spending limits, per-project keys, key rotation, and revoke keys you no
  longer use. Deleting a key in OmniAI does not revoke it at the provider.

## Reporting a vulnerability
Please open a private security advisory on the GitHub repository instead of a public issue.
