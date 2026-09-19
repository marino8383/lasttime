#Requires -Version 5.1
<#
  pubblica.ps1 - Last Time
  Compila l'APK su GitHub Actions e lo copia nella cartella Drive.
  Doppio clic su pubblica.cmd (il doppio clic su un .ps1 apre Blocco note).
#>

param(
    # Niente domande e niente pausa finale: serve per lanciarlo da remoto.
    [switch]$Auto,
    [string]$Messaggio
)

$Repo     = $PSScriptRoot
$Dest     = 'G:\Il mio Drive\App\LastTime'
$Artifact = 'lasttime-debug-apk'
$Workflow = 'build.yml'

function Step($t) { Write-Host "`n>> $t" -ForegroundColor Cyan }
function Ok($t)   { Write-Host "   $t" -ForegroundColor Green }
function Info($t) { Write-Host "   $t" -ForegroundColor Gray }
function Warn($t) { Write-Host "   $t" -ForegroundColor Yellow }
function Fine($code) { if (-not $Auto) { Read-Host "`nPremi INVIO per chiudere" | Out-Null }; exit $code }
function Fail($t) { Write-Host "`n!! $t" -ForegroundColor Red; Fine 1 }

Write-Host "===  Last Time - pubblica APK  ===" -ForegroundColor White
Set-Location $Repo

# --- prerequisiti -----------------------------------------------------------
Step "Controllo prerequisiti"
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "`n!! Manca la CLI di GitHub." -ForegroundColor Red
    Write-Host "   Installala con:  winget install --id GitHub.cli -e" -ForegroundColor Yellow
    Write-Host "   Poi esegui una volta:  gh auth login" -ForegroundColor Yellow
    Fine 1
}
gh auth status
if ($LASTEXITCODE -ne 0) { Fail "Non sei autenticato. Esegui:  gh auth login" }
Ok "gh presente e autenticato"

# --- versione ---------------------------------------------------------------
$ver = "0.0"
$gradle = Get-Content (Join-Path $Repo 'app\build.gradle.kts') -Raw
if ($gradle -match 'versionName\s*=\s*"([^"]+)"') { $ver = $Matches[1] }
Info "versionName = $ver"

# --- modifiche non committate -----------------------------------------------
Step "Controllo lo stato di git"
$dirty = git status --porcelain
if ($dirty) {
    Warn "Ci sono modifiche non committate:"
    git status --short
    Write-Host ""
    if ($Auto) {
        $msg = if ([string]::IsNullOrWhiteSpace($Messaggio)) { "Build APK v$ver" } else { $Messaggio }
        Info "Modalita' automatica: committo con -> $msg"
        git add -A
        if ($LASTEXITCODE -ne 0) { Fail "git add fallito." }
        git commit -m $msg
        if ($LASTEXITCODE -ne 0) { Fail "git commit fallito." }
        Ok "Commit creato."
    } else {
    $ans = Read-Host "Committo e pusho queste modifiche? [s/N]"
    if ($ans -match '^[sSyY]') {
        $msg = Read-Host "Messaggio di commit (INVIO per 'Build APK v$ver')"
        if ([string]::IsNullOrWhiteSpace($msg)) { $msg = "Build APK v$ver" }
        git add -A
        if ($LASTEXITCODE -ne 0) { Fail "git add fallito." }
        git commit -m $msg
        if ($LASTEXITCODE -ne 0) { Fail "git commit fallito." }
        Ok "Commit creato."
    } else {
        Warn "Non committo. La CI compilera' l'ultimo commit gia' pushato,"
        Warn "quindi le modifiche qui sopra NON finiranno nell'APK."
        $go = Read-Host "Procedo lo stesso? [s/N]"
        if ($go -notmatch '^[sSyY]') { Write-Host "`nAnnullato."; Fine 0 }
    }
    }
} else {
    Ok "Working tree pulito."
}

Step "Push su main"
git push origin main
if ($LASTEXITCODE -ne 0) { Fail "git push fallito." }

$sha = (git rev-parse HEAD).Trim()
Info "commit $($sha.Substring(0,7))"

