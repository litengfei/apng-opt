@echo off
for /f "delims=" %%a in ("%~1") do (
  echo ----------------
  echo rebuilding: %%a
  java -jar AngMaker.jar b -i %%a -pngs %%a.frames/ -o %%a.ang
  echo rebuild finished: %%a.ang
)

pause