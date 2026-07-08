# =====================================================================
#  Git Pre-Commit Hook — Mentora RH Backend (PowerShell)
#  Vérifie le formatage du code (Spotless) et les lints (Checkstyle)
#  avant d'autoriser le commit sous Windows.
# =====================================================================

Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host " 🔍 [Git Hook] Vérification de la qualité du code..." -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan

# 1. Vérification Spotless (formatage)
& ./mvnw spotless:check -q
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host " ❌ [ERREUR] Le formatage du code n'est pas conforme." -ForegroundColor Red
    Write-Host "    Veuillez lancer la commande suivante pour formater le code :" -ForegroundColor Yellow
    Write-Host "    👉  mvn spotless:apply  (ou: make format)" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

# 2. Vérification Checkstyle (règles de code)
& ./mvnw checkstyle:check -q
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host " ❌ [ERREUR] Des violations de règles de style ont été trouvées." -ForegroundColor Red
    Write-Host "    Veuillez consulter les rapports dans target/checkstyle-result.xml" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

Write-Host " ✅ [Succès] Code validé avec succès. Commit autorisé !" -ForegroundColor Green
exit 0