# --- run della CI -----------------------------------------------------------
Step "Cerco la build su GitHub Actions"
# Lo sha viaggia dentro l'URL e l'espressione jq non contiene virgolette: un filtro
# con le virgolette dentro non funziona, perche' PowerShell le toglie prima di
# consegnare l'argomento a gh e jq finisce per leggere lo sha come una funzione.
$apiPath = "repos/{owner}/{repo}/actions/workflows/$Workflow/runs?head_sha=$sha"
$id = ""
$idNum = [int64]0
for ($i = 0; $i -lt 40; $i++) {
    $id = (gh api $apiPath --jq '.workflow_runs[0].id // empty' | Out-String).Trim()
    if ([int64]::TryParse($id, [ref]$idNum)) { break }
    $id = ""
    if ($i -eq 0) { Info "La build non e' ancora partita, aspetto..." }
    Start-Sleep -Seconds 3
}
if (-not $id) { Fail "Nessuna build trovata per questo commit dopo 2 minuti. Controlla il tab Actions su GitHub." }

$statoRun = (gh run view $id --json status --jq '.status' | Out-String).Trim()
$esito = (gh run view $id --json conclusion --jq '.conclusion' | Out-String).Trim()
if ($statoRun -eq 'completed' -and $esito -eq 'success') {
    Ok "Build gia' pronta (run $id), la riuso."
} else {
    Info "Run $id in corso, aspetto che finisca (di solito 2-4 minuti)..."
    if ($Auto) {
        # gh run watch ridisegna lo schermo con sequenze ANSI: in un output catturato
        # diventa illeggibile. Qui basta richiedere lo stato ogni dieci secondi.
        for ($w = 0; $w -lt 60; $w++) {
            Start-Sleep -Seconds 10
            $statoRun = (gh run view $id --json status --jq '.status' | Out-String).Trim()
            if ($statoRun -eq 'completed') { break }
        }
        if ($statoRun -ne 'completed') { Fail "La build non e' finita entro 10 minuti." }
        $esito = (gh run view $id --json conclusion --jq '.conclusion' | Out-String).Trim()
        if ($esito -ne 'success') {
            Write-Host "`n!! La build e' FALLITA. Ultimi errori:" -ForegroundColor Red
            gh run view $id --log-failed
            Fine 1
        }
    } else {
        gh run watch $id --exit-status
        if ($LASTEXITCODE -ne 0) {
            Write-Host "`n!! La build e' FALLITA. Ultimi errori:" -ForegroundColor Red
            gh run view $id --log-failed
            Fine 1
        }
    }
    Ok "Build completata."
}

# --- download e copia in Drive ----------------------------------------------
Step "Scarico l'APK"
$tmp = Join-Path $env:TEMP ("lasttime-apk-" + [guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $tmp -Force | Out-Null
gh run download $id --name $Artifact --dir $tmp
if ($LASTEXITCODE -ne 0) { Fail "Download dell'artifact fallito." }

$apk = Get-ChildItem -Path $tmp -Recurse -Filter *.apk | Select-Object -First 1
if (-not $apk) { Fail "Nessun .apk dentro l'artifact scaricato." }

if (-not (Test-Path $Dest)) {
    New-Item -ItemType Directory -Path $Dest -Force | Out-Null
    Info "Creata la cartella $Dest"
}

$stamp    = Get-Date -Format 'yyyyMMdd-HHmm'
$versione = Join-Path $Dest "lasttime-v$ver-$stamp.apk"
$ultima   = Join-Path $Dest 'lasttime-ultima.apk'

Copy-Item $apk.FullName $versione -Force
Copy-Item $apk.FullName $ultima   -Force
try { Remove-Item $tmp -Recurse -Force -ErrorAction Stop } catch {}

$mb = [math]::Round($apk.Length / 1MB, 1)
Write-Host "`n=== FATTO ===" -ForegroundColor Green
Ok "$(Split-Path $versione -Leaf)  ($mb MB)"
Ok "lasttime-ultima.apk  (sovrascritto: dal telefono apri sempre questo)"
Info $Dest
Fine 0
