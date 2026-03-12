param (
    [int]$MvcPid,
    [int]$FluxPid,
    [int]$Duration = 120
)

$OutputFile = "perf_report.csv"
"Time,MVC_CPU,MVC_MEM(MB),FLUX_CPU,FLUX_MEM(MB)" | Out-File -FilePath $OutputFile -Encoding ascii

Write-Host "--- Start Monitoring --- Duration: $Duration s" -ForegroundColor Cyan

# Get Process Objects
$MvcProc = Get-Process -Id $MvcPid -ErrorAction SilentlyContinue
$FluxProc = Get-Process -Id $FluxPid -ErrorAction SilentlyContinue

if (-not $MvcProc -or -not $FluxProc) {
    Write-Host "Error: Cannot find one or both PIDs. Check jps -l again." -ForegroundColor Red
    exit
}

for ($i = 1; $i -le $Duration; $i++) {
    $StartTime = Get-Date

    # Sample 1
    $MvcTime1 = $MvcProc.TotalProcessorTime.TotalSeconds
    $FluxTime1 = $FluxProc.TotalProcessorTime.TotalSeconds

    Start-Sleep -Milliseconds 500

    # Sample 2
    $MvcProc.Refresh()
    $FluxProc.Refresh()
    $MvcTime2 = $MvcProc.TotalProcessorTime.TotalSeconds
    $FluxTime2 = $FluxProc.TotalProcessorTime.TotalSeconds

    $EndTime = Get-Date
    $Interval = ($EndTime - $StartTime).TotalSeconds

    # Calc CPU %
    $MvcCpu = (($MvcTime2 - $MvcTime1) / $Interval / $env:NUMBER_OF_PROCESSORS) * 100
    $FluxCpu = (($FluxTime2 - $FluxTime1) / $Interval / $env:NUMBER_OF_PROCESSORS) * 100

    # Get Memory (RSS)
    $MvcMem = $MvcProc.WorkingSet64 / 1MB
    $FluxMem = $FluxProc.WorkingSet64 / 1MB

    # Formatted Strings for Output
    $MvcCpuF = $MvcCpu.ToString("F2")
    $FluxCpuF = $FluxCpu.ToString("F2")
    $MvcMemF = $MvcMem.ToString("F2")
    $FluxMemF = $FluxMem.ToString("F2")

    # Save to CSV
    "$i,$MvcCpuF,$MvcMemF,$FluxCpuF,$FluxMemF" | Out-File -FilePath $OutputFile -Append -Encoding ascii

    # Print to Console
    $log = "[$i s] MVC: CPU $MvcCpuF% MEM $MvcMemF MB | FLUX: CPU $FluxCpuF% MEM $FluxMemF MB"
    Write-Host $log

    Start-Sleep -Milliseconds 500
}

Write-Host "--- Monitoring Complete ---" -ForegroundColor Green