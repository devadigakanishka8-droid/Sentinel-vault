# SentinelVault

Secure file vault in Java: AES-256-GCM file encryption, BCrypt password hashing, role-based access (Owner/Editor/Viewer) and an audit log. Offline desktop app, SQLite database.

## Setup
1. Install JDK 17 or newer (JDK 21 recommended).
2. Download these 3 jars into the `lib/` folder:
   - https://repo1.maven.org/maven2/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar
   - https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.46.1.3/sqlite-jdbc-3.46.1.3.jar
   - https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar
3. Windows: run `build.bat`, then `run.bat` to open the app (`test.bat` runs the backend test). Mac/Linux: `./build.sh`, then `./run.sh` (`./test.sh` for the test).
4. Always start the app from the project folder, so it finds `assets/` (logo) and the database.

First launch: type a master passphrase (it protects every file key - do not forget it), then create the Owner account.

## Structure
- model: Role, User, FileEntry, AuditLogEntry
- security: EncryptionUtil, PasswordUtil, PermissionService
- db: DatabaseManager, UserDAO, FileDAO, AuditLogDAO
- service: FileService, UserService, AuditService
- ui: Swing screens (App is the entry point), colours #101826 and #F1EAD9
- TestBackend: end-to-end test


## Two laptops (client / server, encrypted with TLS)
One laptop holds the vault and runs the server; other laptops connect to it over the same Wi-Fi or hotspot.

1. Laptop 1 (vault): `server.bat`. Enter the master passphrase. On the first run it creates `tls/server.p12` (private, stays on this laptop) and `tls/server.crt` (public certificate). The window shows the address, for example `192.168.1.20:5050`. Allow Java through the Windows firewall when asked.
2. Copy `tls/server.crt` from laptop 1 into a folder named `tls` in the project on each client laptop (pen drive or WhatsApp is fine, it is a public file).
3. Client laptop: `client.bat 192.168.1.20:5050` (or run `client.bat` and type the address).
4. Sign in as usual. All rules (roles, lockout, encryption, audit log) run on the server.

The link is encrypted with TLS 1.3. Clients trust only the one certificate you copied, so another computer on the network cannot pretend to be the server. Never copy `server.p12` to clients and never commit the `tls` folder.
