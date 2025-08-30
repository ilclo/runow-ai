# GIT — WORKFLOW LOCALE → GITHUB

## Setup (una volta)
1. Installa Git (Windows: Git for Windows con Git Credential Manager).
2. Configura:
git config --global user.name "Nome Cognome"
git config --global user.email "email@dominio"



Su Windows:
git config --global core.autocrlf true




## Creo un nuovo repo da una cartella locale
cd runow-ai
git init
git add .
git commit -m "init"
git branch -M main

crea repo vuoto su GitHub e copia l’URL:
git remote add origin https://github.com/tuoutente/runow-ai.git
git push -u origin main




## Ciclo di lavoro giornaliero
git pull

modifica file...
git add -A
git commit -m "descrizione"
git push




## Uso i branch (facoltativo)
git checkout -b feature/nome
git add -A
git commit -m "feat: ..."
git push -u origin feature/nome

apri PR su GitHub e fai merge



## Cambiare/aggiungere il remote
git remote -v
git remote remove origin
git remote add origin <URL>




## Problemi comuni
- "Support for password authentication was removed" → usa PAT (token) o SSH.
- "Permission denied" → controlla che l’URL del remote sia giusto e i permessi ok.
- Conflitti dopo `git pull` → risolvi i file, poi `git add .` + `git commit` + `git push`.