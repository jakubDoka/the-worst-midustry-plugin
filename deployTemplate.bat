@echo off

set type=%1

if "%type%" neq "jar" if "%type%" neq "config" if "%type%" neq "pull" (
    echo Invalid option. Only 'config', 'pull' and 'jar' are available.
    echo config - deploys config to server
    echo jar - deploys plugin executable to server
    echo pull - pulls config from server
    exit /b 1
)

set jarPath=build/libs/the-worst-mindustry-plugin.jar
@rem user name here
set user=%2
@rem path to server mods
set serverDir=%3

set address=%user%@ggg.sytes.net:%serverDir%
set serverConfig=%address%/worst

@rem path to config dir
set configDirParent=%4
set configDir=%configDirParent%/worst
@rem path to command
set command=%5

@rem there has to be password.txt in directory along with this file
set /p password=<password.txt

if "%type%" == "jar" (
    %command% -pw %password% %jarPath% %address%
) else if "%type%" == "pull" (
    %command% -pw %password% -r %serverConfig% %configDirParent%
) else (
    %command% -pw %password% -r %configDir% %address%
)