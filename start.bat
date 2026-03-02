@echo off
title Compilador UberCraft v1.0 - Java 17

echo ============================================
echo Compilador do Plugin UberCraft
echo ============================================
echo.

echo Procurando Java 17 instalado...
echo.

set JDK_PATH=
for /d %%i in ("C:\Program Files\Java\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Java\jdk17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\AdoptOpenJDK\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\OpenJDK\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Amazon Corretto\jdk17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Microsoft\jdk-17*") do set JDK_PATH=%%i

if "%JDK_PATH%"=="" (
    echo ============================================
    echo ERRO: JDK 17 nao encontrado!
    echo Instale o Java 17 JDK e tente novamente.
    echo ============================================
    pause
    exit /b 1
)

echo Java 17 encontrado em: %JDK_PATH%
echo.

set JAVAC="%JDK_PATH%\bin\javac.exe"
set JAR="%JDK_PATH%\bin\jar.exe"

echo ============================================
echo Preparando ambiente de compilacao...
echo ============================================
echo.

echo Limpando pasta out...
if exist out (
    rmdir /s /q out >nul 2>&1
)
mkdir out
mkdir out\com
mkdir out\com\foxsrv
mkdir out\com\foxsrv\uber

echo.
echo ============================================
echo Verificando dependencias...
echo ============================================
echo.

REM Verificar Spigot API
if not exist spigot-api-1.20.1-R0.1-SNAPSHOT.jar (
    echo [ERRO] spigot-api-1.20.1-R0.1-SNAPSHOT.jar nao encontrado!
    echo.
    echo Certifique-se de que o arquivo spigot-api-1.20.1-R0.1-SNAPSHOT.jar esta na pasta raiz.
    pause
    exit /b 1
) else (
    echo [OK] Spigot API encontrado
    set SPIGOT_PATH=spigot-api-1.20.1-R0.1-SNAPSHOT.jar
)

REM Verificar CoinCard API (OBRIGATÓRIO para UberCraft)
if not exist CoinCard.jar (
    echo [ERRO] CoinCard.jar nao encontrado na pasta raiz!
    echo.
    echo O plugin UberCraft REQUER o CoinCard para funcionar!
    echo Certifique-se de que o arquivo CoinCard.jar esta na pasta raiz.
    pause
    exit /b 1
) else (
    echo [OK] CoinCard API encontrado
    set COINCARD_PATH=CoinCard.jar
)

REM Verificar Vault API (opcional, mas recomendado)
if not exist Vault.jar (
    echo [AVISO] Vault.jar nao encontrado na pasta raiz!
    echo O plugin UberCraft nao requer Vault, mas pode ser usado para integracoes.
    echo Continuando compilacao normalmente...
    echo.
    set VAULT_PATH=
) else (
    echo [OK] Vault API encontrado (opcional)
    set VAULT_PATH=Vault.jar
)

echo.
echo ============================================
echo Compilando UberCraft...
echo ============================================
echo.

REM Montar classpath
set CLASSPATH="%SPIGOT_PATH%";"%COINCARD_PATH%"
if defined VAULT_PATH (
    set CLASSPATH=%CLASSPATH%;"%VAULT_PATH%"
)

REM Mostrar classpath para debug
echo Classpath: %CLASSPATH%
echo.

REM Verificar se o arquivo fonte existe
if not exist src\com\foxsrv\uber\UberCraft.java (
    echo ============================================
    echo ERRO: Arquivo fonte nao encontrado!
    echo ============================================
    echo.
    echo Caminho esperado: src\com\foxsrv\uber\UberCraft.java
    echo.
    echo Estrutura de diretorios atual:
    echo.
    if exist src (
        echo Conteudo de src:
        dir /s /b src
    ) else (
        echo Pasta src nao encontrada!
    )
    echo.
    echo Criando estrutura de diretorios necessaria...
    mkdir src\com\foxsrv\uber 2>nul
    echo Por favor, coloque o arquivo UberCraft.java em src\com\foxsrv\uber\
    pause
    exit /b 1
)

REM Criar arquivo com lista de fontes
dir /s /b src\com\foxsrv\uber\*.java > sources.txt

