; blesk — installer customization

!macro customInstall
  CreateShortCut "$DESKTOP\blesk.lnk" "$INSTDIR\blesk.exe" "" "$INSTDIR\blesk.exe" 0
!macroend

!macro customUnInstall
  Delete "$DESKTOP\blesk.lnk"
!macroend
