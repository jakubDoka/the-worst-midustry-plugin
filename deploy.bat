@echo off
set user=mlokistws
set serverDir=/home/7T/config/mods
set configParentDir=C:\Users\jakub\Documents\programming\java\mindustry6\ms\config\mods
set command="C:/Program Files/Putty/pscp"

deployTemplate %1 %user% %serverDir% %configParentDir% %command%