REM Compilar com as dependências necessárias
echo Compilando UberCraft.java...
%JAVAC% --release 17 -d out ^
-cp %CLASSPATH% ^
-sourcepath src ^
-encoding UTF-8 ^
@sources.txt

if %errorlevel% neq 0 (
    echo ============================================
    echo ERRO AO COMPILAR O PLUGIN!
    echo ============================================
    echo.
    echo Verifique os erros acima e corrija o codigo.
    echo.
    echo Possiveis causas:
    echo 1 - Erro de sintaxe no codigo
    echo 2 - CoinCard API nao encontrada ou incompativel
    echo 3 - Versao do Java incorreta
    del sources.txt
    pause
    exit /b 1
)

del sources.txt

echo.
echo Compilacao concluida com sucesso!
echo.

echo ============================================
echo Copiando arquivos de recursos...
echo ============================================
echo.

REM Copiar plugin.yml
if exist resources\plugin.yml (
    copy resources\plugin.yml out\ >nul
    echo [OK] plugin.yml copiado
) else (
    echo [AVISO] plugin.yml nao encontrado em resources\
    echo Criando plugin.yml padrao...
    
    (
        echo name: UberCraft
        echo version: 1.0
        echo main: com.foxsrv.uber.UberCraft
        echo api-version: 1.20
        echo depend: [CoinCard]
        echo author: FoxOficial2
        echo description: Uber system for Minecraft using CoinCard payments
        echo.
        echo commands:
        echo   uber:
        echo     description: Main Uber command
        echo     usage: /uber ^<x y z^|warp^|cancel^|admin^>
        echo     aliases: [u]
        echo   ubergui:
        echo     description: Open Uber requests menu
        echo     usage: /ubergui
        echo     aliases: [ug]
        echo.
        echo permissions:
        echo   uber.player:
        echo     description: Can request Uber rides
        echo     default: true
        echo   uber.uber:
        echo     description: Can accept Uber rides
        echo     default: op
        echo   uber.admin:
        echo     description: Can manage warps
        echo     default: op
        echo     children:
        echo       uber.uber: true
    ) > out\plugin.yml
    echo [OK] plugin.yml criado automaticamente
)

REM Copiar config.yml
if exist resources\config.yml (
    copy resources\config.yml out\ >nul
    echo [OK] config.yml copiado
) else (
    echo [AVISO] config.yml nao encontrado em resources\
    echo Criando config.yml padrao...
    
    (
        echo # UberCraft Configuration
        echo Server_Card: "e1301fadfc35"  # Server card to receive and pay Uber payments
        echo.
        echo # Amount paid for each block traveled
        echo Distance_Worth: 0.00000001
        echo.
        echo # Message Configuration
        echo messages:
        echo   prefix: "&8[&6UberCraft&8]"
        echo   no-permission: "&cYou don't have permission to use this command!"
        echo.
        echo   # GUI Titles
        echo   uber-gui-title: "&6&lUber Requests"
        echo   warp-gui-title: "&6&lSelect Destination Warp"
        echo.
        echo   # Compass
        echo   uber-compass-name: "&6&lUber Compass &7- %%player%%"
        echo   uber-compass-lore: "&7Follow this compass to &6%%player%%'s destination"
        echo.
        echo   # Request messages
        echo   request-created: "&aUber requested! Price: &6%%price%%"
        echo   request-waiting: "&7Waiting for an Uber to accept..."
        echo   request-expired: "&cYour Uber request expired! &a%%price%% &chas been refunded."
        echo.
        echo   # Ride messages
        echo   ride-accepted: "&a%%uber%% accepted your ride!"
        echo   ride-pickup: "&7They are on their way to pick you up."
        echo   ride-completed: "&aRide completed! You received: &6%%price%%"
        echo   ride-cancelled: "&cRide cancelled!"
        echo.
        echo   # Refund messages
        echo   refund-issued: "&aRefund issued: &6%%price%%"
        echo.
        echo   # Warp messages
        echo   warp-created: "&aWarp &6%%name%% &acreated!"
        echo   warp-removed: "&aWarp &6%%name%% &aremoved!"
        echo   warp-not-found: "&cWarp not found!"
        echo.
        echo   # Error messages
        echo   error-insufficient-balance: "&cInsufficient balance! Need: &6%%need%% &cHave: &6%%have%%"
        echo   error-no-card: "&cYou don't have a card set! Use /coin card <card>"
        echo   error-active-request: "&cYou already have an active Uber request!"
        echo   error-active-ride: "&cYou are already in an Uber ride!"
        echo   error-player-offline: "&cThe player is no longer online!"
        echo   error-wrong-world: "&cYou must be in the same world as the destination!"
        echo   error-invalid-coordinates: "&cInvalid coordinates!"
        echo   error-hold-item: "&cYou must hold an item to use as warp icon!"
    ) > out\config.yml
    echo [OK] config.yml criado automaticamente
)

