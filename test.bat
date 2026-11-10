   @echo off
   rem Runs the backend test in a temporary folder, so it never touches your real vault.
   set ROOT=%~dp0
   if exist "%TEMP%\sv-test" rmdir /s /q "%TEMP%\sv-test"
   mkdir "%TEMP%\sv-test"
   pushd "%TEMP%\sv-test"
   java -cp "%ROOT%out;%ROOT%lib\*" com.sentinelvault.TestBackend
   popd