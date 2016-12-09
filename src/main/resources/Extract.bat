@echo off
for /f "delims=" %%a in ("%~1") do (
  echo ----------------
  echo extracting: %%a
  java -jar apngopt.jar  e -i %%a -k ""
  echo extracted: %%a 
)

pause