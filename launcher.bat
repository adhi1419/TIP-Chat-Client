@ECHO off
ECHO Compiling all Files...
javac *.java
ECHO All files compiled!
set /p clients=Enter the Number of Cients: 
start cmd /k java SimpleServer
FOR /L %%G IN (1,1,%clients%) DO (
	start cmd /k java SimpleClient
)
