# =====================================================================
#  Mentora-managment-be — Makefile
#  Toutes les cibles supposent que le repo frère est cloné côte à côte
#  dans le même dossier workspace (voir README.md).
# =====================================================================

.PHONY: up down test lint openapi-export n8n-export n8n-import help

## Démarre tous les services (backend + postgres + frontend)
up:
	docker compose up --build

## Démarre en arrière-plan
up-d:
	docker compose up --build -d

## Arrête et supprime les conteneurs
down:
	docker compose down

## Arrête et supprime les conteneurs + volumes (base de données incluse)
down-v:
	docker compose down -v

## Lance les tests Maven (Testcontainers inclus) — nécessite Docker
test:
	./mvnw verify

## Vérifie le formatage et le style (Spotless + Checkstyle)
lint:
	./mvnw spotless:check checkstyle:check

## Applique automatiquement le formatage Spotless
format:
	./mvnw spotless:apply

## Génère la spec OpenAPI dans contracts/openapi.json
## Lance d'abord l'application en arrière-plan (port 8080), attend,
## puis télécharge la spec et arrête l'application.
openapi-export:
	@echo "==> Démarrage temporaire du backend pour export OpenAPI..."
	./mvnw spring-boot:run -Dspring-boot.run.profiles=dev &
	SPRING_PID=$$! ; \
	sleep 15 ; \
	curl -s http://localhost:8080/v3/api-docs > contracts/openapi.json ; \
	kill $$SPRING_PID
	@echo "==> contracts/openapi.json mis à jour."

## Exporte les workflows n8n depuis l'instance locale vers n8n/workflows/
## Nécessite que le service n8n soit démarré (make up-d)
n8n-export:
	@echo "==> Export des workflows n8n..."
	docker compose exec n8n n8n export:workflow --all --separate --pretty --output=/n8n/workflows/ 2>/dev/null || \
		echo "ERREUR : le service n8n n'est pas démarré. Lancez 'make up-d' d'abord."

## Importe tous les workflows du dossier n8n/workflows/ dans l'instance locale
n8n-import:
	@echo "==> Import des workflows n8n..."
	docker compose exec n8n n8n import:workflow --separate --input=/n8n/workflows/ 2>/dev/null || \
		echo "ERREUR : le service n8n n'est pas démarré. Lancez 'make up-d' d'abord."

## Installe les git hooks locaux pour la validation automatique pre-commit
install-hooks:
	@echo "==> Installation des hooks de pré-validation..."
	@mkdir -p .git/hooks
	@cp scripts/pre-commit .git/hooks/pre-commit
	@chmod +x .git/hooks/pre-commit
	@echo "==> Hook pre-commit installé avec succès."

## Affiche l'aide
help:
	@echo ""
	@echo "Cibles disponibles :"
	@echo "  make up            — Démarre tous les services"
	@echo "  make up-d          — Démarre en arrière-plan"
	@echo "  make down          — Arrête les services"
	@echo "  make down-v        — Arrête + supprime les volumes (reset BDD)"
	@echo "  make test          — Lance les tests (Testcontainers)"
	@echo "  make lint          — Vérifie le style de code"
	@echo "  make format        — Applique le formatage automatique"
	@echo "  make openapi-export — Génère contracts/openapi.json"
	@echo "  make n8n-export    — Exporte les workflows n8n"
	@echo "  make n8n-import    — Importe les workflows n8n"
	@echo "  make install-hooks — Installe le hook Git de pré-validation"
	@echo ""
