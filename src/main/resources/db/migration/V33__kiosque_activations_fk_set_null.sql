-- Complète V32 : les autres FK de kiosque_activations (emis_par, revoquee_par, delegation_id)
-- bloquaient elles aussi la suppression d'un utilisateur/délégation référencé, pour la même
-- raison — un code généré ne doit jamais empêcher de supprimer l'Admin qui l'a émis.
ALTER TABLE kiosque_activations ALTER COLUMN emis_par DROP NOT NULL;

ALTER TABLE kiosque_activations DROP CONSTRAINT kiosque_activations_emis_par_fkey;
ALTER TABLE kiosque_activations
  ADD CONSTRAINT kiosque_activations_emis_par_fkey
  FOREIGN KEY (emis_par) REFERENCES utilisateurs(id) ON DELETE SET NULL;

ALTER TABLE kiosque_activations DROP CONSTRAINT kiosque_activations_revoquee_par_fkey;
ALTER TABLE kiosque_activations
  ADD CONSTRAINT kiosque_activations_revoquee_par_fkey
  FOREIGN KEY (revoquee_par) REFERENCES utilisateurs(id) ON DELETE SET NULL;

ALTER TABLE kiosque_activations DROP CONSTRAINT kiosque_activations_delegation_id_fkey;
ALTER TABLE kiosque_activations
  ADD CONSTRAINT kiosque_activations_delegation_id_fkey
  FOREIGN KEY (delegation_id) REFERENCES delegations_approbation(id) ON DELETE SET NULL;
