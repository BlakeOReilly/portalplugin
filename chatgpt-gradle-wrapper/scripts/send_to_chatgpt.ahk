; --- scripts/send_to_chatgpt.ahk (AutoHotkey v2) ---
; Usage:
;   send_to_chatgpt.ahk "<ChatGPT.exe or '-'>" "<message.txt>" <timeoutMs> "<windowTitle>"

#Requires AutoHotkey v2.0

if (A_Args.Length < 4) {
    MsgBox("Usage:`n  send_to_chatgpt.ahk `"<ChatGPT.exe or '-'>`" `"<message.txt>`" <timeoutMs> `"<windowTitle>`"")
    ExitApp 1
}

exePath   := A_Args[1]
msgFile   := A_Args[2]
timeoutMs := Integer(A_Args[3])
winTitle  := A_Args[4]

if !FileExist(msgFile) {
    MsgBox("Message file not found: " msgFile)
    ExitApp 2
}

; Launch if exePath is provided and exists; '-' means "no exe"
if (exePath != "-" && FileExist(exePath)) {
    try {
        Run(exePath)
    } catch e {
        ; ignore if already running / cannot start
    }
}

; Try to find window by title; if exe given, also try process name as fallback
if !WinWait(winTitle, , timeoutMs/1000.0) {
    if (exePath != "-" && FileExist(exePath)) {
        exeName := StrSplit(exePath, "\").Pop()
        if !WinWait("ahk_exe " exeName, , 2) {
            MsgBox("Could not find a window titled '" winTitle "' or ahk_exe '" exeName "' within timeout.")
            ExitApp 3
        }
        WinActivate("ahk_exe " exeName)
    } else {
        MsgBox("Could not find a window titled '" winTitle "' within timeout.")
        ExitApp 3
    }
} else {
    WinActivate(winTitle)
}

; Copy text and send
txt := FileRead(msgFile, "UTF-8")
A_Clipboard := ""
A_Clipboard := txt
if !ClipWait(2) {
    MsgBox("Failed to set clipboard.")
    ExitApp 4
}

; New chat to ensure focus, then paste+send twice for reliability
Send("^n")
Sleep(250)
Send("^v")
Sleep(150)
Send("{Enter}")
Sleep(300)
Send("^v")
Sleep(150)
Send("{Enter}")

ExitApp 0
