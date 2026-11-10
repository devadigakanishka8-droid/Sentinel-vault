@echo off
rem Run this on any laptop on the same Wi-Fi. Example:  client.bat 192.168.1.20:5050
java -cp "out;lib\*" com.sentinelvault.ui.ClientApp %1
