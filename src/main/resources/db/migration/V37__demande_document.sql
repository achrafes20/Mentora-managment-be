-- Demande de document : ticket employé -> Admin RH avec motif libre obligatoire (ex. "attestation
-- de travail pour la banque"), signalant qu'un document est attendu. Direction opposée à l'ancien
-- type "document_libre" retiré en V36 (qui était un envoi Admin -> employé, jamais une demande
-- soumise à approbation) — aucun risque de réintroduire la confusion levée par ce retrait.
ALTER TYPE type_demande_administrative ADD VALUE 'demande_document';
