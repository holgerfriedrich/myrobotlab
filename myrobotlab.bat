@ECHO OFF

REPO_FILE=libraries/repo.json

rem TODO move vars to top
rem TODO have --id as an optional var

set APPDIR=%CD%
echo APPDIR=%APPDIR%

set CLASSPATH="%CLASSPATH%;%APPDIR%\target\classes\*;%APPDIR%\libraries\jar\*;%APPDIR%\myrobotlab.jar"

echo CLASSPATH=%CLASSPATH%

rem TODO - option to package jdk for now use bin in path
set JAVA=java

rem Processing/Arduino handle this in an array - no need for now
set JAVA_OPTIONS="-Djava.library.path=libraries/native -Djna.library.path=libraries/native -Dfile.encoding=UTF-8"

IF EXIST %REPO_FILE% (
    echo "$REPO_FILE exists."
) ELSE (
    echo "$REPO_FILE does not exist."
    "${JAVA}" ${JAVA_OPTIONS} org.myrobotlab.service.Runtime --from-launcher --install
)

"%JAVA%" %JAVA_OPTIONS% -cp %CLASSPATH% org.myrobotlab.service.Runtime --from-launcher --log-level info -s webgui WebGui intro Intro python Python
