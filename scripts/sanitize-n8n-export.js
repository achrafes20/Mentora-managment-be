#!/usr/bin/env node
// Redacte l'identite du compte proprietaire n8n (nom + e-mail reel) dans chaque
// workflow JSON fraichement exporte par `n8n export:workflow` avant commit.
// `shared[].project.name` embarque cette identite (ex. "Prenom Nom <email@...>")
// pour un projet "personal" (celui de quiconque a lance l'export sur sa machine) —
// jamais nettoye par la CLI n8n elle-meme. Appele automatiquement par `make n8n-export`.

const fs = require("fs");
const path = require("path");

const WORKFLOWS_DIR = path.join(__dirname, "..", "n8n", "workflows");
const PLACEHOLDER_NAME = "HB Developpement (n8n)";

const fichiers = fs
  .readdirSync(WORKFLOWS_DIR)
  .filter((f) => f.endsWith(".json"));

let modifies = 0;

for (const fichier of fichiers) {
  const chemin = path.join(WORKFLOWS_DIR, fichier);
  const workflow = JSON.parse(fs.readFileSync(chemin, "utf8"));

  let touche = false;
  for (const entree of workflow.shared ?? []) {
    if (entree.project?.name && entree.project.name !== PLACEHOLDER_NAME) {
      entree.project.name = PLACEHOLDER_NAME;
      touche = true;
    }
  }

  if (touche) {
    fs.writeFileSync(chemin, JSON.stringify(workflow, null, 2) + "\n");
    modifies++;
  }
}

console.log(
  `==> Identite exportateur nettoyee dans ${modifies}/${fichiers.length} fichier(s) de n8n/workflows/.`,
);
