using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;

internal static class Program
{
    // Usage:
    // ChatGptResponseWatcher.exe --out "C:\path\to\fixes.json" --timeout 600 --log "C:\path\to\watcher.log" --clear-clipboard
    [STAThread]
    private static int Main(string[] args)
    {
        string outPath = null;
        int timeoutSec = 600;
        string logPath = null;
        bool clearClipboard = false;

        for (int i = 0; i < args.Length; i++)
        {
            var arg = args[i];
            if (arg == "--out" && i + 1 < args.Length) { outPath = args[++i]; continue; }
            if (arg == "--timeout" && i + 1 < args.Length && int.TryParse(args[++i], out var t)) { timeoutSec = Math.Max(1, t); continue; }
            if (arg == "--log" && i + 1 < args.Length) { logPath = args[++i]; continue; }
            if (arg == "--clear-clipboard") { clearClipboard = true; continue; }
        }

        if (string.IsNullOrWhiteSpace(outPath))
        {
            Console.Error.WriteLine("Missing --out <file>.");
            return 2;
        }

        Logger.Init(logPath);
        Logger.Info($"Watching clipboard for valid fixes JSON… (timeout {timeoutSec}s)");
        Logger.Info($"Output file (will overwrite on success): {outPath}");

        if (clearClipboard)
        {
            TryClearClipboard();
            Logger.Info("Clipboard cleared at start.");
        }

        var deadline = DateTime.UtcNow.AddSeconds(timeoutSec);
        string lastHash = null;
        int lastShownSecLeft = int.MaxValue;

        while (DateTime.UtcNow < deadline)
        {
            var secLeft = (int)Math.Max(0, (deadline - DateTime.UtcNow).TotalSeconds);
            if (secLeft != lastShownSecLeft && secLeft % 5 == 0)
            {
                Logger.Info($"Time remaining: {secLeft}s");
                lastShownSecLeft = secLeft;
            }

            Application.DoEvents();
            Thread.Sleep(400);

            string text = TryGetClipboardText();
            if (string.IsNullOrWhiteSpace(text)) continue;

            var hash = Hash(text);
            if (hash == lastHash) continue;
            lastHash = hash;

            string validationError;
            if (IsValidFixesJson(text, out validationError))
            {
                try
                {
                    var dir = Path.GetDirectoryName(outPath);
                    if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);

                    // Atomic overwrite: write temp then replace
                    var tmp = outPath + ".tmp";
                    File.WriteAllText(tmp, text, new UTF8Encoding(encoderShouldEmitUTF8Identifier: false));
                    if (File.Exists(outPath)) File.Delete(outPath);
                    File.Move(tmp, outPath);

                    Logger.Info($"Captured JSON → {outPath}");
                    return 0;
                }
                catch (Exception ex)
                {
                    Logger.Error($"Failed to write JSON: {ex.Message}");
                }
            }
            else
            {
                Logger.Warn($"Clipboard not valid JSON yet: {validationError}");
            }
        }

        Logger.Warn("Timeout waiting for valid fixes JSON.");
        return 1;
    }

    private static string TryGetClipboardText()
    {
        try
        {
            if (Clipboard.ContainsText(TextDataFormat.UnicodeText))
                return Clipboard.GetText(TextDataFormat.UnicodeText);
            if (Clipboard.ContainsText(TextDataFormat.Text))
                return Clipboard.GetText(TextDataFormat.Text);
        }
        catch { }
        return null;
    }

    private static void TryClearClipboard()
    {
        try { Clipboard.SetText(string.Empty, TextDataFormat.UnicodeText); } catch { }
    }

    // Strict validation: single JSON array of { "path": string, "content": string }, no fences/prose
    private static bool IsValidFixesJson(string raw, out string error)
    {
        error = null;
        if (string.IsNullOrWhiteSpace(raw)) { error = "clipboard empty"; return false; }
        raw = raw.Trim();

        if (!(raw.StartsWith("[") && raw.EndsWith("]"))) { error = "must start with [ and end with ]"; return false; }

        try
        {
            using var doc = JsonDocument.Parse(raw);
            if (doc.RootElement.ValueKind != JsonValueKind.Array) { error = "top-level value is not an array"; return false; }

            int idx = 0;
            foreach (var item in doc.RootElement.EnumerateArray())
            {
                idx++;
                if (item.ValueKind != JsonValueKind.Object) { error = $"item #{idx} is not an object"; return false; }

                if (!item.TryGetProperty("path", out var p) || p.ValueKind != JsonValueKind.String) { error = $"item #{idx} missing string 'path'"; return false; }
                if (!item.TryGetProperty("content", out var c) || c.ValueKind != JsonValueKind.String) { error = $"item #{idx} missing string 'content'"; return false; }

                var pathStr = p.GetString();
                var contentStr = c.GetString();
                if (string.IsNullOrWhiteSpace(pathStr)) { error = $"item #{idx} has empty 'path'"; return false; }
                if (contentStr == null) { error = $"item #{idx} has null 'content'"; return false; }

                if (Regex.IsMatch(contentStr, @"^\s*```")) { error = $"item #{idx} 'content' appears to be wrapped in code fences"; return false; }
            }
            return true;
        }
        catch (Exception ex)
        {
            error = "JSON parse: " + ex.Message;
            return false;
        }
    }

    private static string Hash(string s)
    {
        using var sha = SHA256.Create();
        return BitConverter.ToString(sha.ComputeHash(Encoding.UTF8.GetBytes(s)));
    }

    private static class Logger
    {
        private static string _logPath;

        public static void Init(string logPath)
        {
            _logPath = logPath;
            if (!string.IsNullOrEmpty(_logPath))
            {
                try
                {
                    var dir = Path.GetDirectoryName(_logPath);
                    if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
                    File.WriteAllText(_logPath, $"[watcher] started {DateTime.Now:yyyy-MM-dd HH:mm:ss}\r\n", new UTF8Encoding(false));
                }
                catch { _logPath = null; }
            }
        }

        public static void Info(string msg) => Write("INFO", msg);
        public static void Warn(string msg) => Write("WARN", msg);
        public static void Error(string msg) => Write("ERROR", msg);

        private static void Write(string level, string msg)
        {
            var line = $"[watcher] {level} {DateTime.Now:HH:mm:ss}  {msg}";
            Console.WriteLine(line);
            if (!string.IsNullOrEmpty(_logPath))
            {
                try { File.AppendAllText(_logPath, line + "\r\n", new UTF8Encoding(false)); } catch { }
            }
        }
    }
}
