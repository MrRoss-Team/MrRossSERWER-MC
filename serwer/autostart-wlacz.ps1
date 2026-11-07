$taskName = 'EasyTP Serwer Minecraft 24-7'
$serverDir = $PSScriptRoot
$liveDir = 'C:\mc-serwer'
if (($PSScriptRoot -ne $liveDir) -and (Test-Path -LiteralPath (Join-Path $liveDir 'start-serwer.bat'))) {
	Write-Host "Zywy serwer jest w $liveDir, wiec autostart ustawiam dla tego folderu."
	$serverDir = $liveDir
}
$bat = Join-Path $serverDir 'start-serwer.bat'
if (-not (Test-Path -LiteralPath $bat)) {
	Write-Host "Nie znaleziono pliku $bat"
	exit 1
}
$user = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$arguments = '/c start "Serwer Minecraft 24-7" /min cmd /c ""{0}" auto"' -f $bat

try {
	$action = New-ScheduledTaskAction -Execute 'cmd.exe' -Argument $arguments -WorkingDirectory $serverDir
	$trigger = New-ScheduledTaskTrigger -AtLogOn -User $user
	$trigger.Delay = 'PT30S'
	$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -MultipleInstances IgnoreNew
	$principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited
	Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Description 'Uruchamia serwer Minecraft (EasyTP) po zalogowaniu i wlacza go ponownie po awarii.' -Force -ErrorAction Stop | Out-Null
	Write-Host "Gotowe: zadanie '$taskName' uruchomi serwer po kazdym zalogowaniu (z opoznieniem 30 s). Usuniecie: autostart-wylacz.bat"
} catch {
	Write-Host ('Nie udalo sie utworzyc zadania w Harmonogramie: ' + $_.Exception.Message)
	Write-Host 'Probuje zwyklego autostartu (skrot w folderze Autostart)...'
	try {
		$startup = [Environment]::GetFolderPath('Startup')
		$link = (New-Object -ComObject WScript.Shell).CreateShortcut((Join-Path $startup 'EasyTP Serwer Minecraft 24-7.lnk'))
		$link.TargetPath = 'cmd.exe'
		$link.Arguments = $arguments
		$link.WorkingDirectory = $serverDir
		$link.WindowStyle = 7
		$link.Save()
		Write-Host "Gotowe: skrot w folderze Autostart ($startup). Usuniecie: autostart-wylacz.bat"
	} catch {
		Write-Host ('Nie udalo sie utworzyc autostartu: ' + $_.Exception.Message)
		exit 1
	}
}
