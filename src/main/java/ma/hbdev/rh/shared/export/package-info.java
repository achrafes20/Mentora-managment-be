/**
 * Plomberie générique de génération Excel/PDF (EF-EXP — T5.B1). Ne connaît aucune logique métier :
 * chaque module propriétaire (employee/attendance/administrative/config) construit ses propres
 * en-têtes/lignes déjà filtrées, et délègue uniquement le rendu du fichier à ce package.
 */
package ma.hbdev.rh.shared.export;
