
Add-Type -AssemblyName System.Speech
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$s.SelectVoice('Microsoft Hedda Desktop')
$s.Rate = -1
$fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(22050, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
$text = $args[0]
$out = $args[1]
$log = New-Object System.Collections.Generic.List[string]
Register-ObjectEvent -InputObject $s -EventName VisemeReached -Action { $e = $Event.SourceEventArgs; $global:vl += ("V`t" + $e.AudioPosition.TotalMilliseconds + "`t" + $e.Viseme + "`t" + $e.Duration.TotalMilliseconds) } | Out-Null
Register-ObjectEvent -InputObject $s -EventName PhonemeReached -Action { $e = $Event.SourceEventArgs; $global:vl += ("P`t" + $e.AudioPosition.TotalMilliseconds + "`t" + $e.Phoneme + "`t" + $e.Duration.TotalMilliseconds) } | Out-Null
$global:vl = @()
$s.SetOutputToWaveFile($out, $fmt)
$s.Speak($text)
Start-Sleep -Milliseconds 300
$s.SetOutputToNull()
$global:vl | Set-Content -Encoding UTF8 ($out + '.sapi.txt')
