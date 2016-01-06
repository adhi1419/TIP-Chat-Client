@ECHO off
ECHO Compiling all Files...
javac ./tip/adhi/chatclient/*.java
ECHO All files compiled!
set /p clients=Enter the Number of Cients: 
start cmd /k java tip.adhi.chatclient.SimpleServer
FOR /L %%G IN (1,1,%clients%) DO (
	start cmd /k java tip.adhi.chatclient.SimpleClient
)
