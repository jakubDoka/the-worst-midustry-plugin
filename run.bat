@echo off
set /p password=<password.txt
putty mlokistws@ggg.sytes.net -pw %password% -P 22