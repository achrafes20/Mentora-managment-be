-- Corrige V30 : la FK employe_id sur kiosque_activations bloquait la suppression d'un employé
-- (violation de contrainte) au lieu de se détacher proprement — un appareil personnel révoqué ne
-- doit jamais empêcher de supprimer la fiche employé associée.
ALTER TABLE kiosque_activations DROP CONSTRAINT kiosque_activations_employe_id_fkey;
ALTER TABLE kiosque_activations
  ADD CONSTRAINT kiosque_activations_employe_id_fkey
  FOREIGN KEY (employe_id) REFERENCES employes(id) ON DELETE SET NULL;
