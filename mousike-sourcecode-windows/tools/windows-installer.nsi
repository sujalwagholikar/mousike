Unicode true
!include "MUI2.nsh"
!include "WinVer.nsh"
!include "x64.nsh"
!ifndef APP_DIR
!error "APP_DIR is required"
!endif
!ifndef OUTPUT
!error "OUTPUT is required"
!endif
Name "Mousiké"
OutFile "${OUTPUT}"
InstallDir "$LOCALAPPDATA\Programs\Mousike"
InstallDirRegKey HKCU "Software\Mousike" "InstallDir"
RequestExecutionLevel user
SetCompressor zlib
BrandingText "Mousiké · Sound In motion"
!define MUI_ABORTWARNING
!define MUI_ICON "..\assets\mousike.ico"
!define MUI_UNICON "..\assets\mousike.ico"
!define MUI_FINISHPAGE_RUN "$INSTDIR\Mousike.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Open Mousiké"
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"
Function .onInit
  ${IfNot} ${RunningX64}
    MessageBox MB_OK|MB_ICONSTOP "Mousiké requires 64-bit Windows 10 or Windows 11."
    Abort
  ${EndIf}
  ${IfNot} ${AtLeastWin10}
    MessageBox MB_OK|MB_ICONSTOP "Mousiké requires Windows 10 or Windows 11 (64-bit)."
    Abort
  ${EndIf}
FunctionEnd
Section "Mousiké" SEC_MAIN
  SetShellVarContext current
  SetOutPath "$INSTDIR"
  File /r /x ".*" /x "*.tmp" "${APP_DIR}\*"
  WriteUninstaller "$INSTDIR\Uninstall Mousike.exe"
  CreateDirectory "$SMPROGRAMS\Mousiké"
  CreateShortcut "$SMPROGRAMS\Mousiké\Mousiké.lnk" "$INSTDIR\Mousike.exe"
  CreateShortcut "$DESKTOP\Mousiké.lnk" "$INSTDIR\Mousike.exe"
  WriteRegStr HKCU "Software\Mousike" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mousike" "DisplayName" "Mousiké"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mousike" "DisplayVersion" "3.6.0"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mousike" "Publisher" "Sujal Wagholikar"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mousike" "DisplayIcon" "$INSTDIR\Mousike.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mousike" "UninstallString" '"$INSTDIR\Uninstall Mousike.exe"'
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mousike" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mousike" "NoRepair" 1
SectionEnd
Section "Uninstall"
  SetShellVarContext current
  Delete "$DESKTOP\Mousiké.lnk"
  Delete "$SMPROGRAMS\Mousiké\Mousiké.lnk"
  RMDir "$SMPROGRAMS\Mousiké"
  !include "uninstall-files.nsh"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mousike"
  DeleteRegKey HKCU "Software\Mousike"
SectionEnd
