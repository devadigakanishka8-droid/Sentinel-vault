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

## Team SE_A_06
Guide: Prof. Rujata Chaudhari
