#Requires AutoHotkey v2.0
#SingleInstance Force
; send_to_chatgpt.ahk "<ChatGPT.exe or '-'>" "<message.txt>" <timeoutMs> "<windowTitle>"

SetTitleMatchMode 2
SendMode "Input"
CoordMode "Mouse", "Screen"

if (A_Args.Length < 4) {
    MsgBox("Usage:`n  send_to_chatgpt.ahk `"<ChatGPT.exe or '-'>`" `"<message.txt>`" <timeoutMs> `"<windowTitle>`"")
    ExitApp 1
}

exePath   := A_Args[1]
msgFile   := A_Args[2]
timeoutMs := Integer(A_Args[3])
winTitle  := A_Args[4]

try msg := FileRead(msgFile, "UTF-8") catch {
    MsgBox("Cannot read message file: " msgFile), ExitApp 2
}
msg := Trim(msg, "`r`n")

if (exePath != "-" && FileExist(exePath))
    try Run(exePath)

conds := ["ahk_exe ChatGPT.exe ahk_class Chrome_WidgetWin_1","ahk_exe ChatGPT.exe"]
if (winTitle && winTitle != "-") conds.Push(winTitle)

target := ""
deadline := A_TickCount + timeoutMs
for cond in conds {
    while (A_TickCount < deadline)
        if WinWait(cond, , 0.4) { target := cond, break }
    if target
        break
}
if !target
    MsgBox("Could not find ChatGPT window."), ExitApp 3

WinActivate target
WinShow target
if !WinWaitActive(target, , 2)
    MsgBox("ChatGPT window did not activate."), ExitApp 4

for ctrl in ["Chrome_RenderWidgetHostHWND1","Chrome_RenderWidgetHostHWND2"]
    try ControlFocus(ctrl, target)

FocusComposerSmart(target)

ClipSaved := ClipboardAll()
A_Clipboard := ""
A_Clipboard := msg
useSendText := !ClipWait(1)

Send("^a")
Sleep(80)
if useSendText
    SendText(msg)
else
    Send("^v")

Sleep(150)
Send("^Enter")
Sleep(220)
Send("^Enter")

try Clipboard := ClipSaved
ExitApp 0

; ---- Helpers ----
FocusComposerSmart(winTitle) {
    Send("{Esc}")
    Sleep(120)
    Send("^n")
    Sleep(350)

    WinGetPos &wx, &wy, &ww, &wh, winTitle
    thresholdY := wy + Round(wh * 0.65)

    ; Try keyboard first: walk focus until caret appears low in the window
    Loop 8 {
        Send("{Tab}")
        Sleep(110)
        Send("{Space}")
        Sleep(50)
        Send("{BS}")
        cx := 0, cy := 0
        if CaretGetPos(&cx, &cy) && (cy >= thresholdY)
            return
    }

    ; Fallback: click well above the toolbar, then re-check caret
    for offset in [320, 260, 200, 160] {
        Click wx + (ww // 2), wy + wh - offset
        Sleep(150)
        Send("{Space}")
        Sleep(50)
        Send("{BS}")
        cx := 0, cy := 0
        if CaretGetPos(&cx, &cy) && (cy >= thresholdY)
            return
    }
}
