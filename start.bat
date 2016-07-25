@echo off
echo [推送服务]启动

set _javaOpts=-Xms512m -Xmx1024m
set _javaHome=C:\jdk1.8.0_66\
set _runDir=%~dp0%

set _runLibDir=%_runDir%runlib
setlocal enabledelayedexpansion 
set _classpath=
for %%i in (%_runLibDir%\*.jar) do (
  set _classpath=%%i;!_classpath!
)

echo =================================================
echo Using Duser.dir: %_runDir%
echo Using JAVA_HOME: %_javaHome%
echo Using JAVA_OPTS: %_javaOpts%
echo Usiig CLASSPATH: %_classpath%
echo =================================================
echo Start Command:
echo   %_javaHome%bin\java -Duser.dir=%DIR% %_javaOpts% -classpath %_classpath%PushCenter.jar com.woting.push.ServerStart
echo =================================================

%_javaHome%bin\java -Duser.dir=%DIR% %_javaOpts% -classpath %_classpath%PushCenter.jar com.woting.push.ServerStart 2>&1 >> push.log

