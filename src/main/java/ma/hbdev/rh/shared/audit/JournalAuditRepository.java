package ma.hbdev.rh.shared.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JournalAuditRepository extends JpaRepository<JournalAudit, UUID> {

  /**
   * EF-CFG-04 : filtres module/utilisateur/période + recherche texte libre. La clause de recherche
   * reproduit exactement l'expression indexée par {@code idx_journal_audit_recherche_trgm}
   * (V1__schema_initial.sql) pour que le GIN trigram serve réellement à ce filtre plutôt que d'être
   * mort. Requête native (pas de Specification/Criteria) car le cast jsonb -> text et l'expression
   * concaténée indexée n'ont pas d'équivalent propre côté Criteria API. Chaque paramètre est casté
   * explicitement (y compris dans son "is null") : sans ça, Postgres refuse de déterminer le type
   * d'un paramètre `null` non typé côté protocole étendu ("could not determine data type of
   * parameter").
   */
  @Query(
      value =
          """
          select j.* from journal_audit j
           where (cast(:module as text) is null or j.module = cast(:module as module_audit))
             and (cast(:utilisateurId as uuid) is null or j.utilisateur_id = cast(:utilisateurId as uuid))
             and (cast(:debut as timestamptz) is null or j.horodatage >= cast(:debut as timestamptz))
             and (cast(:fin as timestamptz) is null or j.horodatage < cast(:fin as timestamptz))
             and (cast(:terme as text) is null or
                  (coalesce(j.action, '') || ' ' || coalesce(j.entite_type, '') || ' '
                    || coalesce(j.details::text, '')) ilike concat('%', cast(:terme as text), '%'))
           order by j.horodatage desc
          """,
      countQuery =
          """
          select count(*) from journal_audit j
           where (cast(:module as text) is null or j.module = cast(:module as module_audit))
             and (cast(:utilisateurId as uuid) is null or j.utilisateur_id = cast(:utilisateurId as uuid))
             and (cast(:debut as timestamptz) is null or j.horodatage >= cast(:debut as timestamptz))
             and (cast(:fin as timestamptz) is null or j.horodatage < cast(:fin as timestamptz))
             and (cast(:terme as text) is null or
                  (coalesce(j.action, '') || ' ' || coalesce(j.entite_type, '') || ' '
                    || coalesce(j.details::text, '')) ilike concat('%', cast(:terme as text), '%'))
          """,
      nativeQuery = true)
  Page<JournalAudit> rechercher(
      @Param("module") String module,
      @Param("utilisateurId") UUID utilisateurId,
      @Param("debut") Instant debut,
      @Param("fin") Instant fin,
      @Param("terme") String terme,
      Pageable pageable);
}
