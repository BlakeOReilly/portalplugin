# ChatGPT Gradle Wrapper

Automates a Gradle build and, on failure, prepares a ChatGPT-ready message and sends it into the ChatGPT desktop app.

## What it does
1. Runs an optional kickoff command you provide (e.g., `git pull`).
2. Runs the Gradle build (prefers `gradlew.bat`, falls back to `gradle.bat`).
3. Overwrites `.chatgpt/gradle_build.log` with the complete stdout+stderr.
4. Overwrites `.chatgpt/message_to_chatgpt.txt` with your pre-written prompt + the build log.
5. Opens ChatGPT desktop.
6. Pastes the message and hits Enter to send.

## Requirements
- Windows 10/11, PowerShell 5+ (or PowerShell 7)
- Gradle/gradlew available in your project folder
- ChatGPT desktop app installed
- (Recommended) AutoHotkey v2 for reliable UI automation: https://www.autohotkey.com/

## Setup
1. Edit `config.ps1` to point `$ProjectDir` at your Gradle project.
2. Optionally update `$ChatGptExe` if you installed ChatGPT somewhere custom.
3. Optionally install AutoHotkey v2 and ensure `$AutoHotkeyExe` is correct.
4. Customize `chatgpt_prompt.txt` to your preferred “build doctor” prompt.

## Usage
Open PowerShell in this folder:

```powershell
# Example with a kickoff command (Step 1)
.\scripts\run_and_send.ps1 -Command 'git pull'

# Or run without a kickoff command
.\scripts\run_and_send.ps1