echo.
echo ============================================
echo Criando arquivo JAR...
echo ============================================
echo.

cd out

REM Criar JAR com todos os recursos
echo Criando UberCraft.jar...
%JAR% cf UberCraft.jar com plugin.yml config.yml

cd ..

echo.
echo ============================================
echo PLUGIN COMPILADO COM SUCESSO!
echo ============================================
echo.
echo Arquivo gerado: out\UberCraft.jar
echo.
dir out\UberCraft.jar
echo.
echo ============================================
echo RESUMO DA COMPILACAO:
echo ============================================
echo.
echo - Data/Hora: %date% %time%
echo - Java Version: 17
echo - Spigot API: OK
echo - CoinCard API: OK (OBRIGATÓRIO)
if defined VAULT_PATH (
    echo - Vault API: OK (opcional)
) else (
    echo - Vault API: NAO ENCONTRADO (opcional)
)
echo - Arquivo fonte: src\com\foxsrv\uber\UberCraft.java
echo.
echo ============================================
echo ARQUIVOS COMPILADOS:
echo ============================================
echo.
dir /b src\com\foxsrv\uber\*.java
echo.
echo ============================================
echo IMPORTANTE - REQUISITOS PARA EXECUCAO:
echo ============================================
echo.
echo 1 - O plugin REQUER CoinCard.jar na pasta plugins do servidor
echo 2 - Spigot/Paper 1.20+ necessario
echo 3 - Java 17 ou superior
echo.
echo ============================================
echo Para instalar:
echo ============================================
echo.
echo 1 - Copie out\UberCraft.jar para a pasta plugins do servidor
echo 2 - Copie CoinCard.jar tambem para a pasta plugins (se ainda nao estiver)
echo 3 - Reinicie o servidor ou use /reload confirm
echo 4 - Edite plugins/UberCraft/config.yml se necessario
echo 5 - Os dados serao salvos em plugins/UberCraft/data.dat
echo.
echo ============================================
echo COMANDOS DO PLUGIN:
echo ============================================
echo.
echo PLAYERS:
echo /uber x y z [world] - Request Uber to coordinates
echo /uber warp - Open warp selection menu
echo /uber warp ^<name^> - Request Uber to specific warp
echo /uber cancel - Cancel your current Uber
echo.
echo UBERS:
echo /ubergui - Open Uber requests menu
echo.
echo ADMIN:
echo /uber admin set ^<name^> - Create a warp with item in hand
echo /uber admin unset ^<name^> - Remove a warp
echo.
echo ============================================
echo PERMISSOES:
echo ============================================
echo.
echo uber.player - Can request Uber rides (default: true)
echo uber.uber - Can accept Uber rides (default: op)
echo uber.admin - Can manage warps (default: op)
echo.
echo ============================================
echo FUNCIONALIDADES:
echo ============================================
echo.
echo - Sistema de corridas Uber com pagamentos em CoinCard
echo - Menu GUI com cabecas dos jogadores
echo - Bussola apontando para o destino
echo - Sistema de warps com icones personalizaveis
echo - Calculo de preco baseado em distancia
echo - Cancelamento com reembolso parcial (50/40/10)
echo - Expiração automatica apos 1 minuto
echo - Fila de pagamentos com delay de 1 segundo
echo - 100% assincrono e anti-lag
echo - Salvamento em data.dat
echo.
echo ============================================
echo.

pause
